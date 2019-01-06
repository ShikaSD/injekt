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
import com.ivianuu.injekt.InjektPlugins.logger
import org.mockito.Mockito.mock

/**
 * Sandbox Instance Holder - let execute the definition but return a mock of it
 */
@Suppress("UNCHECKED_CAST")
class SandboxInstance<T : Any>(beanDefinition: BeanDefinition<T>) : Instance<T>(beanDefinition) {

    private var _value: T? = null

    override val isCreated: Boolean
        get() = _value != null

    override fun get(parameters: ParametersDefinition?): T {
        if (_value == null) {
            _value = create(parameters)
        }
        return _value ?: error("SandboxInstance should return a value for $beanDefinition")
    }

    override fun create(parameters: ParametersDefinition?): T {
        try {
            beanDefinition.definition.invoke(
                DefinitionContext(component),
                parameters?.invoke() ?: emptyParameters()
            )
        } catch (e: Exception) {
            when (e) {
                is NoBeanDefinitionFoundException, is InstanceCreationException, is OverrideException -> {
                    throw BrokenDefinitionException("Definition $beanDefinition is broken due to error : $e")
                }
                else -> logger?.debug("sandbox resolution continue on caught error: $e")
            }
        }
        return mock(beanDefinition.type.java) as T
    }

}

class BrokenDefinitionException(msg: String) : Exception(msg)
