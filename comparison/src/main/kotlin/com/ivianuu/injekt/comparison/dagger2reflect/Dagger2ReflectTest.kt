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

package com.ivianuu.injekt.comparison.dagger2reflect

import com.ivianuu.injekt.comparison.base.InjectionTest
import com.ivianuu.injekt.comparison.dagger2.Dagger2Component
import dagger.Dagger

object Dagger2ReflectTest : InjectionTest {

    override val name = "Dagger 2 Reflect"

    private var component: Dagger2Component? = null

    override fun setup() {
        component = Dagger.factory(Dagger2Component.Factory::class.java).create()
    }

    override fun inject() {
        component!!.fib8
    }

    override fun shutdown() {
        component = null
    }
}
