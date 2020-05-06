package com.ivianuu.injekt.compiler.transform.factory

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektSymbols
import com.ivianuu.injekt.compiler.MapKey
import com.ivianuu.injekt.compiler.buildClass
import com.ivianuu.injekt.compiler.classOrFail
import com.ivianuu.injekt.compiler.ensureBound
import com.ivianuu.injekt.compiler.ensureQualifiers
import com.ivianuu.injekt.compiler.findPropertyGetter
import com.ivianuu.injekt.compiler.getAnnotatedAnnotations
import com.ivianuu.injekt.compiler.getQualifiers
import com.ivianuu.injekt.compiler.hasAnnotation
import com.ivianuu.injekt.compiler.transform.AbstractInjektTransformer
import com.ivianuu.injekt.compiler.transform.InjektDeclarationStore
import com.ivianuu.injekt.compiler.typeArguments
import com.ivianuu.injekt.compiler.withQualifiers
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
    private val injektTransformer: AbstractInjektTransformer,
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
                    .withQualifiers(symbols, listOf(InjektFqNames.ChildFactory))
                    .asKey()

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
                ?.getArgumentType(factoryImplementation.context.moduleDescriptor)
                ?.let {
                    factoryImplementation.context.symbolTable
                        .referenceClass(it.constructor.declarationDescriptor as ClassDescriptor)
                }
                ?.ensureBound(factoryImplementation.context.irProviders)
                ?.owner

        val childFactoryImplementation =
            FactoryImplementation(
                parent = factoryImplementation,
                irParent = factoryImplementation.clazz,
                name = members.nameForGroup("child"),
                superType = superType,
                moduleClass = moduleClass,
                context = factoryImplementation.context,
                symbols = factoryImplementation.symbols,
                factoryTransformer = factoryImplementation.factoryTransformer,
                declarationStore = factoryImplementation.declarationStore
            )

        members.addClass(childFactoryImplementation.clazz)

        val childFactory =
            DeclarationIrBuilder(injektTransformer.context, factoryImplementation.clazz.symbol)
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
                with(injektTransformer) {
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
                                irCall(childFactoryImplementation.moduleClass!!.constructors.single()).apply {
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
    private val injektTransformer: AbstractInjektTransformer,
    private val dependencyNode: DependencyNode,
    private val members: FactoryMembers,
    private val factoryImplementation: FactoryImplementation
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
                    it.dispatchReceiverParameter!!.type != injektTransformer.irBuiltIns.anyType
        }

    private val providersByDependency = mutableMapOf<IrFunction, IrClass>()

    private fun provider(dependencyFunction: IrFunction): IrClass =
        providersByDependency.getOrPut(dependencyFunction) {
            with(injektTransformer) {
                with(DeclarationIrBuilder(injektTransformer.context, dependencyFunction.symbol)) {
                    provider(
                        name = Name.identifier("dep_provider_${providersByDependency.size}"),
                        parameters = listOf(
                            AbstractInjektTransformer.ProviderParameter(
                                name = "dependency",
                                type = dependencyNode.dependency.defaultType.ensureQualifiers(
                                    symbols
                                ),
                                assisted = false
                            )
                        ),
                        returnType = dependencyFunction.returnType.ensureQualifiers(symbols),
                        createBody = { createFunction ->
                            irExprBody(
                                irCall(dependencyFunction).apply {
                                    dispatchReceiver =
                                        irGet(createFunction.valueParameters.single())
                                }
                            )
                        }
                    ).also { members.addClass(it) }
                }
            }
        }

    override fun invoke(requestedKey: Key): List<BindingNode> {
        return allDependencyFunctions
            .filter {
                it.returnType.ensureQualifiers(factoryImplementation.symbols)
                    .asKey() == requestedKey
            }
            .map { dependencyFunction ->
                val provider = provider(dependencyFunction)
                DependencyBindingNode(
                    key = requestedKey,
                    provider = provider,
                    requirementNode = dependencyNode,
                    owner = factoryImplementation
                )
            }
    }
}

