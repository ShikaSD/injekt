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

package com.ivianuu.injekt.multibinding

import com.ivianuu.injekt.BindingBuilder
import com.ivianuu.injekt.getOrSet

/**
 * Adds this binding into a set
 */
fun BindingBuilder<*>.bindIntoSet(setBinding: SetBinding) {
    attributes.getOrSet(KEY_SET_BINDINGS) {
        hashMapOf<Any, SetBinding>()
    }[setBinding.setName] = setBinding
}

/**
 * Adds this binding into a set
 */
fun <T> BindingBuilder<T>.bindIntoSet(setName: Any) {
    bindIntoSet(SetBinding(setName))
}