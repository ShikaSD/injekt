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

package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptorWithTypeParameters
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorVisitor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.ir.builders.declarations.IrClassBuilder
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.descriptors.LazyTypeConstructor
import org.jetbrains.kotlin.ir.descriptors.WrappedDeclarationDescriptor
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.TypeSubstitution
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.utils.Printer

fun IrClassBuilder.buildClass(): IrClass {
    val wrappedDescriptor = WrappedClassDescriptor()
    return IrClassImpl(
        startOffset, endOffset, origin,
        IrClassSymbolImpl(wrappedDescriptor),
        name, kind, visibility, modality,
        isCompanion = isCompanion, isInner = isInner, isData = isData, isExternal = isExternal,
        isInline = isInline, isExpect = isExpect, isFun = isFun
    ).also {
        wrappedDescriptor.bind(it)
    }
}

inline fun buildClass(builder: IrClassBuilder.() -> Unit) =
    IrClassBuilder().run {
        builder()
        buildClass()
    }

open class WrappedClassDescriptor(
    annotations: Annotations = Annotations.EMPTY,
    private val sourceElement: SourceElement = SourceElement.NO_SOURCE
) : ClassDescriptor, WrappedDeclarationDescriptor<IrClass>(annotations) {
    override fun getName() = owner.name

    override fun getMemberScope(typeArguments: MutableList<out TypeProjection>) = MemberScope.Empty

    override fun getMemberScope(typeSubstitution: TypeSubstitution) = MemberScope.Empty

    private val unsubstitutedMemberScope = object : MemberScopeImpl() {
        override fun printScopeStructure(p: Printer) {
        }

        override fun getContributedDescriptors(
            kindFilter: DescriptorKindFilter,
            nameFilter: (Name) -> Boolean
        ): Collection<DeclarationDescriptor> {
            return owner.declarations
                .filterIsInstance<IrFunction>()
                .filter { it.name != Name.special("<clinit>") }
                .map { it.descriptor }
        }
    }

    override fun getUnsubstitutedMemberScope() = unsubstitutedMemberScope

    private val innerClassesScope = object : MemberScopeImpl() {
        override fun printScopeStructure(p: Printer) {
        }

        override fun getContributedDescriptors(
            kindFilter: DescriptorKindFilter,
            nameFilter: (Name) -> Boolean
        ): Collection<DeclarationDescriptor> {
            return owner.declarations
                .filterIsInstance<IrClass>()
                .map { it.descriptor }
        }
    }

    override fun getUnsubstitutedInnerClassesScope() = innerClassesScope

    override fun getStaticScope() = MemberScope.Empty

    override fun getSource() = sourceElement

    override fun getConstructors() =
        owner.declarations.asSequence().filterIsInstance<IrConstructor>().map { it.descriptor }
            .toList()

    override fun getContainingDeclaration() = (owner.parent as IrSymbolOwner).symbol.descriptor

    private val _defaultType: SimpleType by lazy {
        TypeUtils.makeUnsubstitutedType(
            this,
            unsubstitutedMemberScope,
            KotlinTypeFactory.EMPTY_REFINED_TYPE_FACTORY
        )
    }

    override fun getDefaultType(): SimpleType = _defaultType

    override fun getKind() = owner.kind

    override fun getModality() = owner.modality

    override fun getCompanionObjectDescriptor() =
        owner.declarations.filterIsInstance<IrClass>().firstOrNull { it.isCompanion }?.descriptor

    override fun getVisibility() = owner.visibility

    override fun isCompanionObject() = owner.isCompanion

    override fun isData() = owner.isData

    override fun isInline() = owner.isInline

    override fun isFun() = owner.isFun

    override fun getThisAsReceiverParameter() =
        owner.thisReceiver?.descriptor as ReceiverParameterDescriptor

    override fun getUnsubstitutedPrimaryConstructor() =
        owner.declarations.filterIsInstance<IrConstructor>()
            .singleOrNull { it.isPrimary }?.descriptor

    override fun getDeclaredTypeParameters() = owner.typeParameters.map { it.descriptor }

    override fun getSealedSubclasses(): Collection<ClassDescriptor> = emptyList()

    override fun getOriginal() = this

    override fun isExpect() = false

    override fun substitute(substitutor: TypeSubstitutor): ClassifierDescriptorWithTypeParameters =
        throw UnsupportedOperationException("Wrapped descriptors SHOULD NOT be substituted")

    override fun isActual() = false

    private val _typeConstructor: TypeConstructor by lazy {
        LazyTypeConstructor(
            this,
            { declaredTypeParameters },
            { owner.superTypes.map { it.toKotlinType() } },
            LockBasedStorageManager.NO_LOCKS
        )
    }

    override fun getTypeConstructor(): TypeConstructor = _typeConstructor

    override fun isInner() = owner.isInner

    override fun isExternal() = owner.isExternal

    override fun <R : Any?, D : Any?> accept(
        visitor: DeclarationDescriptorVisitor<R, D>?,
        data: D
    ): R =
        visitor!!.visitClassDescriptor(this, data)

    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) {
        visitor!!.visitClassDescriptor(this, null)
    }

    override fun getDefaultFunctionTypeForSamInterface(): SimpleType? = null

    override fun isDefinitelyNotSamInterface(): Boolean {
        return owner.descriptor.isDefinitelyNotSamInterface
    }
}
