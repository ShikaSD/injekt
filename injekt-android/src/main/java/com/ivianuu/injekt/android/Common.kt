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

package com.ivianuu.injekt.android

import android.content.Context
import android.content.SharedPreferences
import com.ivianuu.injekt.BindingContext
import com.ivianuu.injekt.Module
import com.ivianuu.injekt.single

/**
 * Declare [SharedPreferences]
 */
fun Module.sharedPreferences(
    sharedPreferencesName: String,
    sharedPreferencesMode: Int = Context.MODE_PRIVATE,
    name: String? = null,
    scopeName: String? = null,
    override: Boolean = false,
    eager: Boolean = false
): BindingContext<SharedPreferences> = single(name, scopeName, override, eager) {
    context().getSharedPreferences(sharedPreferencesName, sharedPreferencesMode)!!
}