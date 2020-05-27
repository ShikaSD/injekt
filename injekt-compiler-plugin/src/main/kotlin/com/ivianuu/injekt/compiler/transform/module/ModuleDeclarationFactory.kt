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

package com.ivianuu.injekt.compiler.transform.module

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektSymbols
import com.ivianuu.injekt.compiler.InjektWritableSlices
import com.ivianuu.injekt.compiler.NameProvider
import com.ivianuu.injekt.compiler.getFunctionFromLambdaExpression
import com.ivianuu.injekt.compiler.irTrace
import com.ivianuu.injekt.compiler.remapTypeParameters
import com.ivianuu.injekt.compiler.transform.InjektDeclarationIrBuilder
import com.ivianuu.injekt.compiler.transform.InjektDeclarationStore
import com.ivianuu.injekt.compiler.typeArguments
import com.ivianuu.injekt.compiler.typeOrFail
import com.ivianuu.injekt.compiler.typeWith
import com.ivianuu.injekt.compiler.withAnnotations
import com.ivianuu.injekt.compiler.withNoArgAnnotations
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getArgumentsWithIr
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class ModuleDeclarationFactory(
    private val moduleFunction: IrFunction,
    private val moduleClass: IrClass,
    private val pluginContext: IrPluginContext,
    private val declarationStore: InjektDeclarationStore,
    private val nameProvider: NameProvider,
    private val symbols: InjektSymbols
) {

    fun createDeclarations(
        callee: IrFunction,
        call: IrCall
    ): List<ModuleDeclaration> {
        val calleeFqName = callee.descriptor.fqNameSafe.asString()

        return when {
            calleeFqName == "com.ivianuu.injekt.scope" ->
                listOf(createScopeDeclaration(call))
            calleeFqName == "com.ivianuu.injekt.dependency" ->
                listOf(createDependencyDeclaration(call))
            calleeFqName == "com.ivianuu.injekt.childFactory" ->
                listOf(createChildFactoryDeclaration(call))
            calleeFqName == "com.ivianuu.injekt.alias" ->
                listOf(createAliasDeclaration(call))
            calleeFqName == "com.ivianuu.injekt.transient" ||
                    calleeFqName == "com.ivianuu.injekt.scoped" ||
                    calleeFqName == "com.ivianuu.injekt.instance" ->
                listOf(createBindingDeclaration(call))
            calleeFqName == "com.ivianuu.injekt.map" ->
                createMapDeclarations(call)
            calleeFqName == "com.ivianuu.injekt.set" ->
                createSetDeclarations(call)
            callee.hasAnnotation(InjektFqNames.Module) ||
                    call.isModuleLambdaInvoke() ->
                listOf(createIncludedModuleDeclaration(call, callee))
            else -> emptyList()
        }
    }

    private fun createScopeDeclaration(call: IrCall): ScopeDeclaration =
        ScopeDeclaration(call.getTypeArgument(0)!!)

    private fun createDependencyDeclaration(call: IrCall): DependencyDeclaration {
        val dependencyType = call.getTypeArgument(0)!!
            .remapTypeParameters(moduleFunction, moduleClass)
        val property = InjektDeclarationIrBuilder(pluginContext, moduleClass.symbol)
            .fieldBakedProperty(
                moduleClass,
                nameProvider.allocateForType(dependencyType),
                dependencyType
            )
        return DependencyDeclaration(
            dependencyType,
            property,
            call.getValueArgument(0)!!
        )
    }

    private fun createChildFactoryDeclaration(call: IrCall): ChildFactoryDeclaration {
        val factoryRef = call.getValueArgument(0)!! as IrFunctionReference
        val factoryModuleClass = declarationStore.getModuleClassForFunction(
            declarationStore.getModuleFunctionForFactory(factoryRef.symbol.owner)
        )
        return ChildFactoryDeclaration(factoryRef, factoryModuleClass)
    }

    private fun createAliasDeclaration(call: IrCall): AliasDeclaration =
        AliasDeclaration(call.getTypeArgument(0)!!, call.getTypeArgument(1)!!)

    private fun createBindingDeclaration(call: IrCall): BindingDeclaration {
        val bindingQualifiers =
            pluginContext.irTrace[InjektWritableSlices.QUALIFIERS, call] ?: emptyList()
        val bindingType = call.getTypeArgument(0)!!
            .remapTypeParameters(moduleFunction, moduleClass)
            .withAnnotations(bindingQualifiers)
        return createBindingDeclarationFromSingleArgument(
            bindingType,
            if (call.valueArgumentsCount != 0) call.getValueArgument(0) else null,
            call.symbol.owner.name.asString() == "instance",
            call.symbol.owner.name.asString() == "scoped"
        )
    }

    private fun createMapDeclarations(call: IrCall): List<ModuleDeclaration> {
        val declarations = mutableListOf<ModuleDeclaration>()
        val mapQualifiers =
            pluginContext.irTrace[InjektWritableSlices.QUALIFIERS, call] ?: emptyList()
        val mapKeyType = call.getTypeArgument(0)!!
        val mapValueType = call.getTypeArgument(1)!!

        val mapType = pluginContext.referenceClass(KotlinBuiltIns.FQ_NAMES.map)!!
            .typeWith(mapKeyType, mapValueType)
            .withAnnotations(mapQualifiers)

        declarations += MapDeclaration(mapType)

        val mapBlock = call.getValueArgument(0) as? IrFunctionExpression
        mapBlock?.function?.body?.transformChildrenVoid(object :
            IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol == symbols.mapDsl.functions.single { it.descriptor.name.asString() == "put" }) {
                    declarations += MapEntryDeclaration(
                        mapType,
                        expression.getValueArgument(0)!!,
                        expression.getTypeArgument(0)!!
                    )
                }

                return super.visitCall(expression)
            }
        })

        return declarations
    }

    private fun createSetDeclarations(call: IrCall): List<ModuleDeclaration> {
        val declarations = mutableListOf<ModuleDeclaration>()
        val setQualifiers =
            pluginContext.irTrace[InjektWritableSlices.QUALIFIERS, call] ?: emptyList()
        val setElementType = call.getTypeArgument(0)!!

        val setType = pluginContext.referenceClass(KotlinBuiltIns.FQ_NAMES.set)!!
            .typeWith(setElementType)
            .withAnnotations(setQualifiers)

        declarations += SetDeclaration(setType)

        val setBlock = call.getValueArgument(0) as? IrFunctionExpression
        setBlock?.function?.body?.transformChildrenVoid(object :
            IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol == symbols.setDsl.functions.single { it.descriptor.name.asString() == "add" }) {
                    declarations += SetElementDeclaration(
                        setType,
                        expression.getTypeArgument(0)!!
                    )
                }
                return super.visitCall(expression)
            }
        })

        return declarations
    }

    private fun IrCall.isModuleLambdaInvoke(): Boolean {
        return origin == IrStatementOrigin.INVOKE &&
                dispatchReceiver?.type?.hasAnnotation(InjektFqNames.Module) == true
    }

    private fun createIncludedModuleDeclaration(
        call: IrCall,
        includedModuleFunction: IrFunction
    ): ModuleDeclaration {
        val includedClass: IrClass
        val includedType: IrType

        if (call.isModuleLambdaInvoke()) {
            includedClass = pluginContext.irBuiltIns.anyClass.owner
            includedType = pluginContext.irBuiltIns.anyType
        } else {
            includedClass = includedModuleFunction.returnType.classOrNull!!.owner
            includedType = includedModuleFunction.returnType
                .typeWith(*call.typeArguments.toTypedArray())
        }

        val property = InjektDeclarationIrBuilder(pluginContext, includedClass.symbol)
            .fieldBakedProperty(
                moduleClass,
                Name.identifier(nameProvider.allocateForGroup(includedModuleFunction.name.asString())),
                includedType
            )

        val moduleLambdaTypeMap = call.getArgumentsWithIr()
            .filter { it.first.type.hasAnnotation(InjektFqNames.Module) }
            .map { (valueParameter, expression) ->
                valueParameter to if (expression is IrFunctionExpression) {
                    expression.function.returnType
                } else expression.type.typeArguments.last().typeOrFail
            }
            .toMap()

        return IncludedModuleDeclaration(
            includedType,
            moduleLambdaTypeMap,
            property,
            call
        )
    }

    private fun createBindingDeclarationFromSingleArgument(
        bindingType: IrType,
        singleArgument: IrExpression?,
        instance: Boolean,
        scoped: Boolean
    ): BindingDeclaration {
        val property: IrProperty
        val variableExpression: IrExpression
        val parameters =
            mutableListOf<InjektDeclarationIrBuilder.FactoryParameter>()

        if (instance) {
            property = InjektDeclarationIrBuilder(pluginContext, moduleClass.symbol)
                .fieldBakedProperty(
                    moduleClass,
                    nameProvider.allocateForType(bindingType),
                    bindingType
                )
            variableExpression = singleArgument!!
        } else if (singleArgument != null) {
            val providerType = singleArgument.type
                .withNoArgAnnotations(pluginContext, listOf(InjektFqNames.Provider))
            variableExpression = singleArgument
            val parameterNameProvider = NameProvider()

            providerType.typeArguments.dropLast(1).forEach {
                parameters += InjektDeclarationIrBuilder.FactoryParameter(
                    parameterNameProvider.allocateForType(it.typeOrFail).asString(),
                    it.typeOrFail
                        .remapTypeParameters(moduleFunction, moduleClass),
                    it.typeOrFail.hasAnnotation(InjektFqNames.AstAssisted)
                )
            }
            property = InjektDeclarationIrBuilder(pluginContext, moduleClass.symbol)
                .fieldBakedProperty(
                    moduleClass,
                    nameProvider.allocateForType(bindingType),
                    variableExpression.type
                        .remapTypeParameters(moduleFunction, moduleClass)
                )
        } else {
            val clazz = bindingType.classOrNull!!.owner
            val providerExpression =
                InjektDeclarationIrBuilder(pluginContext, moduleFunction.symbol)
                    .classFactoryLambda(
                        clazz,
                        declarationStore.getMembersInjectorForClassOrNull(clazz)
                    )
            val providerFunction = providerExpression.getFunctionFromLambdaExpression()
            variableExpression = providerExpression
            providerFunction.valueParameters.forEach {
                parameters += InjektDeclarationIrBuilder.FactoryParameter(
                    it.name.asString(),
                    it.type
                        .remapTypeParameters(moduleFunction, moduleClass),
                    it.type.hasAnnotation(InjektFqNames.AstAssisted)
                )
            }
            property = InjektDeclarationIrBuilder(pluginContext, moduleClass.symbol)
                .fieldBakedProperty(
                    moduleClass,
                    nameProvider.allocateForType(bindingType),
                    variableExpression.type
                        .remapTypeParameters(moduleFunction, moduleClass)
                )
        }

        return BindingDeclaration(
            bindingType = bindingType,
            parameters = parameters,
            scoped = scoped,
            instance = instance,
            property = property,
            variableExpression = variableExpression
        )
    }

}
