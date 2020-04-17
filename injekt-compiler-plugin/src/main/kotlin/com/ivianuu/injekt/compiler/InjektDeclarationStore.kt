package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.fqNameForIrSerialization
import org.jetbrains.kotlin.name.FqName

class InjektDeclarationStore(
    private val pluginContext: IrPluginContext,
    private val moduleFragment: IrModuleFragment
) {

    lateinit var componentTransformer: ComponentTransformer
    lateinit var moduleTransformer: ModuleTransformer

    fun getComponent(key: String): IrClass {
        return try {
            componentTransformer.getProcessedComponent(key)
                ?: throw DeclarationNotFound("Couldn't find for $key")
        } catch (e: DeclarationNotFound) {
            try {
                moduleFragment.files
                    .flatMap { it.declarations }
                    .filterIsInstance<IrClass>()
                    .singleOrNull {
                        it.name.asString().startsWith("$key\$")
                    }
                    ?.let {
                        it.name.asString().removePrefix("$key\$")
                            .replace("_", ".")
                    }
                    ?.let { fqName ->
                        moduleFragment.files
                            .flatMap { it.declarations }
                            .filterIsInstance<IrClass>()
                            .singleOrNull {
                                it.fqNameForIrSerialization.asString() == fqName
                            }
                    }
                    ?: throw DeclarationNotFound("Decls ${moduleFragment.files.flatMap { it.declarations }
                        .map { it.descriptor.name }}")
            } catch (e: DeclarationNotFound) {
                try {
                    val componentPackage = pluginContext.moduleDescriptor
                        .getPackage(InjektClassNames.InjektComponentsPackage)

                    return componentPackage.memberScope
                        .getContributedDescriptors()
                        .filterIsInstance<ClassDescriptor>()
                        .singleOrNull { it.name.asString().startsWith("$key\$") }
                        ?.let {
                            it.name.asString().removePrefix("$key\$")
                                .replace("_", ".")
                        }
                        ?.let {
                            pluginContext.symbolTable.referenceClass(
                                pluginContext.moduleDescriptor.getTopLevelClass(FqName(it))
                            ).owner
                        }
                        ?: throw DeclarationNotFound("Found ${componentPackage.memberScope.getClassifierNames()}")
                } catch (e: DeclarationNotFound) {
                    throw DeclarationNotFound("Couldn't find component for $key")
                }
            }
        }
    }

    fun getModule(fqName: FqName): IrClass {
        return try {
            moduleTransformer.getProcessedModule(fqName)
                ?: throw DeclarationNotFound()
        } catch (e: DeclarationNotFound) {
            try {
                moduleFragment.files
                    .flatMap { it.declarations }
                    .filterIsInstance<IrClass>()
                    .firstOrNull { it.fqNameForIrSerialization == fqName }
                    ?: throw DeclarationNotFound()
            } catch (e: DeclarationNotFound) {
                try {
                    pluginContext.symbolTable.referenceClass(
                        pluginContext.moduleDescriptor.getTopLevelClass(fqName)
                    ).ensureBound(pluginContext.irProviders).owner
                } catch (e: DeclarationNotFound) {
                    throw DeclarationNotFound("Couldn't find module for $fqName")
                }
            }
        }
    }

}

class DeclarationNotFound(message: String? = null) : RuntimeException(message)