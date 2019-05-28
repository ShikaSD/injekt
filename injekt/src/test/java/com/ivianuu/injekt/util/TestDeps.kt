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

package com.ivianuu.injekt.util

import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.Creator
import com.ivianuu.injekt.FactoryKind
import com.ivianuu.injekt.binding

class TestDep1

class TestDep1__Creator : Creator<TestDep1> {
    override fun create(): Binding<TestDep1> = binding(
        kind = FactoryKind, definition = { TestDep1() }
    )
}

class TestDep2(val dep1: TestDep1)
class TestDep3(val dep1: TestDep1, val dep2: TestDep2)