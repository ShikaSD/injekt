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

class BoundProvider<T>(
    private val scope: Any? = null,
    private val provider: BindingProvider<T>
) : (Component, Parameters) -> T, ComponentInitObserver {

    private lateinit var boundComponent: Component

    override fun onInit(component: Component) {
        check(!this::boundComponent.isInitialized) {
            "Already scoped to $component"
        }

        this.boundComponent = if (scope == null) component
        else component.getComponentForScope(scope)

        (provider as? ComponentInitObserver)?.onInit(component)
    }

    override fun invoke(p1: Component, p2: Parameters): T = provider(boundComponent, p2)
}