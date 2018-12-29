package com.ivianuu.injekt

typealias BeanDefinition<T> = (params: Parameters) -> T

typealias ComponentDefinition = Component.() -> Unit

typealias InjektConfiguration = InjektPlugins.() -> Unit

typealias ModuleDefinition = Module.() -> Unit

typealias ParamsDefinition = () -> Parameters