class ModuleBindingResolver(
    private val moduleNode: ModuleNode,
    descriptor: IrClass,
    private val symbols: InjektSymbols,
    private val factoryImplementation: FactoryImplementation
) : BindingResolver {

    private val bindingFunctions = descriptor
        .declarations
        .filterIsInstance<IrFunction>()

    private val allBindings = bindingFunctions
        .filter { it.hasAnnotation(InjektFqNames.AstBinding) }
        .map { bindingFunction ->
            val bindingKey = bindingFunction.returnType.asKey()
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
                            key = propertyGetter.returnType.asKey(),
                            initializerAccessor = moduleNode.initializerAccessor.child(
                                propertyGetter
                            )
                        ),
                        owner = factoryImplementation
                    )
                }
                else -> {
                    if (bindingFunction.valueParameters.any { it.hasAnnotation(InjektFqNames.AstAssisted) }) {
                        val assistedValueParameters = bindingFunction.valueParameters
                            .filter { it.hasAnnotation(InjektFqNames.AstAssisted) }

                        val assistedFactoryType = symbols.getFunction(assistedValueParameters.size)
                            .typeWith(
                                assistedValueParameters
                                    .map { it.type } + bindingKey.type
                            ).withQualifiers(symbols, listOf(InjektFqNames.Provider))

                        val dependencies = bindingFunction.valueParameters
                            .filterNot { it.hasAnnotation(InjektFqNames.AstAssisted) }
                            .map { it.type.asKey() }
                            .map {
                                DependencyRequest(
                                    it
                                )
                            }

                        AssistedProvisionBindingNode(
                            key = Key(
                                assistedFactoryType
                            ),
                            dependencies = dependencies,
                            targetScope = null,
                            scoped = scoped,
                            module = moduleNode,
                            provider = provider!!,
                            owner = factoryImplementation
                        )
                    } else {
                        val dependencies = bindingFunction.valueParameters
                            .map { it.type.asKey() }
                            .map {
                                DependencyRequest(
                                    it
                                )
                            }

                        ProvisionBindingNode(
                            key = bindingKey,
                            dependencies = dependencies,
                            targetScope = null,
                            scoped = scoped,
                            module = moduleNode,
                            provider = provider!!,
                            owner = factoryImplementation
                        )
                    }
                }
            }
        }

    private val delegateBindings = bindingFunctions
        .filter { it.hasAnnotation(InjektFqNames.AstAlias) }
        .map { delegateFunction ->
            DelegateBindingNode(
                key = Key(delegateFunction.returnType),
                originalKey = Key(
                    delegateFunction.valueParameters.single().type
                ),
                owner = factoryImplementation
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
    private val factoryImplementation: FactoryImplementation
) : BindingResolver {
    override fun invoke(requestedKey: Key): List<BindingNode> {
        if (InjektFqNames.MembersInjector !in requestedKey.type.getQualifiers()) return emptyList()
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
    private val context: IrPluginContext,
    private val declarationStore: InjektDeclarationStore,
    private val symbols: InjektSymbols,
    private val factoryImplementation: FactoryImplementation
) : BindingResolver {
    override fun invoke(requestedKey: Key): List<BindingNode> {
        return if (requestedKey.type.isFunction() &&
            requestedKey.type.classOrFail != symbols.getFunction(0) &&
            InjektFqNames.Provider in requestedKey.type.getQualifiers()
        ) {
            val clazz = requestedKey.type.typeArguments.last().classOrFail.owner
            val scopeAnnotation = clazz.descriptor.getAnnotatedAnnotations(InjektFqNames.Scope)
                .singleOrNull() ?: return emptyList()
            val provider = declarationStore.getProvider(clazz)

            val targetScope = scopeAnnotation.fqName?.takeIf { it != InjektFqNames.Transient }

            val scoped = scopeAnnotation.fqName != InjektFqNames.Transient

            val dependencies = provider.constructors.single().valueParameters
                .filterNot { it.hasAnnotation(InjektFqNames.Assisted) }
                .map { it.type.asKey() }
                .map { DependencyRequest(it) }

            listOf(
                AssistedProvisionBindingNode(
                    key = requestedKey,
                    dependencies = dependencies,
                    targetScope = targetScope,
                    scoped = scoped,
                    module = null,
                    provider = provider,
                    owner = factoryImplementation
                )
            )
        } else {
            val clazz = requestedKey.type.classOrNull
                ?.ensureBound(context.irProviders)?.owner ?: return emptyList()
            val scopeAnnotation = clazz.descriptor.getAnnotatedAnnotations(InjektFqNames.Scope)
                .singleOrNull() ?: return emptyList()
            val provider = declarationStore.getProvider(clazz)

            val targetScope = scopeAnnotation.fqName?.takeIf { it != InjektFqNames.Transient }

            val scoped = scopeAnnotation.fqName != InjektFqNames.Transient

            val dependencies = provider.constructors.single().valueParameters
                .map { it.type.typeArguments.single().asKey() }
                .map { DependencyRequest(it) }

            listOf(
                ProvisionBindingNode(
                    key = requestedKey,
                    dependencies = dependencies,
                    targetScope = targetScope,
                    scoped = scoped,
                    module = null,
                    provider = provider,
                    owner = factoryImplementation
                )
            )
        }
    }
}

class MapBindingResolver(
    private val context: IrPluginContext,
    private val symbols: InjektSymbols,
    private val factoryImplementation: FactoryImplementation
) : BindingResolver {

    private val maps =
        mutableMapOf<Key, MutableMap<MapKey, DependencyRequest>>()

    override fun invoke(requestedKey: Key): List<BindingNode> {
        return maps
            .flatMap { (mapKey, entries) ->
                listOf(
                    MapBindingNode(
                        mapKey,
                        factoryImplementation,
                        entries
                    ),
                    frameworkBinding(InjektFqNames.Lazy, mapKey, entries),
                    frameworkBinding(InjektFqNames.Provider, mapKey, entries)
                )
            }
            .filter { it.key == requestedKey }
    }

    fun addMap(mapKey: Key) {
        maps.getOrPut(mapKey) { mutableMapOf() }
    }

    fun putMapEntry(
        mapKey: Key,
        entryKey: MapKey,
        entryValue: DependencyRequest
    ) {
        val map = maps[mapKey]!!
        if (entryKey in map) {
            error("Already bound value with $entryKey into map $mapKey")
        }

        map[entryKey] = entryValue
    }

    private fun frameworkBinding(
        qualifier: FqName,
        mapKey: Key,
        entries: Map<MapKey, DependencyRequest>
    ) = MapBindingNode(
        context.symbolTable.referenceClass(context.builtIns.map)
            .typeWith(
                mapKey.type.typeArguments[0],
                symbols.getFunction(0)
                    .typeWith(mapKey.type.typeArguments[1])
                    .withQualifiers(symbols, listOf(qualifier))
            )
            .ensureQualifiers(symbols)
            .asKey(),
        factoryImplementation,
        entries
            .mapValues {
                DependencyRequest(
                    key = symbols.getFunction(0)
                        .typeWith(it.value.key.type)
                        .withQualifiers(symbols, listOf(qualifier)).asKey()
                )
            }
    )
}


class SetBindingResolver(
    private val context: IrPluginContext,
    private val symbols: InjektSymbols,
    private val factoryImplementation: FactoryImplementation
) : BindingResolver {

    private val sets = mutableMapOf<Key, MutableSet<DependencyRequest>>()

    override fun invoke(requestedKey: Key): List<BindingNode> {
        return sets
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
        sets.getOrPut(setKey) { mutableSetOf() }
    }

    fun addSetElement(setKey: Key, element: DependencyRequest) {
        val set = sets[setKey]!!
        if (element in set) {
            error("Already bound $element into set $setKey")
        }

        set += element
    }

    private fun frameworkBinding(
        qualifier: FqName,
        setKey: Key,
        elements: Set<DependencyRequest>
    ) = SetBindingNode(
        Key(
            context.symbolTable.referenceClass(context.builtIns.set)
                .typeWith(
                    symbols.getFunction(0).typeWith(
                        setKey.type.typeArguments.single()
                    ).withQualifiers(symbols, listOf(qualifier))
                )
                .ensureQualifiers(symbols)
        ),
        factoryImplementation,
        elements
            .map {
                DependencyRequest(
                    key = Key(
                        symbols.getFunction(0).typeWith(
                            it.key.type
                        ).withQualifiers(symbols, listOf(qualifier))
                    )
                )
            }
    )
}

class LazyOrProviderBindingResolver(
    private val symbols: InjektSymbols,
    private val factoryImplementation: FactoryImplementation
) : BindingResolver {
    override fun invoke(requestedKey: Key): List<BindingNode> {
        val requestedType = requestedKey.type
        return when {
            requestedType.isFunction() &&
                    requestedKey.type.classOrFail == symbols.getFunction(0) &&
                    InjektFqNames.Lazy in requestedType.getQualifiers() ->
                listOf(
                    LazyBindingNode(
                        requestedKey,
                        factoryImplementation
                    )
                )
            requestedType.isFunction() &&
                    requestedKey.type.classOrFail == symbols.getFunction(0) &&
                    InjektFqNames.Provider in requestedType.getQualifiers() ->
                listOf(
                    ProviderBindingNode(
                        requestedKey,
                        factoryImplementation
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
        factoryImplementationNode.key.type.classOrFail
            .superTypes().single().asKey()

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
