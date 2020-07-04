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
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.ivianuu.injekt.ChildFactory
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.alias
import com.ivianuu.injekt.composition.CompositionComponent
import com.ivianuu.injekt.composition.CompositionFactory
import com.ivianuu.injekt.get
import com.ivianuu.injekt.composition.parent
import com.ivianuu.injekt.composition.runReader
import com.ivianuu.injekt.create
import com.ivianuu.injekt.unscoped

@Target(AnnotationTarget.TYPE)
@Qualifier
annotation class ForFragment

@CompositionComponent
interface FragmentComponent

val Fragment.fragmentComponent: FragmentComponent
    get() = lifecycle.singleton {
        activity!!.activityComponent.runReader {
            get<@ChildFactory (Fragment) -> FragmentComponent>()(this)
        }
    }

@CompositionFactory
fun createFragmentComponent(instance: Fragment): FragmentComponent {
    parent<ActivityComponent>()
    unscoped { instance }
    unscoped<@ForFragment Context> { get<Fragment>().context!! }
    unscoped<@ForFragment Resources> { get<Fragment>().resources }
    alias<Fragment, @ForFragment LifecycleOwner>()
    alias<Fragment, @ForFragment SavedStateRegistryOwner>()
    alias<Fragment, @ForFragment ViewModelStoreOwner>()
    return create()
}
