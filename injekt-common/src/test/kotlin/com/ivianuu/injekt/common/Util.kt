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

package com.ivianuu.injekt.common

import com.ivianuu.injekt.Factory
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.QualifierMarker
import com.ivianuu.injekt.Scope
import com.ivianuu.injekt.ScopeMarker

interface Command

object Command1 : Command

object Command2 : Command

object Command3 : Command

@QualifierMarker
annotation class TestQualifier1 {
    companion object : Qualifier.Element
}

@QualifierMarker
annotation class TestQualifier2 {
    companion object : Qualifier.Element
}

@QualifierMarker
annotation class TestQualifier3 {
    companion object : Qualifier.Element
}

@ScopeMarker
annotation class TestScopeOne {
    companion object : Scope
}

@ScopeMarker
annotation class TestScopeTwo {
    companion object : Scope
}

@ScopeMarker
annotation class TestScopeThree {
    companion object : Scope
}

@Factory
class TestDep1

@Factory
class TestDep2(val dep1: TestDep1)

@Factory
class TestDep3(val dep1: TestDep1, val dep2: TestDep2)