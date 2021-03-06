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

package com.ivianuu.injekt.compiler.transform.implicit

import com.ivianuu.injekt.compiler.NameProvider
import com.ivianuu.injekt.compiler.canUseImplicits
import com.ivianuu.injekt.compiler.flatMapFix
import com.ivianuu.injekt.compiler.getContext
import com.ivianuu.injekt.compiler.getReaderConstructor
import com.ivianuu.injekt.compiler.irClassReference
import com.ivianuu.injekt.compiler.isMarkedAsImplicit
import com.ivianuu.injekt.compiler.isReaderLambdaInvoke
import com.ivianuu.injekt.compiler.transform.AbstractInjektTransformer
import com.ivianuu.injekt.compiler.transform.DeclarationGraph
import com.ivianuu.injekt.compiler.transform.Indexer
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetVariable
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.utils.addIfNotNull

class ReaderTrackingTransformer(
    pluginContext: IrPluginContext,
    private val indexer: Indexer,
    private val implicitContextParamTransformer: ImplicitContextParamTransformer
) : AbstractInjektTransformer(pluginContext) {

    private val nameProvider = NameProvider()
    private val newIndexBuilders = mutableListOf<NewIndexBuilder>()

    private data class NewIndexBuilder(
        val tag: String,
        val originatingDeclaration: IrDeclarationWithName,
        val classBuilder: IrClass.() -> Unit
    )

    private sealed class Scope {
        abstract val file: IrFile
        abstract val fqName: FqName
        abstract val invocationContext: IrClass

        class Reader(
            val declaration: IrDeclaration,
            override val invocationContext: IrClass
        ) : Scope() {
            override val file: IrFile
                get() = declaration.file
            override val fqName: FqName
                get() = declaration.descriptor.fqNameSafe
        }

        class RunReader(
            val call: IrCall,
            override val file: IrFile,
            override val fqName: FqName
        ) : Scope() {

            override val invocationContext =
                (call.getValueArgument(1) as IrFunctionExpression)
                    .function
                    .getContext()!!

            fun isBlock(function: IrFunction): Boolean =
                call.getValueArgument(0).let {
                    it is IrFunctionExpression &&
                            it.function == function
                }
        }
    }

    private var currentReaderScope: Scope? = null

    private inline fun <R> inScope(scope: Scope, block: () -> R): R {
        val previousScope = currentReaderScope
        currentReaderScope = scope
        val result = block()
        currentReaderScope = previousScope
        return result
    }

    override fun lower() {
        module.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {

            override fun visitValueParameterNew(declaration: IrValueParameter): IrStatement {
                val defaultValue = declaration.defaultValue
                if (defaultValue != null && defaultValue.expression.type.isTransformedReaderLambda()) {
                    newIndexBuilders += defaultValue.expression
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                declaration.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitValueParameterNew(declaration)
            }

            override fun visitFieldNew(declaration: IrField): IrStatement {
                val initializer = declaration.initializer
                if (initializer != null && initializer.expression.type.isTransformedReaderLambda()) {
                    newIndexBuilders += initializer.expression
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                declaration.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitFieldNew(declaration)
            }

            override fun visitVariable(declaration: IrVariable): IrStatement {
                val initializer = declaration.initializer
                if (initializer != null && initializer.type.isTransformedReaderLambda()) {
                    newIndexBuilders += initializer
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                declaration.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitVariable(declaration)
            }

            override fun visitSetField(expression: IrSetField): IrExpression {
                if (expression.symbol.owner.type.isTransformedReaderLambda()) {
                    newIndexBuilders += expression.value
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                expression.symbol.owner.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitSetField(expression)
            }

            override fun visitSetVariable(expression: IrSetVariable): IrExpression {
                if (expression.symbol.owner.type.isTransformedReaderLambda()) {
                    newIndexBuilders += expression.value
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                expression.symbol.owner.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitSetVariable(expression)
            }

            override fun visitWhen(expression: IrWhen): IrExpression {
                val result = super.visitWhen(expression) as IrWhen
                if (expression.type.isTransformedReaderLambda()) {
                    newIndexBuilders += expression.branches
                        .flatMapFix { it.result.collectReaderLambdaContextsInExpression() }
                        .map { subContext ->
                            readerImplIndexBuilder(
                                expression.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return result
            }

            override fun visitClassNew(declaration: IrClass): IrStatement {
                return if (declaration.canUseImplicits(pluginContext)) {
                    inScope(
                        Scope.Reader(
                            declaration,
                            declaration.getReaderConstructor(pluginContext)!!
                                .getContext()!!
                        )
                    ) {
                        super.visitClassNew(declaration)
                    }
                } else super.visitClassNew(declaration)
            }

            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                if (declaration.returnType.isTransformedReaderLambda()) {
                    val lastBodyStatement =
                        declaration.body?.statements?.lastOrNull() as? IrExpression
                    if (lastBodyStatement != null && lastBodyStatement.type.isTransformedReaderLambda()) {
                        newIndexBuilders += lastBodyStatement
                            .collectReaderLambdaContextsInExpression()
                            .map { subContext ->
                                readerImplIndexBuilder(
                                    declaration.returnType.lambdaContext!!,
                                    subContext
                                )
                            }
                    }

                    if (declaration is IrSimpleFunction) {
                        val field = declaration.correspondingPropertySymbol?.owner?.backingField
                        if (field != null && field.type.isTransformedReaderLambda()) {
                            newIndexBuilders += readerImplIndexBuilder(
                                declaration.returnType.lambdaContext!!,
                                field.type.lambdaContext!!
                            )
                        }
                    }
                }

                return if (declaration.canUseImplicits(pluginContext) &&
                    currentReaderScope.let {
                        it == null || it !is Scope.RunReader ||
                                !it.isBlock(declaration)
                    }
                ) {
                    inScope(
                        Scope.Reader(
                            declaration,
                            declaration.getContext()!!
                        )
                    ) {
                        super.visitFunctionNew(declaration)
                    }
                } else super.visitFunctionNew(declaration)
            }

            override fun visitCall(expression: IrCall): IrExpression {
                return if (expression.symbol.descriptor.fqNameSafe.asString() ==
                    "com.ivianuu.injekt.runReader" ||
                    expression.symbol.descriptor.fqNameSafe.asString() ==
                    "com.ivianuu.injekt.runChildReader"
                ) {
                    if (expression.symbol.descriptor.fqNameSafe.asString() ==
                        "com.ivianuu.injekt.runChildReader"
                    ) {
                        visitPossibleReaderCall(expression)
                    }
                    inScope(
                        Scope.RunReader(
                            expression,
                            currentFile,
                            currentScope!!.scope.scopeOwner.fqNameSafe
                        )
                    ) {
                        super.visitCall(expression)
                    }
                } else {
                    visitPossibleReaderCall(expression)
                    super.visitCall(expression)
                }
            }
        })

        module.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {

            override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
                if (expression.symbol.descriptor.fqNameSafe.asString() ==
                    "com.ivianuu.injekt.runReader" ||
                    expression.symbol.descriptor.fqNameSafe.asString() ==
                    "com.ivianuu.injekt.runChildReader"
                ) return super.visitFunctionAccess(expression)

                val transformedCallee = implicitContextParamTransformer
                    .getTransformedFunction(expression.symbol.owner)

                newIndexBuilders += (0 until expression.valueArgumentsCount)
                    .mapNotNull { index ->
                        expression.getValueArgument(index)
                            ?.let { index to it }
                    }
                    .map { transformedCallee.valueParameters[it.first] to it.second }
                    .filter { it.first.type.isTransformedReaderLambda() }
                    .flatMapFix { (parameter, argument) ->
                        argument.collectReaderLambdaContextsInExpression()
                            .map { context ->
                                (parameter.type.lambdaContext
                                    ?: error("null for ${parameter.dump()}\n${expression.symbol.owner.dump()}")) to context
                            }
                    }
                    .map { (superContext, subContext) ->
                        readerImplIndexBuilder(
                            superContext,
                            subContext
                        )
                    }

                return super.visitFunctionAccess(expression)
            }

            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                if (declaration is IrSimpleFunction &&
                    declaration.isMarkedAsImplicit(pluginContext) &&
                    declaration.overriddenSymbols.isNotEmpty()
                ) {
                    newIndexBuilders += readerImplIndexBuilder(
                        declaration.overriddenSymbols
                            .single()
                            .owner
                            .getContext()!!,
                        declaration
                            .getContext()!!
                    )
                }

                return super.visitFunctionNew(declaration)
            }

        })

        newIndexBuilders.forEach {
            indexer.index(it.originatingDeclaration, it.tag, it.classBuilder)
        }
    }

    private fun visitPossibleReaderCall(call: IrCall) {
        val indexBuilder = when {
            call.isReaderLambdaInvoke(pluginContext) -> {
                val lambdaContext = call.dispatchReceiver!!.type.lambdaContext!!
                val scope = currentReaderScope!!
                readerInvocationIndexBuilder(
                    lambdaContext,
                    scope.invocationContext,
                    true,
                    false
                )
            }
            call.symbol.owner.canUseImplicits(pluginContext) -> {
                val isRunChildReader = call.symbol.descriptor.fqNameSafe.asString() ==
                        "com.ivianuu.injekt.runChildReader"
                val calleeContext = if (isRunChildReader) {
                    (call.getValueArgument(1) as IrFunctionExpression)
                        .function.getContext()!!
                } else {
                    call.symbol.owner.getContext()!!
                }
                val scope = currentReaderScope!!
                readerInvocationIndexBuilder(
                    calleeContext,
                    scope.invocationContext,
                    false,
                    isRunChildReader
                )
            }
            else -> null
        }
        indexBuilder?.let { newIndexBuilders += it }
    }

    private fun readerInvocationIndexBuilder(
        calleeContext: IrClass,
        invocationContext: IrClass,
        isLambda: Boolean,
        isRunChildReader: Boolean
    ) = NewIndexBuilder(
        DeclarationGraph.READER_INVOCATION_TAG,
        invocationContext
    ) {
        annotations += DeclarationIrBuilder(pluginContext, invocationContext.symbol).run {
            irCall(
                (if (isLambda) symbols.readerInvocation else
                    symbols.readerInvocation).constructors.single()
            ).apply {
                putValueArgument(
                    0,
                    irClassReference(calleeContext)
                )
                putValueArgument(
                    1,
                    irClassReference(invocationContext)
                )
                putValueArgument(
                    2,
                    irBoolean(isLambda)
                )
                putValueArgument(
                    3,
                    irBoolean(isRunChildReader)
                )
            }
        }
    }

    private fun readerImplIndexBuilder(
        superContext: IrClass,
        subContext: IrClass
    ) = NewIndexBuilder(
        DeclarationGraph.READER_IMPL_TAG,
        subContext
    ) {
        annotations += DeclarationIrBuilder(pluginContext, subContext.symbol).run {
            irCall(symbols.readerImpl.constructors.single()).apply {
                putValueArgument(
                    0,
                    irClassReference(superContext)
                )
                putValueArgument(
                    1,
                    irClassReference(subContext)
                )
            }
        }
    }

    private fun IrExpression.collectReaderLambdaContextsInExpression(): Set<IrClass> {
        val contexts = mutableSetOf<IrClass>()

        if (type.isTransformedReaderLambda()) {
            contexts.addIfNotNull(type.lambdaContext)
        }

        when (this) {
            is IrGetField -> {
                if (symbol.owner.type.isTransformedReaderLambda()) {
                    contexts.addIfNotNull(symbol.owner.type.lambdaContext)
                }
            }
            is IrGetValue -> {
                if (symbol.owner.type.isTransformedReaderLambda()) {
                    contexts.addIfNotNull(symbol.owner.type.lambdaContext)
                }
            }
            is IrFunctionExpression -> {
                contexts.addIfNotNull(function.getContext())
            }
            is IrCall -> {
                contexts.addIfNotNull(symbol.owner.getContext())
            }
        }

        return contexts
    }

}
