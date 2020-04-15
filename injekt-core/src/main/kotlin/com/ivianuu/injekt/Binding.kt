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
 * A binding knows how to create a concrete instance of a type
 * it also holds additional information about the declaration
 */
data class Binding<T>(
    /**
     * The key which is used to identify this binding
     */
    val key: Key<T>,
    /**
     * All behaviors of this binding
     */
    val behavior: Behavior = Behavior.None,
    /**
     * How overrides should be handled
     */
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.Fail,
    /**
     * Creates instances for this binding
     */
    val provider: BindingProvider<T>
)

interface BindingProvider<T> {
    fun link(linker: Linker) {
    }

    operator fun invoke(parameters: Parameters = emptyParameters()): T
}

class KeyedBindingProvider<T>(val key: Key<T>) : BindingProvider<T> {
    private lateinit var provider: BindingProvider<T>
    override fun link(linker: Linker) {
        provider = linker.get(key)
    }

    override fun invoke(parameters: Parameters): T = provider(parameters)
}

typealias BindingDefinition<T> = ProviderContext.(Parameters) -> T

class ProviderContext {
    @KeyOverload
    fun <T> get(
        key: Key<T>,
        parameters: Parameters = emptyParameters()
    ): T = error("Stub")
}
