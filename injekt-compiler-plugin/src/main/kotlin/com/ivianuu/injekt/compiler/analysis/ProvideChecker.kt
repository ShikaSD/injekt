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
import com.ivianuu.injekt.compiler.hasAnnotation
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext

class ProvideChecker : DeclarationChecker {
    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (descriptor !is ClassDescriptor) return

        val classHasAnnotation = descriptor.hasAnnotation(InjektFqNames.Unscoped) ||
                descriptor.hasAnnotation(InjektFqNames.Scoped)

        val annotatedConstructors = descriptor.constructors
            .filter {
                it.hasAnnotation(InjektFqNames.Unscoped) ||
                        it.hasAnnotation(InjektFqNames.Scoped)
            }

        if (!classHasAnnotation && annotatedConstructors.isEmpty()) return

        checkAnnotations(declaration, descriptor, context)
        descriptor.constructors.forEach { checkAnnotations(declaration, it, context) }

        if (classHasAnnotation && descriptor.constructors.size > 1 &&
            annotatedConstructors.isEmpty()
        ) {
            context.trace.report(
                InjektErrors.MULTIPLE_CONSTRUCTORS
                    .on(declaration)
            )
        }

        if (classHasAnnotation && annotatedConstructors.isNotEmpty()) {
            context.trace.report(
                InjektErrors.EITHER_CLASS_OR_CONSTRUCTOR
                    .on(declaration)
            )
        }

        if (annotatedConstructors.size > 1) {
            context.trace.report(
                InjektErrors.MULTIPLE_CONSTRUCTORS_ANNOTATED
                    .on(declaration)
            )
        }

        if ((descriptor.kind != ClassKind.CLASS && descriptor.kind != ClassKind.OBJECT) ||
            descriptor.modality == Modality.ABSTRACT
        ) {
            context.trace.report(InjektErrors.ANNOTATED_BINDING_CANNOT_BE_ABSTRACT.on(declaration))
        }
    }

    private fun checkAnnotations(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (!descriptor.hasAnnotation(InjektFqNames.Unscoped) &&
            !descriptor.hasAnnotation(InjektFqNames.Scoped)
        ) return

        if (descriptor.hasAnnotation(InjektFqNames.Unscoped) &&
            descriptor.hasAnnotation(InjektFqNames.Scoped)
        ) {
            context.trace.report(
                InjektErrors.UNSCOPED_WITH_SCOPED
                    .on(declaration)
            )
        }
    }

}