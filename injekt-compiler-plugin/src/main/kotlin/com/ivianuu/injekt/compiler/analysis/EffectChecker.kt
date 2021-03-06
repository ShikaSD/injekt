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

package com.ivianuu.injekt.compiler.analysis

import com.ivianuu.injekt.compiler.InjektErrors
import com.ivianuu.injekt.compiler.InjektFqNames
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class EffectChecker : DeclarationChecker {

    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (descriptor is ClassDescriptor) checkEffect(descriptor, declaration, context)
        if (descriptor is ClassDescriptor || descriptor is FunctionDescriptor)
            checkEffectUsage(descriptor, declaration, context)
    }

    private fun checkEffect(
        descriptor: ClassDescriptor,
        declaration: KtDeclaration,
        context: DeclarationCheckerContext
    ) {
        if (!descriptor.hasAnnotation(InjektFqNames.Effect)) return

        val companion = descriptor.companionObjectDescriptor
        if (companion == null) {
            context.trace.report(
                InjektErrors.EFFECT_WITHOUT_COMPANION
                    .on(declaration)
            )
            return
        }

        companion.unsubstitutedMemberScope
            .getContributedDescriptors()
            .filterIsInstance<FunctionDescriptor>()
            .filter { it.dispatchReceiverParameter?.value?.type == companion.defaultType }
            .forEach { effectFunction ->
                if (effectFunction.typeParameters.size != 1) {
                    context.trace.report(
                        InjektErrors.EFFECT_FUNCTION_NEEDS_ONE_TYPE_PARAMETER
                            .on(declaration)
                    )
                    return
                }

                if (effectFunction.valueParameters.isNotEmpty()) {
                    context.trace.report(
                        InjektErrors.EFFECT_FUNCTION_CANNOT_HAVE_VALUE_PARAMETERS
                            .on(declaration)
                    )
                    return
                }
            }
    }

    private fun checkEffectUsage(
        descriptor: DeclarationDescriptor,
        declaration: KtDeclaration,
        context: DeclarationCheckerContext
    ) {
        if (!descriptor.hasAnnotatedAnnotations(InjektFqNames.Effect, descriptor.module)) return

        if (descriptor is ClassDescriptor &&
            descriptor.hasAnnotatedAnnotations(
                InjektFqNames.Effect,
                descriptor.module
            ) && !descriptor.hasAnnotation(InjektFqNames.Given)
        ) {
            context.trace.report(
                InjektErrors.EFFECT_WITHOUT_GIVEN
                    .on(declaration)
            )
        }

        if ((descriptor is ClassDescriptor && descriptor.declaredTypeParameters.isNotEmpty()) ||
            (descriptor is FunctionDescriptor && descriptor.typeParameters.isNotEmpty())
        ) {
            context.trace.report(
                InjektErrors.EFFECT_WITH_TYPE_PARAMETERS
                    .on(declaration)
            )
        }

        val effectAnnotations = listOfNotNull(
            descriptor.getAnnotatedAnnotations(InjektFqNames.Effect, descriptor.module)
                .singleOrNull()
        )

        val upperBounds = effectAnnotations
            .mapNotNull {
                it.type
                    .constructor
                    .declarationDescriptor
                    .let { it as ClassDescriptor }
                    .companionObjectDescriptor
                    ?.unsubstitutedMemberScope
                    ?.getContributedDescriptors()
                    ?.filterIsInstance<FunctionDescriptor>()
                    ?.firstOrNull()
                    ?.typeParameters
                    ?.singleOrNull()
                    ?.upperBounds
                    ?.singleOrNull()
            }

        val declarationType = when (descriptor) {
            is ClassDescriptor -> descriptor.defaultType
            is FunctionDescriptor -> descriptor.getFunctionType()
            else -> error("Unexpected descriptor $descriptor")
        }

        upperBounds.forEach { upperBound ->
            if (!declarationType.isSubtypeOf(upperBound)) {
                context.trace.report(
                    InjektErrors.NOT_IN_EFFECT_BOUNDS
                        .on(declaration)
                )
            }
        }
    }

}
