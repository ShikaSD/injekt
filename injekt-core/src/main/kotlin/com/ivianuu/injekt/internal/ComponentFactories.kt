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

package com.ivianuu.injekt.internal

import kotlin.reflect.KClass

object ComponentFactories {

    private val factories = mutableMapOf<KClass<*>, Any>()

    fun register(component: KClass<*>, factory: Any) {
        factories[component] = factory
    }

    fun <T> get(component: KClass<*>): T {
        return factories[component] as? T
            ?: error("Couldn't get factory for component ${component.java.name}")
    }
}