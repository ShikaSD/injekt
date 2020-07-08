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

package com.ivianuu.injekt.compiler.transform.component

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.dumpSrc
import com.ivianuu.injekt.compiler.flatMapFix
import com.ivianuu.injekt.compiler.infoPackageFile
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class DeclarationGraph(
    private val module: IrModuleFragment,
    private val pluginContext: IrPluginContext
) {

    val componentFactories: List<ComponentFactory> get() = _componentFactories
    private val _componentFactories = mutableListOf<ComponentFactory>()

    val bindings: List<Binding> get() = _bindings
    private val _bindings = mutableListOf<Binding>()

    fun initialize() {
        collectComponentFactories()
        if (_componentFactories.isNotEmpty()) {
            collectBindings()
        }
    }

    private fun collectComponentFactories() {
        val memberScope = pluginContext.moduleDescriptor.getPackage(InjektFqNames.InfoPackage)
            .memberScope

        (module.infoPackageFile.declarations
            .filterIsInstance<IrClass>() +
                (memberScope.getClassifierNames() ?: emptySet()).map {
                    memberScope.getContributedClassifier(
                        it,
                        NoLookupLocation.FROM_BACKEND
                    )!!
                }.map { pluginContext.referenceClass(it.fqNameSafe)!!.owner })
            .mapNotNull {
                val infoAnnotation = it.getAnnotation(InjektFqNames.InjektInfo)!!
                val fqName = infoAnnotation.getValueArgument(0)!!
                    .let { it as IrConst<String> }
                    .value
                    .let { FqName(it) }
                val type = infoAnnotation.getValueArgument(2)!!
                    .let { it as IrConst<String> }
                    .value
                if (type == "class") pluginContext.referenceClass(fqName)!!.owner
                else null
            }
            .forEach { _componentFactories += ComponentFactory(it) }
    }

    private fun collectBindings() {
        val memberScope = pluginContext.moduleDescriptor.getPackage(InjektFqNames.InfoPackage)
            .memberScope

        (module.infoPackageFile.declarations
            .filterIsInstance<IrClass>() +
                (memberScope.getClassifierNames() ?: emptySet()).map {
                    memberScope.getContributedClassifier(
                        it,
                        NoLookupLocation.FROM_BACKEND
                    )!!
                }.map { pluginContext.referenceClass(it.fqNameSafe)!!.owner })
            .flatMapFix {
                val infoAnnotation = it.getAnnotation(InjektFqNames.InjektInfo)!!
                val fqName = infoAnnotation.getValueArgument(0)!!
                    .let { it as IrConst<String> }
                    .value
                    .let { FqName(it) }
                val type = infoAnnotation.getValueArgument(2)!!
                    .let { it as IrConst<String> }
                    .value
                if (type == "function") pluginContext.referenceFunctions(fqName)
                    .map { it.owner }
                else emptyList()
            }
            .forEach { _bindings += Binding(it) }
    }

}

class Component(
    val clazz: IrClass
)

class ComponentFactory(
    val factory: IrClass
)

class Binding(
    val function: IrFunction
)

class MapElementsBinding(
    val function: IrFunction
)

class SetElementsBinding(
    val function: IrFunction
)