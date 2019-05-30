/*
 * Copyright 2018 Manuel Wrage
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivianuu.injekt

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import java.util.jar.JarFile
import java.util.zip.ZipEntry

internal object FastServiceLoader {
    private const val PREFIX: String = "META-INF/services/"

    private const val FAST_SERVICE_LOADER_ENABLED = true

    fun <S> load(service: Class<S>, loader: ClassLoader): List<S> {
        return try {
            loadProviders(service, loader)
        } catch (e: Throwable) {
            e.printStackTrace()
            // Fallback to default service loader
            ServiceLoader.load(service, loader).toList()
        }
    }

    private fun <S> loadProviders(service: Class<S>, loader: ClassLoader): List<S> {
        val fullServiceName = PREFIX + service.name
        // Filter out situations when both JAR and regular files are in the classpath (e.g. IDEA)
        val urls = loader.getResources(fullServiceName)
        val providers = urls.toList().flatMap { parse(it) }.toSet()
        require(providers.isNotEmpty()) { "No providers were loaded with FastServiceLoader" }
        return providers.map { getProviderInstance(it, loader, service) }
    }

    private fun <S> getProviderInstance(name: String, loader: ClassLoader, service: Class<S>): S {
        val clazz = Class.forName(name, false, loader)
        require(service.isAssignableFrom(clazz)) { "Expected service of class $service, but found $clazz" }
        return service.cast(clazz.getDeclaredConstructor().newInstance())
    }

    private fun parse(url: URL): List<String> {
        val path = url.toString()
        // Fast-path for JARs
        if (path.startsWith("jar")) {
            val pathToJar = path.substringAfter("jar:file:").substringBefore('!')
            val entry = path.substringAfter("!/")
            // mind the verify = false flag!
            (JarFile(pathToJar, false) as Closeable).use { file ->
                BufferedReader(
                    InputStreamReader(
                        (file as JarFile).getInputStream(ZipEntry(entry)),
                        "UTF-8"
                    )
                ).use { r ->
                    return parseFile(r)
                }
            }
        }
        // Regular path for everything elese
        return BufferedReader(InputStreamReader(url.openStream())).use { reader ->
            parseFile(reader)
        }
    }

    private fun parseFile(r: BufferedReader): List<String> {
        val names = mutableSetOf<String>()
        while (true) {
            val line = r.readLine() ?: break
            val serviceName = line.substringBefore("#").trim()
            require(serviceName.all { it == '.' || Character.isJavaIdentifierPart(it) }) { "Illegal service provider class name: $serviceName" }
            if (serviceName.isNotEmpty()) {
                names.add(serviceName)
            }
        }
        return names.toList()
    }
}