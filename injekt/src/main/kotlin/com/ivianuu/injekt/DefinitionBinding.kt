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
 * Creates instances of type [T]
 */
typealias Definition<T> = DefinitionContext.(Parameters?) -> T

/**
 * The receiver scope for [Definition]s
 *
 * Which allows the following syntax:
 *
 * @see Module.factory
 * @see Module.single
 */
interface DefinitionContext {

    /**
     * @see Component.get
     */
    fun <T> get(
        type: Type<T>,
        name: Any? = null,
        parameters: ParametersDefinition? = null
    ): T = get(key = keyOf(type, name), parameters = parameters)

    /**
     * @see Component.get
     */
    fun <T> get(key: Key, parameters: ParametersDefinition? = null): T

    /**
     * Nullable version of [Parameters.component1]
     */
    operator fun <T> Parameters?.component1(): T = this!![0]

    operator fun <T> Parameters?.component2(): T = this!![1]
    operator fun <T> Parameters?.component3(): T = this!![2]
    operator fun <T> Parameters?.component4(): T = this!![3]
    operator fun <T> Parameters?.component5(): T = this!![4]
}

/**
 * @see Component.get
 */
inline fun <reified T> DefinitionContext.get(
    name: Any? = null,
    noinline parameters: ParametersDefinition? = null
): T = get(type = typeOf(), name = name, parameters = parameters)

internal fun <T> definitionBinding(
    optimizing: Boolean,
    definition: Definition<T>
): Binding<T> {
    return if (optimizing) UnlinkedOptimizingDefinitionBinding(definition)
    else UnlinkedDefinitionBinding(definition)
}

private class UnlinkedDefinitionBinding<T>(
    private val definition: Definition<T>
) : UnlinkedBinding<T>() {
    override fun link(linker: Linker): LinkedBinding<T> = LinkedDefinitionBinding(
        linker.component, definition
    )
}

/**
 * Unlike [LinkedOptimizingDefinitionBinding] always looks up the bindings
 * because it will be only for singletons
 *
 * @see Module.single
 */
private class LinkedDefinitionBinding<T>(
    private val component: Component,
    private val definition: Definition<T>
) : LinkedBinding<T>(), DefinitionContext {

    override fun <T> get(key: Key, parameters: ParametersDefinition?): T =
        component.get(key, parameters)

    override fun invoke(parameters: ParametersDefinition?): T =
        definition(this, parameters?.invoke())

}

private class UnlinkedOptimizingDefinitionBinding<T>(
    private val definition: Definition<T>
) : UnlinkedBinding<T>() {
    override fun link(linker: Linker): LinkedBinding<T> =
        LinkedOptimizingDefinitionBinding(linker.component, definition)
}

/**
 * Caches already retrieved bindings to avoid the lookup cost
 * because it will be used in factory bindings which will get called on each request
 *
 * @see Module.factory
 */
private class LinkedOptimizingDefinitionBinding<T>(
    private val component: Component,
    private val definition: Definition<T>
) : LinkedBinding<T>(), DefinitionContext {

    private var bindings = arrayOfNulls<LinkedBinding<*>>(5)
    private var currentIndex = 0

    override fun <T> get(key: Key, parameters: ParametersDefinition?): T {
        return if (currentIndex > bindings.lastIndex) {
            bindings = bindings.copyOf(currentIndex + 5)
            val binding = component.linker.get<T>(key)
            bindings[currentIndex] = binding
            ++currentIndex
            binding
        } else {
            var binding = bindings[currentIndex]
            if (binding == null) {
                binding = component.linker.get<T>(key)
                bindings[currentIndex] = binding
            }

            ++currentIndex

            binding
        }(parameters) as T
    }

    override fun invoke(parameters: ParametersDefinition?): T {
        currentIndex = 0
        return definition(this, parameters?.invoke())
    }

}