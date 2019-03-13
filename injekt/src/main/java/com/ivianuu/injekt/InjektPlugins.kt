package com.ivianuu.injekt

/**
 * Global configurations
 */
object InjektPlugins

/**
 * Defines inject configuration
 */
typealias InjektDefinition = InjektPlugins.() -> Unit

/**
 * Configure injekt
 */
inline fun configureInjekt(definition: InjektDefinition) {
    InjektPlugins.apply(definition)
}

private var _factoryFinder: FactoryFinder = DefaultFactoryFinder()
/**
 * The factory finder
 */
var InjektPlugins.factoryFinder: FactoryFinder
    get() = _factoryFinder
    set(value) {
        _factoryFinder = value
    }

private var _logger: Logger? = null
/**
 * The logger to use
 */
var InjektPlugins.logger: Logger?
    get() = _logger
    set(value) {
        _logger = value
    }

private val registeredComponentExtensions =
    linkedSetOf<ComponentExtension>()

internal fun InjektPlugins.getRegisteredComponentExtensions(): Set<ComponentExtension> =
    registeredComponentExtensions.toSet()

fun InjektPlugins.isComponentExtensionRegistered(extension: ComponentExtension): Boolean =
    registeredComponentExtensions.contains(extension)

fun InjektPlugins.registerComponentExtension(extension: ComponentExtension) {
    registeredComponentExtensions.add(extension)
}