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

package com.ivianuu.injekt.compiler

import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.BindingFactory
import com.ivianuu.injekt.Component
import com.ivianuu.injekt.Parameters
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlin.reflect.KClass

class BindingFactoryGenerator(private val descriptor: BindingFactoryDescriptor) {

    fun generate() =
        FileSpec.builder(descriptor.factoryName.packageName, descriptor.factoryName.simpleName)
            .apply {
                val imports = imports()
                if (imports.isNotEmpty()) {
                    addImport("com.ivianuu.injekt", *imports().toTypedArray())
                }
            }
            .addType(bindingFactory())
            .build()

    private fun imports() = mutableSetOf("getBinding").apply {
        if (descriptor.isSingle) add("asSingle")
    }

    private fun bindingFactory() = TypeSpec.classBuilder(descriptor.factoryName)
        .addSuperinterface(
            BindingFactory::class.asClassName().plusParameter(descriptor.target)
        )
        .addProperty(
            PropertySpec.builder(
                "scope",
                KClass::class.asClassName().plusParameter(
                    WildcardTypeName.producerOf(Annotation::class)
                ).copy(nullable = true),
                KModifier.OVERRIDE
            )
                .apply {
                    if (descriptor.scope != null) {
                        getter(
                            FunSpec.getterBuilder()
                                .addCode("return %T::class", descriptor.scope)
                                .build()
                        )
                    } else {
                        initializer("null")
                    }
                }
                .build()
        )
        .addFunction(
            FunSpec.builder("create")
                .addModifiers(KModifier.OVERRIDE)
                .returns(Binding::class.asClassName().plusParameter(descriptor.target))
                .apply {
                    if (descriptor.isSingle) {
                        addCode("return BindingImpl().asSingle()")
                    } else {
                        addCode("return BindingImpl()")
                    }
                }
                .build()
        )
        .addType(bindingImpl())
        .build()

    private fun bindingImpl() = TypeSpec.classBuilder("BindingImpl")
        .addModifiers(KModifier.PRIVATE)
        .addSuperinterface(
            Binding::class.asClassName().plusParameter(descriptor.target)
        )
        .apply {
            descriptor.constructorParams
                .filterIsInstance<ParamDescriptor.Dependency>()
                .forEach { param ->
                    addProperty(
                        PropertySpec.builder(
                            param.paramName + "Binding",
                            Binding::class.asTypeName().plusParameter(param.paramType)
                        )
                            .addModifiers(KModifier.PRIVATE, KModifier.LATEINIT)
                            .mutable()
                            .build()
                    )
                }
        }
        .apply {
            if (descriptor.constructorParams.isNotEmpty()) {
                addFunction(
                    FunSpec.builder("attach")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("component", Component::class)
                        .addCode(
                            CodeBlock.builder()
                                .apply {
                                    descriptor.constructorParams
                                        .filterIsInstance<ParamDescriptor.Dependency>()
                                        .forEach { param ->
                                            if (param.qualifierName != null) {
                                                addStatement(
                                                    "${param.paramName}Binding = component.getBinding(%T)",
                                                    param.qualifierName
                                                )
                                            } else {
                                                addStatement("${param.paramName}Binding = component.getBinding()")
                                            }
                                        }
                                }
                                .build()
                        )
                        .build()
                )
            }
        }
        .addFunction(
            FunSpec.builder("get")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(
                    "parameters",
                    LambdaTypeName.get(
                        returnType = Parameters::class.asClassName()
                    ).copy(nullable = true)
                )
                .returns(descriptor.target)
                .addCode(createBody())
                .build()
        )
        .build()

    private fun createBody() = CodeBlock.builder()
        .apply {
            if (descriptor.constructorParams.any { it is ParamDescriptor.Parameter }) {
                add("val params = parameters?.invoke()\n")
            }
        }
        .add("return %T(\n", descriptor.target)
        .indent()
        .apply {
            descriptor.constructorParams.forEachIndexed { i, param ->
                when (param) {
                    is ParamDescriptor.Parameter -> {
                        add("${param.paramName} = params!!.get(${param.index})")
                    }
                    is ParamDescriptor.Dependency -> {
                        add("${param.paramName} = ${param.paramName}Binding()")
                    }
                }

                if (i != descriptor.constructorParams.lastIndex) {
                    add(",\n")
                }
            }
        }
        .unindent()
        .add("\n")
        .add(")")
        .add("\n")
        .build()
}