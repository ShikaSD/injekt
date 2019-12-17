/*
 * Copyright 2019 Manuel Wrage
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
 * Provides instances of type [T]
 * For any type [T] that can be injected, you can also inject Provider<T>.
 * This enables providing multiple, lazy or optional retrieval of instances
 */
interface Provider<T> {

    /**
     * Provides an instance of type [T]
     *
     * @param parameters optional parameters for constructing the instance
     * @return the instance of type [T]
     */
    operator fun invoke(parameters: ParametersDefinition? = null): T
}

internal class KeyedProvider<T>(
    private val component: Component,
    private val key: Key
) : Provider<T> {

    private var _binding: LinkedBinding<T>? = null

    override fun invoke(parameters: ParametersDefinition?): T {
        var binding = _binding
        if (binding == null) {
            binding = component.getBinding(key)
            _binding = binding
        }
        return binding(parameters)
    }
}

internal class ProviderBinding<T>(
    private val component: Component,
    private val key: Key
) : LinkedBinding<Provider<T>>() {
    override fun invoke(parameters: ParametersDefinition?): Provider<T> =
        KeyedProvider(component, key)
}
