package com.ivianuu.injekt.compiler.transform.factory

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektSymbols
import com.ivianuu.injekt.compiler.MapKey
import com.ivianuu.injekt.compiler.buildClass
import com.ivianuu.injekt.compiler.classOrFail
import com.ivianuu.injekt.compiler.ensureBound
import com.ivianuu.injekt.compiler.findPropertyGetter
import com.ivianuu.injekt.compiler.getAnnotatedAnnotations
import com.ivianuu.injekt.compiler.getQualifierFqNames
import com.ivianuu.injekt.compiler.hasAnnotation
import com.ivianuu.injekt.compiler.substituteByName
import com.ivianuu.injekt.compiler.transform.InjektDeclarationIrBuilder
import com.ivianuu.injekt.compiler.transform.InjektDeclarationStore
import com.ivianuu.injekt.compiler.typeArguments
import com.ivianuu.injekt.compiler.withNoArgQualifiers
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.MetadataSource
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.superTypes
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.isFunction
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.KClassValue

typealias BindingResolver = (Key) -> List<BindingNode>

class ChildFactoryBindingResolver(
    private val factoryImplementation: FactoryImplementation,
    descriptor: IrClass,
    private val symbols: InjektSymbols,
    private val members: FactoryMembers
) : BindingResolver {

    private val childFactoryFunctions =
        mutableMapOf<Key, MutableList<Lazy<ChildFactoryBindingNode>>>()

    init {
        descriptor
            .functions
            .filter { it.hasAnnotation(InjektFqNames.AstChildFactory) }
            .forEach { function ->
                val key = symbols.getFunction(function.valueParameters.size)
                    .typeWith(function.valueParameters.map { it.type } + function.returnType)
                    .withNoArgQualifiers(symbols, listOf(InjektFqNames.ChildFactory))
                    .asKey(factoryImplementation.pluginContext)

                childFactoryFunctions.getOrPut(key) { mutableListOf() } += childFactoryBindingNode(
                    key, function
                )
            }
    }

    override fun invoke(requestedKey: Key): List<BindingNode> =
        childFactoryFunctions[requestedKey]?.map { it.value } ?: emptyList()

    private fun childFactoryBindingNode(
        key: Key,
        function: IrFunction
    ) = lazy {
        val superType = function.returnType

        val moduleClass = function.getAnnotation(InjektFqNames.AstClassPath)
            ?.getValueArgument(0)
            ?.let { it as IrClassReferenceImpl }
            ?.classType
            ?.classOrFail
            ?.owner
            ?: function.descriptor.annotations.findAnnotation(InjektFqNames.AstClassPath)
                ?.allValueArguments
                ?.get(Name.identifier("clazz"))
                ?.let { it as KClassValue }
                ?.getArgumentType(factoryImplementation.pluginContext.moduleDescriptor)
                ?.let {
                    factoryImplementation.pluginContext.symbolTable
                        .referenceClass(it.constructor.declarationDescriptor as ClassDescriptor)
                }
                ?.ensureBound(factoryImplementation.pluginContext.irProviders)
                ?.owner
        moduleClass!!

        val childFactoryImplementation =
            FactoryImplementation(
                factoryFunction = null,
                parent = factoryImplementation,
                irParent = factoryImplementation.clazz,
                name = members.nameForGroup("child"),
                superType = superType,
                moduleClass = moduleClass,
                pluginContext = factoryImplementation.pluginContext,
                symbols = factoryImplementation.symbols,
                factoryTransformer = factoryImplementation.factoryTransformer,
                declarationStore = factoryImplementation.declarationStore
            )

        members.addClass(childFactoryImplementation.clazz)

        val childFactory =
            DeclarationIrBuilder(
                factoryImplementation.pluginContext,
                factoryImplementation.clazz.symbol
            )
                .childFactory(
                    members.nameForGroup("child_factory"),
                    childFactoryImplementation,
                    key.type
                )

        members.addClass(childFactory)

        return@lazy ChildFactoryBindingNode(
            key,
            factoryImplementation,
            childFactoryImplementation,
            childFactory
        )
    }

    private fun IrBuilderWithScope.childFactory(
        name: Name,
        childFactoryImplementation: FactoryImplementation,
        superType: IrType
    ) = buildClass {
        this.name = name
        visibility = Visibilities.PRIVATE
    }.apply clazz@{
        parent = factoryImplementation.clazz
        createImplicitParameterDeclarationWithWrappedDescriptor()
        (this as IrClassImpl).metadata = MetadataSource.Class(descriptor)
        superTypes += superType

        val parentField = addField(
            "parent",
            factoryImplementation.clazz.defaultType
        )

        addConstructor {
            returnType = defaultType
            isPrimary = true
            visibility = Visibilities.PUBLIC
        }.apply {
            val parentValueParameter = addValueParameter(
                name = "parent",
                type = factoryImplementation.clazz.defaultType
            )

            body = irBlockBody {
                with(InjektDeclarationIrBuilder(factoryImplementation.pluginContext, symbol)) {
                    initializeClassWithAnySuperClass(this@clazz.symbol)
                }
                +irSetField(
                    irGet(thisReceiver!!),
                    parentField,
                    irGet(parentValueParameter)
                )
            }
        }

        addFunction {
            this.name = Name.identifier("invoke")
            returnType = childFactoryImplementation.clazz.defaultType
        }.apply {
            dispatchReceiverParameter = thisReceiver!!.copyTo(this)

            overriddenSymbols += superTypes.single()
                .classOrFail
                .functions
                .single { it.owner.name.asString() == "invoke" }

            superType.typeArguments.dropLast(1).forEachIndexed { index, type ->
                addValueParameter(
                    "p$index",
                    type
                )
            }

            body = irBlockBody {
                +DeclarationIrBuilder(context, symbol).irReturn(
                    irCall(childFactoryImplementation.constructor).apply {
                        putValueArgument(
                            0,
                            irGetField(
                                irGet(dispatchReceiverParameter!!),
                                parentField
                            )
                        )

                        if (childFactoryImplementation.moduleConstructorValueParameter.isInitialized()) {
                            putValueArgument(
                                1,
                                irCall(childFactoryImplementation.moduleClass.constructors.single()).apply {
                                    valueParameters.forEachIndexed { index, parameter ->
                                        putValueArgument(
                                            index,
                                            irGet(parameter)
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

class DependencyBindingResolver(
    private val dependencyNode: DependencyNode,
    private val members: FactoryMembers,
    private val factoryProduct: AbstractFactoryProduct
) : BindingResolver {

    private val allDependencyFunctions = dependencyNode.dependency
        .declarations
        .mapNotNull { declaration ->
            when (declaration) {
                is IrFunction -> declaration
                is IrProperty -> declaration.getter
                else -> null
            }
        }
        .filter {
            it.valueParameters.isEmpty()
                    && !it.isFakeOverride &&
                    it.dispatchReceiverParameter!!.type != factoryProduct.pluginContext.irBuiltIns.anyType
        }

    private val providersByDependency = mutableMapOf<IrFunction, IrClass>()

    private fun provider(dependencyFunction: IrFunction): IrClass =
        providersByDependency.getOrPut(dependencyFunction) {
            with(
                InjektDeclarationIrBuilder(
                    factoryProduct.pluginContext,
                    dependencyFunction.symbol
                )
            ) {
                provider(
                    name = Name.identifier("dep_provider_${providersByDependency.size}"),
                    visibility = Visibilities.PRIVATE,
                    parameters = listOf(
                        InjektDeclarationIrBuilder.ProviderParameter(
                            name = "dependency",
                            type = dependencyNode.dependency.defaultType,
                            assisted = false
                        )
                    ),
                    returnType = dependencyFunction.returnType,
                    createBody = { createFunction ->
                        builder.irExprBody(
                            irCall(dependencyFunction).apply {
                                dispatchReceiver =
                                    irGet(createFunction.valueParameters.single())
                            }
                        )
                    }
                ).also { members.addClass(it) }
            }
        }

    override fun invoke(requestedKey: Key): List<BindingNode> {
        return allDependencyFunctions
            .filter { it.returnType.asKey(factoryProduct.pluginContext) == requestedKey }
            .map { dependencyFunction ->
                val provider = provider(dependencyFunction)
                DependencyBindingNode(
                    key = requestedKey,
                    provider = provider,
                    requirementNode = dependencyNode,
                    owner = factoryProduct
                )
            }
    }
}

class ModuleBindingResolver(
    private val moduleNode: ModuleNode,
    descriptor: IrClass,
    private val symbols: InjektSymbols,
    private val factoryProduct: AbstractFactoryProduct
) : BindingResolver {

    private val bindingFunctions = descriptor
        .declarations
        .filterIsInstance<IrFunction>()

    private val allBindings = bindingFunctions
        .filter { it.hasAnnotation(InjektFqNames.AstBinding) }
        .filterNot { it.hasAnnotation(InjektFqNames.AstInline) }
        .map { bindingFunction ->
            val bindingKey = bindingFunction.returnType
                .substituteByName(moduleNode.typeParametersMap)
                .asKey(factoryProduct.pluginContext)
            val propertyName = bindingFunction.getAnnotation(InjektFqNames.AstPropertyPath)
                ?.getValueArgument(0)?.let { it as IrConst<String> }?.value
            val provider = bindingFunction.getAnnotation(InjektFqNames.AstClassPath)
                ?.getValueArgument(0)
                ?.let { it as IrClassReferenceImpl }
                ?.classType
                ?.classOrFail
                ?.owner
                ?: bindingFunction.descriptor.annotations.findAnnotation(InjektFqNames.AstClassPath)
                    ?.allValueArguments
                    ?.get(Name.identifier("clazz"))
                    ?.let { it as KClassValue }
                    ?.let { it.value as KClassValue.Value.NormalClass }
                    ?.classId
                    ?.shortClassName
                    ?.asString()
                    ?.substringAfter("\$")
                    ?.let { name ->
                        moduleNode.module.declarations
                            .filterIsInstance<IrClass>()
                            .single { it.name.asString() == name }
                    }

            val scoped = bindingFunction.hasAnnotation(InjektFqNames.AstScoped)

            when {
                propertyName != null -> {
                    val propertyGetter =
                        moduleNode.module.findPropertyGetter(propertyName)
                    InstanceBindingNode(
                        key = bindingKey,
                        requirementNode = InstanceNode(
                            key = propertyGetter.returnType
                                .substituteByName(moduleNode.typeParametersMap)
                                .asKey(factoryProduct.pluginContext),
                            initializerAccessor = moduleNode.initializerAccessor.child(
                                propertyGetter
                            )
                        ),
                        owner = factoryProduct
                    )
                }
                else -> {
                    if (bindingFunction.valueParameters.any {
                            it.descriptor.annotations.hasAnnotation(
                                InjektFqNames.AstAssisted
                            )
                        }) {
                        val assistedValueParameters = bindingFunction.valueParameters
                            .filter { it.descriptor.annotations.hasAnnotation(InjektFqNames.AstAssisted) }

                        val assistedFactoryType = symbols.getFunction(assistedValueParameters.size)
                            .typeWith(
                                assistedValueParameters
                                    .map {
                                        it.type
                                            .substituteByName(moduleNode.typeParametersMap)
                                    } + bindingKey.type
                            ).withNoArgQualifiers(symbols, listOf(InjektFqNames.Provider))

                        val dependencies = bindingFunction.valueParameters
                            .filterNot { it.descriptor.annotations.hasAnnotation(InjektFqNames.AstAssisted) }
                            .map {
                                it.type
                                    .substituteByName(moduleNode.typeParametersMap)
                                    .asKey(factoryProduct.pluginContext)
                            }
                            .map { BindingRequest(it) }

                        AssistedProvisionBindingNode(
                            key = assistedFactoryType.asKey(factoryProduct.pluginContext),
                            dependencies = dependencies,
                            targetScope = null,
                            scoped = scoped,
                            module = moduleNode,
                            provider = provider!!,
                            owner = factoryProduct
                        )
                    } else {
                        val dependencies = bindingFunction.valueParameters
                            .map {
                                it.type
                                    .substituteByName(moduleNode.typeParametersMap)
                                    .asKey(factoryProduct.pluginContext)
                            }
                            .map { BindingRequest(it) }

                        ProvisionBindingNode(
                            key = bindingKey,
                            dependencies = dependencies,
                            targetScope = null,
                            scoped = scoped,
                            module = moduleNode,
                            provider = provider!!,
                            owner = factoryProduct
                        )
                    }
                }
            }
        }

    private val delegateBindings = bindingFunctions
        .filter { it.hasAnnotation(InjektFqNames.AstAlias) }
        .map { delegateFunction ->
            DelegateBindingNode(
                key = delegateFunction.returnType.asKey(factoryProduct.pluginContext),
                originalKey = delegateFunction.valueParameters.single().type
                    .asKey(factoryProduct.pluginContext),
                owner = factoryProduct
            )
        }

    override fun invoke(requestedKey: Key): List<BindingNode> {
        return (allBindings + delegateBindings)
            .filter { it.key == requestedKey }
    }
}

class MembersInjectorBindingResolver(
    private val symbols: InjektSymbols,
    private val declarationStore: InjektDeclarationStore,
    private val factoryImplementation: AbstractFactoryProduct
) : BindingResolver {
    override fun invoke(requestedKey: Key): List<BindingNode> {
        if (InjektFqNames.MembersInjector !in requestedKey.type.getQualifierFqNames()) return emptyList()
        if (requestedKey.type.classOrNull != symbols.getFunction(1)) return emptyList()
        val target = requestedKey.type.typeArguments.first().classOrFail.owner
        val membersInjector = declarationStore.getMembersInjector(target)
        return listOf(
            MembersInjectorBindingNode(
                key = requestedKey,
                membersInjector = membersInjector,
                owner = factoryImplementation
            )
        )
    }
}

class AnnotatedClassBindingResolver(
    private val pluginContext: IrPluginContext,
    private val declarationStore: InjektDeclarationStore,
    private val symbols: InjektSymbols,
    private val factoryProduct: AbstractFactoryProduct
) : BindingResolver {
    override fun invoke(requestedKey: Key): List<BindingNode> {

        return if (requestedKey.type.isFunction() &&
            requestedKey.type.classOrFail != symbols.getFunction(0) &&
            InjektFqNames.Provider in requestedKey.type.getQualifierFqNames()
        ) {
            val clazz = requestedKey.type.typeArguments.last().classOrFail.owner
            val scopeAnnotation = clazz.descriptor.getAnnotatedAnnotations(InjektFqNames.Scope)
                .singleOrNull() ?: return emptyList()
            val provider = declarationStore.getProvider(clazz)

            val targetScope = scopeAnnotation.fqName?.takeIf { it != InjektFqNames.Transient }

            val scoped = scopeAnnotation.fqName != InjektFqNames.Transient

            val dependencies = provider.constructors.single().valueParameters
                .filterNot { it.descriptor.annotations.hasAnnotation(InjektFqNames.Assisted) }
                .map { it.type.asKey(pluginContext) }
                .map { BindingRequest(it) }

            listOf(
                AssistedProvisionBindingNode(
                    key = requestedKey,
                    dependencies = dependencies,
                    targetScope = targetScope,
                    scoped = scoped,
                    module = null,
                    provider = provider,
                    owner = factoryProduct
                )
            )
        } else {
            val clazz = requestedKey.type.classOrNull
                ?.ensureBound(pluginContext.irProviders)?.owner ?: return emptyList()
            val scopeAnnotation = clazz.descriptor.getAnnotatedAnnotations(InjektFqNames.Scope)
                .singleOrNull() ?: return emptyList()
            val provider = declarationStore.getProvider(clazz)

            val targetScope = scopeAnnotation.fqName?.takeIf { it != InjektFqNames.Transient }

            val scoped = scopeAnnotation.fqName != InjektFqNames.Transient

            val dependencies = provider.constructors
                .singleOrNull()
                ?.valueParameters
                ?.map { it.type.typeArguments.single().asKey(pluginContext) }
                ?.map { BindingRequest(it) } ?: emptyList()

            listOf(
                ProvisionBindingNode(
                    key = requestedKey,
                    dependencies = dependencies,
                    targetScope = targetScope,
                    scoped = scoped,
                    module = null,
                    provider = provider,
                    owner = factoryProduct
                )
            )
        }
    }
}

class MapBindingResolver(
    private val pluginContext: IrPluginContext,
    private val symbols: InjektSymbols,
    private val factoryProduct: AbstractFactoryProduct,
    private val parent: MapBindingResolver?
) : BindingResolver {

    private val mapBuilders =
        mutableMapOf<Key, MutableMap<MapKey, BindingRequest>>()
    private val finalMaps: Map<Key, Map<MapKey, BindingRequest>> by lazy {
        val mergedMaps: MutableMap<Key, MutableMap<MapKey, BindingRequest>> = parent?.finalMaps
            ?.mapValues { it.value.toMutableMap() }
            ?.toMutableMap() ?: mutableMapOf()
        mapBuilders.forEach { (mapKey, mapBuilder) ->
            val mergedMap = mergedMaps.getOrPut(mapKey) { mutableMapOf() }
            mapBuilder.forEach { (entryKey, entryValue) ->
                if (entryKey in mergedMap) {
                    error("Already bound value with $entryKey into map $mapKey")
                }

                mergedMap[entryKey] = entryValue
            }
        }
        mergedMaps
    }

    override fun invoke(requestedKey: Key): List<BindingNode> {
        return finalMaps
            .flatMap { (mapKey, entries) ->
                listOf(
                    MapBindingNode(
                        mapKey,
                        factoryProduct,
                        entries
                    ),
                    frameworkBinding(InjektFqNames.Lazy, mapKey, entries),
                    frameworkBinding(InjektFqNames.Provider, mapKey, entries)
                )
            }
            .filter { it.key == requestedKey }
    }

    fun addMap(mapKey: Key) {
        mapBuilders.getOrPut(mapKey) { mutableMapOf() }
    }

    fun putMapEntry(
        mapKey: Key,
        entryKey: MapKey,
        entryValue: BindingRequest
    ) {
        val map = mapBuilders[mapKey]!!
        if (entryKey in map) {
            error("Already bound value with $entryKey into map $mapKey")
        }

        map[entryKey] = entryValue
    }

    private fun frameworkBinding(
        qualifier: FqName,
        mapKey: Key,
        entries: Map<MapKey, BindingRequest>
    ) = MapBindingNode(
        pluginContext.symbolTable.referenceClass(pluginContext.builtIns.map)
            .typeWith(
                mapKey.type.typeArguments[0],
                symbols.getFunction(0)
                    .typeWith(mapKey.type.typeArguments[1])
                    .withNoArgQualifiers(symbols, listOf(qualifier))
            )
            .asKey(factoryProduct.pluginContext),
        factoryProduct,
        entries
            .mapValues {
                BindingRequest(
                    key = symbols.getFunction(0)
                        .typeWith(it.value.key.type)
                        .withNoArgQualifiers(symbols, listOf(qualifier))
                        .asKey(factoryProduct.pluginContext)
                )
            }
    )
}


class SetBindingResolver(
    private val pluginContext: IrPluginContext,
    private val symbols: InjektSymbols,
    private val factoryImplementation: AbstractFactoryProduct,
    private val parent: SetBindingResolver?
) : BindingResolver {

    private val setBuilders = mutableMapOf<Key, MutableSet<BindingRequest>>()
    private val finalSets: Map<Key, Set<BindingRequest>> by lazy {
        val mergedSets: MutableMap<Key, MutableSet<BindingRequest>> = parent?.finalSets
            ?.mapValues { it.value.toMutableSet() }
            ?.toMutableMap() ?: mutableMapOf()
        setBuilders.forEach { (setKey, setBuilder) ->
            val mergedSet = mergedSets.getOrPut(setKey) { mutableSetOf() }
            setBuilder.forEach { element ->
                if (element in mergedSet) {
                    error("Already bound $element into set $setKey")
                }

                mergedSet += element
            }
        }
        mergedSets
    }

    override fun invoke(requestedKey: Key): List<BindingNode> {
        return finalSets
            .flatMap { (setKey, elements) ->
                listOf(
                    SetBindingNode(
                        setKey,
                        factoryImplementation,
                        elements.toList()
                    ),
                    frameworkBinding(InjektFqNames.Lazy, setKey, elements),
                    frameworkBinding(InjektFqNames.Provider, setKey, elements)
                )
            }
            .filter { it.key == requestedKey }
    }

    fun addSet(setKey: Key) {
        setBuilders.getOrPut(setKey) { mutableSetOf() }
    }

    fun addSetElement(setKey: Key, element: BindingRequest) {
        val set = setBuilders[setKey]!!
        if (element in set) {
            error("Already bound $element into set $setKey")
        }

        set += element
    }

    private fun frameworkBinding(
        qualifier: FqName,
        setKey: Key,
        elements: Set<BindingRequest>
    ) = SetBindingNode(
        pluginContext.symbolTable.referenceClass(pluginContext.builtIns.set)
            .typeWith(
                symbols.getFunction(0).typeWith(
                    setKey.type.typeArguments.single()
                ).withNoArgQualifiers(symbols, listOf(qualifier))
            ).asKey(factoryImplementation.pluginContext),
        factoryImplementation,
        elements
            .map {
                BindingRequest(
                    key = symbols.getFunction(0).typeWith(
                            it.key.type
                        ).withNoArgQualifiers(symbols, listOf(qualifier))
                        .asKey(factoryImplementation.pluginContext)
                )
            }
    )
}

class LazyOrProviderBindingResolver(
    private val symbols: InjektSymbols,
    private val factoryProduct: AbstractFactoryProduct
) : BindingResolver {
    override fun invoke(requestedKey: Key): List<BindingNode> {
        val requestedType = requestedKey.type
        return when {
            requestedType.isFunction() &&
                    requestedKey.type.classOrFail == symbols.getFunction(0) &&
                    InjektFqNames.Lazy in requestedType.getQualifierFqNames() ->
                listOf(
                    LazyBindingNode(
                        requestedKey,
                        factoryProduct
                    )
                )
            requestedType.isFunction() &&
                    requestedKey.type.classOrFail == symbols.getFunction(0) &&
                    InjektFqNames.Provider in requestedType.getQualifierFqNames() ->
                listOf(
                    ProviderBindingNode(
                        requestedKey,
                        factoryProduct
                    )
                )
            else -> emptyList()
        }
    }
}

class FactoryImplementationBindingResolver(
    private val factoryImplementationNode: FactoryImplementationNode
) : BindingResolver {
    private val factorySuperClassKey =
        factoryImplementationNode.key.type.classOrFail.superTypes().single()
            .asKey(factoryImplementationNode.factoryImplementation.pluginContext)

    override fun invoke(requestedKey: Key): List<BindingNode> {
        if (requestedKey != factorySuperClassKey &&
            requestedKey != factoryImplementationNode.key
        ) return emptyList()
        return listOf(
            FactoryImplementationBindingNode(
                factoryImplementationNode
            )
        )
    }
}