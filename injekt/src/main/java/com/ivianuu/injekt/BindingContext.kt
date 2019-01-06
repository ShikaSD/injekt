package com.ivianuu.injekt

import kotlin.reflect.KClass

/**
 * Binding context
 */
data class BindingContext<T : Any>(
    val definition: BeanDefinition<T>,
    val moduleContext: ModuleContext
)

/**
 * Binds this [BeanDefinition] to [type]
 */
infix fun <T : Any> BindingContext<T>.bind(type: KClass<*>) = apply {
    val copy = (definition as BeanDefinition<Any>).copy(type = type as KClass<Any>, name = null)
    copy.kind = definition.kind
    copy.override = definition.override
    copy.createOnStart = definition.createOnStart
    copy.attributes = definition.attributes
    copy.definition = definition.definition
    copy.instance = definition.instance
    moduleContext.declare(copy)
}

/**
 * Binds this [BeanDefinition] to [types]
 */
infix fun <T : Any> BindingContext<T>.bind(types: Array<KClass<*>>) = apply {
    types.forEach { bind(it) }
}

/**
 * Binds this [BeanDefinition] to [types]
 */
infix fun <T : Any> BindingContext<T>.bind(types: Iterable<KClass<*>>) = apply {
    types.forEach { bind(it) }
}

/**
 * Binds this [BeanDefinition] to [name]
 */
infix fun <T : Any> BindingContext<T>.bind(name: String) = apply {
    val copy = (definition as BeanDefinition<Any>).copy(name = name)
    copy.kind = definition.kind
    copy.override = definition.override
    copy.createOnStart = definition.createOnStart
    copy.attributes = definition.attributes
    copy.definition = definition.definition
    copy.instance = definition.instance
    moduleContext.declare(copy)
}

/**
 * Binds this [BeanDefinition] to [types]
 */
infix fun <T : Any> BindingContext<T>.bind(types: Array<String>) = apply {
    types.forEach { bind(it) }
}

/**
 * Binds this [BeanDefinition] to [names]
 */
@JvmName("bindNames")
infix fun <T : Any> BindingContext<T>.bind(types: Iterable<String>) = apply {
    types.forEach { bind(it) }
}