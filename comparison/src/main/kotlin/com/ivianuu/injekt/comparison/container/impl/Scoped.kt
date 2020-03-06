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

package com.ivianuu.injekt.comparison.container.impl

import com.ivianuu.injekt.Parameters

class ScopedProvider<T>(private val provider: Container.(Parameters) -> T) : (Container, Parameters) -> T, ContainerLifecycleObserver<T> {
    private lateinit var container: Container
    override fun onInit(container: Container) {
        this.container = container
    }
    override fun invoke(p1: Container, p2: Parameters): T {
        return provider(container, p2)
    }
}