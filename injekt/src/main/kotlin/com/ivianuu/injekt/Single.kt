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

/**
 * Makes the annotated class injectable and generates a single binding for it
 * The class will be created once per [Component]
 *
 * @see Factory
 * @see Name
 * @see Scope
 * @see InjektConstructor
 * @see ModuleBuilder.single
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
annotation class Single

/**
 * Used by generated code
 *
 * @see CodegenJustInTimeLookupFactory
 */
interface IsSingle

/**
 * Singleton provider
 */
class SingleProvider<T>(private val provider: Provider<T>) : Provider<T> {
    private var _value: Any? = this

    override fun invoke(parameters: Parameters): T {
        var value = _value
        if (value === this) {
            synchronized(this) {
                value = _value
                if (value === this) {
                    _value = provider(parameters)
                    value = _value
                }
            }
        }

        return value as T
    }
}
