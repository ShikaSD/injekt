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

import com.ivianuu.injekt.*
import org.mockito.Mockito.mock
import kotlin.reflect.KClass

/**
 * Declares a mocked version of [type] and [qualifier]
 */
inline fun <reified T> Component.declareMock(
    qualifier: Qualifier? = null,
    stubbing: T.() -> Unit = {}
): T {
    val key = Key(T::class, qualifier)
    val foundBinding = getBindings().first {
        it.key == key
    } as Binding<T>

    val binding = foundBinding.cloneForMock(T::class)
    addBinding(binding)

    return applyStub(stubbing)
}

inline fun <reified T> Component.applyStub(
    stubbing: T.() -> Unit
): T {
    val instance = get<T>(T::class)
    stubbing.let { instance.apply(stubbing) }
    return instance
}

fun <T> Binding<T>.cloneForMock(type: KClass<*>): Binding<T> =
    copy(definition = { mock<T>(type.java as Class<T>) })