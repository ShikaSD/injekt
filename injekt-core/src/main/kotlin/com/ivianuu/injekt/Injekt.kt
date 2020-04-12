/*
 * Copyright 2020 Manuel Wrage
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

/**
 * Global configurations
 */
object Injekt {

    /**
     * The logger to use
     */
    var logger: Logger? = null

    /**
     * Registers all [modules] and invokes them in matching [ComponentBuilder]s
     */
    fun modules(vararg modules: Module) {
        modules.forEach { Modules.register(it) }
    }

    fun initializeEndpoint(): Unit = error("Must be compiled with the injekt compiler")

}

inline fun injekt(block: Injekt.() -> Unit) {
    Injekt.block()
}

inline fun Injekt.module(
    scope: Scope,
    invokeOnInit: Boolean = false,
    crossinline block: ComponentBuilder.() -> Unit
) {
    modules(Module(scope = scope, invokeOnInit = invokeOnInit, block = block))
}

inline fun Injekt.module(
    scopes: List<Scope>,
    invokeOnInit: Boolean = false,
    crossinline block: ComponentBuilder.() -> Unit
) {
    modules(Module(scopes = scopes, invokeOnInit = invokeOnInit, block = block))
}