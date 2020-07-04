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

import com.ivianuu.injekt.internal.injektIntrinsic

fun <T> dependency(dependency: T): Unit = injektIntrinsic()

fun <T : Function<*>> childFactory(factory: T): Unit = injektIntrinsic()

fun <S : T, T> alias(): Unit = injektIntrinsic()

fun <T> unscoped(provider: @Reader Function<T> = injektIntrinsic()): Unit = injektIntrinsic()

annotation class Unscoped

fun <T> scoped(provider: @Reader Function<T> = injektIntrinsic()): Unit = injektIntrinsic()

annotation class Scoped<T>
