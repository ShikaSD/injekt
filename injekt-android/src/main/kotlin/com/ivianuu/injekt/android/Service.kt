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

package com.ivianuu.injekt.android

import android.app.Service
import android.content.Context
import android.content.res.Resources
import com.ivianuu.injekt.Given
import com.ivianuu.injekt.Reader
import com.ivianuu.injekt.given
import com.ivianuu.injekt.runChildReader

inline fun <R> Service.runServiceReader(block: @Reader () -> R): R =
    application.runApplicationReader {
        runChildReader(this) {
            block()
        }
    }

typealias ServiceContext = Context

typealias ServiceResources = Resources

object ServiceModule {

    @Given
    fun context(): ServiceContext = given<Service>()

    @Given
    fun resources(): ServiceResources = given<Service>().resources

}
