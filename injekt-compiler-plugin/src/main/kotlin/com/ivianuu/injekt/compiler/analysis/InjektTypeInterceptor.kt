package com.ivianuu.injekt.compiler.analysis

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektWritableSlices
import com.ivianuu.injekt.compiler.getAnnotatedAnnotations
import com.ivianuu.injekt.compiler.getOrPut
import com.ivianuu.injekt.compiler.hasAnnotatedAnnotations
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptorImpl
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.extensions.internal.InternalNonStableExtensionPoints
import org.jetbrains.kotlin.extensions.internal.TypeResolutionInterceptorExtension
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingContext
import org.jetbrains.kotlin.types.typeUtil.replaceAnnotations

@OptIn(InternalNonStableExtensionPoints::class)
@Suppress("INVISIBLE_REFERENCE", "EXPERIMENTAL_IS_NOT_ENABLED")
class InjektTypeAnnotationResolutionInterceptorExtension(
    private val typeAnnotationChecker: TypeAnnotationChecker
) : TypeResolutionInterceptorExtension {

    override fun interceptFunctionLiteralDescriptor(
        expression: KtLambdaExpression,
        context: ExpressionTypingContext,
        descriptor: AnonymousFunctionDescriptor
    ): AnonymousFunctionDescriptor {
        if (context.expectedType !== TypeUtils.NO_EXPECTED_TYPE &&
            context.expectedType.hasAnnotatedAnnotations(
                InjektFqNames.TypeAnnotation,
                descriptor.module
            )
        ) {
            context.trace.getOrPut(
                InjektWritableSlices.TYPE_ANNOTATIONS,
                descriptor
            ) { mutableSetOf() } +=
                context.expectedType.getAnnotatedAnnotations(
                        InjektFqNames.TypeAnnotation,
                        descriptor.module
                    )
                    .mapNotNull { it.fqName }
        }
        return descriptor
    }

    override fun interceptType(
        element: KtElement,
        context: ExpressionTypingContext,
        resultType: KotlinType
    ): KotlinType {
        if (resultType === TypeUtils.NO_EXPECTED_TYPE) return resultType
        if (element !is KtLambdaExpression) return resultType
        val module = context.scope.ownerDescriptor.module
        val typeAnnotations = typeAnnotationChecker.getTypeAnnotations(
            context.trace,
            element,
            module,
            resultType
        )
        return if (typeAnnotations.isNotEmpty()) {
            resultType.withTypeAnnotations(typeAnnotations, module)
        } else resultType
    }


    private fun KotlinType.withTypeAnnotations(
        typeAnnotations: Set<FqName>,
        module: ModuleDescriptor
    ): KotlinType {
        val additionalAnnotations = typeAnnotations
            .filter { it !in annotations.map { it.fqName!! } }
            .map {
                AnnotationDescriptorImpl(
                    module.findClassAcrossModuleDependencies(
                        ClassId.topLevel(it)
                    )!!.defaultType,
                    emptyMap(),
                    SourceElement.NO_SOURCE
                )
            }
        return replaceAnnotations(Annotations.create(annotations + additionalAnnotations))
    }
}
