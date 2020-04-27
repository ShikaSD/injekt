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

import junit.framework.Assert.assertEquals
import org.junit.Test

class MapTest {

    @Test
    fun testMapBinding() {
        val component = Component(Module {
            map<String, Command>(mapQualifier = TestQualifier1::class) {
                put("one") { Command1 }
                put("two") { Command2 }
                put("three") { Command3 }
            }
        })

        val map = component.get<Map<String, Command>>(qualifier = TestQualifier1::class)
        assertEquals(3, map.size)
        assertEquals(map["one"], Command1)
        assertEquals(map["two"], Command2)
        assertEquals(map["three"], Command3)

        val providerMap =
            component.get<Map<String, Provider<Command>>>(qualifier = TestQualifier1::class)
        assertEquals(3, providerMap.size)
        assertEquals(providerMap.getValue("one")(), Command1)
        assertEquals(providerMap.getValue("two")(), Command2)
        assertEquals(
            providerMap.getValue("three")(),
            Command3
        )

        val lazyMap = component.get<Map<String, Lazy<Command>>>(qualifier = TestQualifier1::class)
        assertEquals(3, lazyMap.size)
        assertEquals(lazyMap.getValue("one")(), Command1)
        assertEquals(lazyMap.getValue("two")(), Command2)
        assertEquals(lazyMap.getValue("three")(), Command3)
    }

    @Test(expected = IllegalStateException::class)
    fun testThrowsOnNonDeclaredMapBinding() {
        val component = Component()
        component.get<Map<String, Int>>()
    }

    @Test
    fun testReturnsEmptyOnADeclaredMapBindingWithoutElements() {
        val component = Component(Module {
            map<String, Int>()
        })

        assertEquals(0, component.get<Map<String, Int>>().size)
    }

    @Test
    fun testNestedMapBindings() {
        val componentA = Component(Module {
            map<String, Command> { put("one") { Command1 } }
        })

        val mapA = componentA.get<Map<String, Command>>()
        assertEquals(1, mapA.size)
        assertEquals(Command1, mapA["one"])

        val componentB = componentA.plus<TestScope1>(Module {
            map<String, Command> { put("two") { Command2 } }
        })

        val mapB = componentB.get<Map<String, Command>>()
        assertEquals(2, mapB.size)
        assertEquals(Command1, mapA["one"])
        assertEquals(Command2, mapB["two"])

        val componentC = componentB.plus<TestScope2>(Module {
            map<String, Command> { put("three") { Command3 } }
        })

        val mapC = componentC.get<Map<String, Command>>()
        assertEquals(3, mapC.size)
        assertEquals(Command1, mapA["one"])
        assertEquals(Command2, mapB["two"])
        assertEquals(Command3, mapC["three"])
    }

    @Test(expected = IllegalStateException::class)
    fun testOverride() {
        Component(Module {
            map<String, Command> {
                put("key") { Command1 }
                put("key") { Command2 }
            }
        })
    }

    @Test(expected = IllegalStateException::class)
    fun testNestedOverride() {
        val componentA = Component(Module {
            factory { Command1 }
            map<String, Command> {
                put(
                    "key",
                    keyOf<Command1>()
                )
            }
        })
        val componentB = componentA.plus<TestScope1>(Module {
            factory { Command2 }
            map<String, Command> {
                put("key", keyOf<Command2>())
            }
        })
    }

    @Test
    fun testReusesMapBuildersInsideAModule() {
        val component = Component(Module {
            instance(Command1)
            instance(Command2)
            map<String, Any> {
                put(
                    "a",
                    keyOf<Command1>()
                )
            }
            map<String, Any> {
                put(
                    "b",
                    keyOf<Command2>()
                )
            }
        })

        assertEquals(2, component.get<Map<String, Any>>().size)
    }
}