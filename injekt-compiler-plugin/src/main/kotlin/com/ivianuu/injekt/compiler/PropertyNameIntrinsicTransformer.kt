package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class PropertyNameIntrinsicTransformer(pluginContext: IrPluginContext) :
    AbstractInjektTransformer(pluginContext) {

    override fun visitProperty(declaration: IrProperty): IrStatement {
        declaration.backingField?.initializer?.transformChildrenVoid(object :
            IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                val callee = expression.symbol.owner
                callee.valueParameters.forEach { valueParameter ->
                    if (valueParameter.annotations.hasAnnotation(InjektClassNames.PropertyName) &&
                        expression.getValueArgument(valueParameter.index) == null
                    ) {
                        expression.putValueArgument(
                            valueParameter.index,
                            DeclarationIrBuilder(pluginContext, expression.symbol)
                                .irString(declaration.fqNameWhenAvailable!!.asString())
                        )
                    }
                }
                return super.visitCall(expression)
            }
        })

        return super.visitProperty(declaration)
    }

}