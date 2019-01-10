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

package com.ivianuu.injekt.test

import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.Component
import org.mockito.Mockito.mock
import kotlin.reflect.KClass

inline fun <reified T : Any> Component.declareMock(
    name: String? = null,
    noinline stubbing: (T.() -> Unit)? = null
) = declareMock(T::class, name, stubbing)

/**
 * Declares a mocked version of [type] and [name]
 */
fun <T : Any> Component.declareMock(
    type: KClass<T>,
    name: String? = null,
    stubbing: (T.() -> Unit)? = null
): T {
    val foundBinding = getBindings().first {
        if (name != null) {
            it.name == name
        } else {
            it.type == type
        }
    } as Binding<T>

    val binding = foundBinding.cloneForMock(type)
    addBinding(binding)

    return applyStub(type, stubbing)
}

fun <T : Any> Component.applyStub(
    type: KClass<T>,
    stubbing: (T.() -> Unit)?
): T {
    val instance: T = get(type)
    stubbing?.let { instance.apply(stubbing) }
    return instance
}

fun <T : Any> Binding<T>.cloneForMock(type: KClass<T>): Binding<T> =
        copy(definition = { mock(type.java) })