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

import androidx.lifecycle.ViewModelProvider
import com.ivianuu.injekt.Given
import com.ivianuu.injekt.Reader
import com.ivianuu.injekt.Scoping
import com.ivianuu.injekt.Storage
import com.ivianuu.injekt.given

@Scoping
object RetainedFragmentScoped {
    @Reader
    inline operator fun <T> invoke(
        key: Any,
        init: () -> T
    ) = given<RetainedFragmentStorage>().scope(key, init)
}

typealias RetainedFragmentStorage = Storage

object RetainedFragmentModule {
    @Given
    val retainedFragmentStorage: RetainedFragmentStorage
        get() {
            return ViewModelProvider(
                given<FragmentViewModelStoreOwner>(),
                RetainedStorageHolder
            )[RetainedStorageHolder::class.java].storage
        }
}
