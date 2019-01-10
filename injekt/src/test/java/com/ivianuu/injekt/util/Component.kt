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

/**
import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.Component
import kotlin.reflect.KClass

inline fun <reified T : Any> Component.getBinding(
name: String? = null
) = getBinding(T::class, name)

fun <T : Any> Component.getBinding(
type: KClass<T>,
name: String? = null
) = beanRegistry.findDefinition(type, name) as? Binding<T>
?: error("binding not found")*/