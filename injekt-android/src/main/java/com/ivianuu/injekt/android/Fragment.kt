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

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.ivianuu.injekt.*
import com.ivianuu.injekt.constant.constant

/**
 * Fragment scope
 */
object FragmentScope : Scope

/**
 * Child fragment scope
 */
object ChildFragmentScope : Scope

/**
 * Fragment name
 */
object ForFragment : Qualifier

/**
 * Child fragment name
 */
object ForChildFragment : Qualifier

/**
 * Returns a [Component] with convenient configurations
 */
fun <T : Fragment> T.fragmentComponent(
    scope: Scope? = FragmentScope,
    modules: Iterable<Module> = emptyList(),
    dependencies: Iterable<Component> = emptyList()
): Component = androidComponent(
    scope, modules, dependencies,
    { fragmentModule() },
    { getClosestComponentOrNull() }
)

/**
 * Returns a [Component] with convenient configurations
 */
fun <T : Fragment> T.childFragmentComponent(
    scope: Scope? = ChildFragmentScope,
    modules: Iterable<Module> = emptyList(),
    dependencies: Iterable<Component> = emptyList()
): Component = androidComponent(
    scope, modules, dependencies,
    { childFragmentModule() },
    { getClosestComponentOrNull() }
)

/**
 * Returns the closest [Component] or null
 */
fun Fragment.getClosestComponentOrNull(): Component? {
    return getParentFragmentComponentOrNull()
        ?: getActivityComponentOrNull()
        ?: getApplicationComponentOrNull()
}

/**
 * Returns the closest [Component]
 */
fun Fragment.getClosestComponent(): Component =
    getClosestComponentOrNull() ?: error("No close component found for $this")

/**
 * Returns the [Component] of the parent fragment or null
 */
fun Fragment.getParentFragmentComponentOrNull(): Component? =
    (parentFragment as? InjektTrait)?.component

/**
 * Returns the [Component] of the parent fragment or throws
 */
fun Fragment.getParentFragmentComponent(): Component =
    getParentFragmentComponentOrNull() ?: error("No parent fragment component found for $this")

/**
 * Returns the [Component] of the activity or null
 */
fun Fragment.getActivityComponentOrNull(): Component? =
    (activity as? InjektTrait)?.component

/**
 * Returns the [Component] of the activity or throws
 */
fun Fragment.getActivityComponent(): Component =
    getActivityComponentOrNull() ?: error("No activity component found for $this")

/**
 * Returns the [Component] of the application or null
 */
fun Fragment.getApplicationComponentOrNull(): Component? =
    (activity?.application as? InjektTrait)?.component

/**
 * Returns the [Component] of the application or throws
 */
fun Fragment.getApplicationComponent(): Component =
    getApplicationComponentOrNull() ?: error("No application component found for $this")


/**
 * Returns a [Module] with convenient bindings
 */
fun <T : Fragment> T.fragmentModule(): Module = module {
    include(internalFragmentModule(ForFragment))
}

/**
 * Returns a [Module] with convenient bindings
 */
fun <T : Fragment> T.childFragmentModule(): Module = module {
    include(internalFragmentModule(ForChildFragment))
}

private fun <T : Fragment> T.internalFragmentModule(name: Qualifier) = module {
    constant(this@internalFragmentModule, override = true).apply {
        bindType<Fragment>()
        bindAlias<Fragment>(name)
        bindType<LifecycleOwner>()
        bindAlias<LifecycleOwner>(name)
        bindType<ViewModelStoreOwner>()
        bindAlias<ViewModelStoreOwner>(name)
        bindType<SavedStateRegistryOwner>()
        bindAlias<SavedStateRegistryOwner>(name)
    }

    factory(override = true) { requireContext() } bindName name
    factory(override = true) { resources } bindName name
    factory(override = true) { lifecycle } bindName name
    factory(override = true) { viewModelStore } bindName name
    factory(override = true) { savedStateRegistry } bindName name
    factory(override = true) { childFragmentManager } bindName name
}