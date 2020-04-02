package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.SingleRootFileViewProvider
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.MetadataSource
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.PsiSourceManager
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import java.io.File

class AggregateGenerator(
    private val moduleFragment: IrModuleFragment,
    private val pluginContext: IrPluginContext,
    private val project: Project,
    private val contributors: List<IrClass>
) {

    fun generate() {
        val psiSourceManager = pluginContext.psiSourceManager as PsiSourceManager

        contributors.forEach { contributor ->
            val className =
                Name.identifier(contributor.descriptor.fqNameSafe.asString().replace(".", "_"))

            val sourceFile = File("$className.kt")

            val virtualFile = CoreLocalVirtualFile(CoreLocalFileSystem(), sourceFile)

            val ktFile = KtFile(
                SingleRootFileViewProvider(
                    PsiManager.getInstance(project),
                    virtualFile
                ),
                false
            )

            val memberScope = MutableMemberScope()

            val packageFragmentDescriptor =
                object : PackageFragmentDescriptorImpl(
                    moduleFragment.descriptor,
                    FqName("com.ivianuu.injekt.aggregate")
                ) {
                    override fun getMemberScope(): MemberScope = memberScope
                }

            val fileEntry = psiSourceManager.getOrCreateFileEntry(ktFile)
            val file = IrFileImpl(
                fileEntry,
                packageFragmentDescriptor
            )
            psiSourceManager.putFileEntry(file, fileEntry)

            moduleFragment.files += file

            val classDescriptor = ClassDescriptorImpl(
                packageFragmentDescriptor,
                className,
                Modality.FINAL,
                ClassKind.CLASS,
                emptyList(),
                SourceElement.NO_SOURCE,
                false,
                LockBasedStorageManager.NO_LOCKS
            ).apply {
                initialize(
                    MemberScope.Empty,
                    emptySet(),
                    null
                )
            }

            memberScope.classDescriptors += classDescriptor

            file.addChild(
                IrClassImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    InjektOrigin,
                    IrClassSymbolImpl(classDescriptor)
                ).apply clazz@{
                    createImplicitParameterDeclarationWithWrappedDescriptor()

                    metadata = MetadataSource.Class(classDescriptor)

                    addConstructor {
                        origin = InjektOrigin
                        isPrimary = true
                        visibility = Visibilities.PUBLIC
                    }.apply {
                        body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                            +IrDelegatingConstructorCallImpl(
                                startOffset, endOffset,
                                context.irBuiltIns.unitType,
                                pluginContext.symbolTable.referenceConstructor(
                                    context.builtIns.any.unsubstitutedPrimaryConstructor!!
                                )
                            )
                            +IrInstanceInitializerCallImpl(
                                startOffset,
                                endOffset,
                                this@clazz.symbol,
                                context.irBuiltIns.unitType
                            )
                        }
                    }
                }
            )
        }
    }

}
