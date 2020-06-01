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

import android.content.Context
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistryOwner
import com.ivianuu.injekt.ChildFactory
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.Scope
import com.ivianuu.injekt.alias
import com.ivianuu.injekt.composition.CompositionComponent
import com.ivianuu.injekt.composition.CompositionFactory
import com.ivianuu.injekt.composition.get
import com.ivianuu.injekt.composition.parent
import com.ivianuu.injekt.create
import com.ivianuu.injekt.instance
import com.ivianuu.injekt.scope
import com.ivianuu.injekt.transient
import kotlinx.coroutines.CoroutineScope

@Scope
annotation class ActivityScoped

@Target(AnnotationTarget.TYPE)
@Qualifier
annotation class ForActivity

@CompositionComponent
interface ActivityComponent

val ComponentActivity.activityComponent: ActivityComponent
    get() = lifecycle.singleton {
        retainedActivityComponent
            .get<@ChildFactory (ComponentActivity) -> ActivityComponent>()(this)
    }

@CompositionFactory
fun createActivityComponent(instance: ComponentActivity): ActivityComponent {
    parent<RetainedActivityComponent>()
    scope<ActivityScoped>()
    instance(instance)
    alias<ComponentActivity, @ForActivity Context>()
    transient<@ForActivity Resources> { activity: ComponentActivity ->
        activity.resources
    }
    transient<@ForActivity CoroutineScope> { lifecycleOwner: @ForActivity LifecycleOwner ->
        lifecycleOwner.lifecycleScope
    }
    alias<ComponentActivity, @ForActivity LifecycleOwner>()
    alias<ComponentActivity, @ForActivity SavedStateRegistryOwner>()
    alias<ComponentActivity, @ForActivity ViewModelStoreOwner>()
    return create()
}
