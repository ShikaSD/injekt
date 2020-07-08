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

package com.ivianuu.injekt.comparison.injekt

import com.ivianuu.injekt.ApplicationComponent
import com.ivianuu.injekt.Component
import com.ivianuu.injekt.comparison.base.InjectionTest
import com.ivianuu.injekt.comparison.fibonacci.Fib8
import com.ivianuu.injekt.componentFactory
import com.ivianuu.injekt.get
import com.ivianuu.injekt.runReader

object InjektTest : InjectionTest {

    override val name = "Injekt"

    private var component: ApplicationComponent? = null

    override fun setup() {
        component = componentFactory<InjektTestComponentFactory>().create()
    }

    override fun inject() {
        component!!.runReader { get<Fib8>() }
    }

    override fun shutdown() {
        component = null
    }

}

@Component.Factory
interface InjektTestComponentFactory {
    fun create(): ApplicationComponent
}
