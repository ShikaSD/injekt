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

package com.ivianuu.injekt.provider

import com.ivianuu.injekt.Component
import com.ivianuu.injekt.ParametersDefinition

import kotlin.reflect.KClass

/**
 * Returns a [Provider] for [T] and [name]
 * Each [Provider.get] call results in a potentially new value
 */
inline fun <reified T> Component.getProvider(
    name: Any? = null,
    noinline defaultParameters: ParametersDefinition? = null
): Provider<T> = getProvider(T::class, name, defaultParameters)

/**
 * Returns a [Provider] for [T] and [name]
 * Each [Provider.get] call results in a potentially new value
 */
fun <T> Component.getProvider(
    type: KClass<*>,
    name: Any? = null,
    defaultParameters: ParametersDefinition? = null
): Provider<T> = provider { parameters: ParametersDefinition? ->
    get<T>(type, name, parameters ?: defaultParameters)
}

/**
 * Returns a [Provider] for [T] and [name]
 * Each [Provider.get] call results in a potentially new value
 */
inline fun <reified T> Component.injectProvider(
    name: Any? = null,
    noinline defaultParameters: ParametersDefinition? = null
): Lazy<Provider<T>> = injectProvider(T::class, name, defaultParameters)

/**
 * Returns a [Provider] for [T] and [name]
 * Each [Provider.get] call results in a potentially new value
 */
fun <T> Component.injectProvider(
    type: KClass<*>,
    name: Any? = null,
    defaultParameters: ParametersDefinition? = null
): Lazy<Provider<T>> = lazy(LazyThreadSafetyMode.NONE) {
    provider { parameters: ParametersDefinition? ->
        get<T>(type, name, parameters ?: defaultParameters)
    }
}