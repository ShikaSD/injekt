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

import kotlin.reflect.KClass

class ComponentBuilder @PublishedApi internal constructor() {

    var scope: KClass<out Annotation>? = null

    private val modules = mutableListOf<Module>()
    private val dependencies = mutableListOf<Component>()

    fun dependencies(dependency: Component): ComponentBuilder {
        this.dependencies.add(dependency)
        return this
    }

    fun dependencies(vararg dependencies: Component): ComponentBuilder {
        this.dependencies.addAll(dependencies)
        return this
    }

    fun dependencies(dependencies: Iterable<Component>): ComponentBuilder {
        this.dependencies.addAll(dependencies)
        return this
    }

    fun modules(module: Module): ComponentBuilder {
        this.modules.add(module)
        return this
    }

    fun modules(vararg modules: Module): ComponentBuilder {
        this.modules.addAll(modules)
        return this
    }

    fun modules(modules: Iterable<Module>): ComponentBuilder {
        this.modules.addAll(modules)
        return this
    }

    @PublishedApi
    internal fun build(): Component = createComponent(
        scope = scope,
        modules = modules,
        dependencies = dependencies
    )

}

/**
 * Constructs a new [Component] which will configured [block]
 */
inline fun component(block: ComponentBuilder.() -> Unit = {}): Component =
    ComponentBuilder().apply(block).build()

inline fun <reified T : Annotation> ComponentBuilder.scope(): ComponentBuilder {
    scope = T::class
    return this
}

@PublishedApi
internal fun createComponent(
    scope: KClass<out Annotation>? = null,
    modules: Iterable<Module> = emptyList(),
    dependencies: Iterable<Component> = emptyList()
): Component {
    val dependencyScopes = mutableSetOf<KClass<out Annotation>>()

    dependencies.forEach {
        if (it.scope != null) {
            if (!dependencyScopes.add(it.scope)) {
                error("Duplicated scope ${it.scope}")
            }
        }
    }

    check(scope == null || !dependencyScopes.contains(scope)) {
        "Duplicated scope $scope"
    }

    check(scope != null || dependencyScopes.isEmpty()) {
        "Must have a scope if a dependency has a scope"
    }

    val dependencyBindingKeys = dependencies
        .map { it.getAllBindingKeys() }
        .fold(mutableSetOf<Key>()) { acc, current ->
            current.forEach { key ->
                check(acc.add(key)) {
                    "Already declared binding for $key"
                }
            }

            return@fold acc
        }

    val bindings = mutableMapOf<Key, Binding<*>>()
    val mapBindings = mutableMapOf<Key, MutableMap<Any?, Binding<*>>>()
    val setBindings = mutableMapOf<Key, MutableSet<Binding<*>>>()

    dependencies.forEach { dependency ->
        dependency.mapBindings.forEach { (mapKey, map) ->
            if (map.isNotEmpty()) {
                map.forEach { (entryKey, entryValueBinding) ->
                    val thisMap = mapBindings.getOrPut(mapKey) {
                        mutableMapOf()
                    }
                    // todo override handling
                    thisMap[entryKey] = entryValueBinding
                }
            } else {
                // ensure that the empty map exists
                mapBindings.getOrPut(mapKey) { mutableMapOf() }
            }
        }

        dependency.setBindings.forEach { (setKey, set) ->
            if (set.isNotEmpty()) {
                set.forEach { elementBinding ->
                    val thisSet = setBindings.getOrPut(setKey) { mutableSetOf() }
                    // todo override handling
                    thisSet.add(elementBinding)
                }
            } else {
                // ensure that the empty set exists
                setBindings.getOrPut(setKey) { mutableSetOf() }
            }
        }
    }

    modules.forEach { module ->
        module.bindings.forEach { (key, binding) ->
            if ((bindings.contains(key)
                        || dependencyBindingKeys.contains(key)) && !binding.override
            ) {
                error("Already declared key $key")
            }
            bindings[key] = binding
        }

        module.mapBindings.forEach { (mapKey, map) ->
            if (map.isNotEmpty()) {
                map.forEach { (entryKey, entryValueBinding) ->
                    val thisMap = mapBindings.getOrPut(mapKey) {
                        mutableMapOf()
                    }
                    // todo override handling
                    thisMap[entryKey] = entryValueBinding
                }
            } else {
                // ensure that the empty map exists
                mapBindings.getOrPut(mapKey) { mutableMapOf() }
            }
        }

        module.setBindings.forEach { (setKey, set) ->
            if (set.isNotEmpty()) {
                set.forEach { elementBinding ->
                    val thisSet = setBindings.getOrPut(setKey) { mutableSetOf() }
                    // todo override handling
                    thisSet.add(elementBinding)
                }
            } else {
                // ensure that the empty set exists
                setBindings.getOrPut(setKey) { mutableSetOf() }
            }
        }
    }

    /*mapBindings.forEach { (mapKey, map) ->
        bindings[mapKey] = MapBinding(map as Map<Any?, Binding<Any?>>)
        val lazyMapKey = keyOf(
            typeOf<Any?>(
                Map::class, mapKey.type.parameters[0],
                typeOf<Any?>(Lazy::class, mapKey.type.parameters[1])
            ),
            mapKey.name
        )
        bindings[lazyMapKey] = LazyMapBinding(map as Map<Any?, Binding<Any?>>)
        val providerMapKey = keyOf(
            typeOf<Any?>(
                Map::class, mapKey.type.parameters[0],
                typeOf<Any?>(Provider::class, mapKey.type.parameters[1])
            ),
            mapKey.name
        )
        bindings[providerMapKey] = ProviderMapBinding(map as Map<Any?, Binding<Any?>>)
    }

    setBindings.forEach { (setKey, set) ->
        bindings[setKey] = SetBinding(set as Set<Binding<Any?>>)
        val lazySetKey = keyOf(
            typeOf<Any?>(Set::class, typeOf<Any?>(Lazy::class, setKey.type.parameters[0])),
            setKey.name
        )
        bindings[lazySetKey] = LazySetBinding(set as Set<Binding<Any?>>)
        val providerSetKey = keyOf(
            typeOf<Any?>(Set::class, typeOf<Any?>(Provider::class, setKey.type.parameters[0])),
            setKey.name
        )
        bindings[providerSetKey] = ProviderSetBinding(set as Set<Binding<Any?>>)
    }*/

    return Component(scope, bindings, mapBindings, setBindings, dependencies)
}

internal fun Component.getAllBindingKeys(): Set<Key> =
    mutableSetOf<Key>().also { collectBindingKeys(it) }

internal fun Component.collectBindingKeys(
    keys: MutableSet<Key>
) {
    dependencies.forEach { it.collectBindingKeys(keys) }
    keys.addAll(this.bindings.keys)
}