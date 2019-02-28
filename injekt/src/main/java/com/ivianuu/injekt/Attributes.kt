/*
 * Copyright 2018 Manuel Wrage
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

package com.ivianuu.injekt

/**
 * Attributes for [Binding]s
 */
inline class Attributes constructor(private val _entries: MutableMap<String, Any?> = hashMapOf()) {

    /**
     * All entries
     */
    val entries: Map<String, Any?> get() = _entries

    /**
     * Whether or not contains a value for [key]
     */
    fun contains(key: String): Boolean = _entries.contains(key)

    /**
     * Sets the value for [key] to [value]
     */
    operator fun <T> set(key: String, value: T) {
        _entries[key] = value as Any
    }

    /**
     * Returns the value for [key]
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T? = _entries[key] as? T

}

/**
 * Returns the value for [key] if present or returns and sets [defaultValue]
 */
inline fun <T> Attributes.getOrSet(key: String, defaultValue: () -> T): T {
    val value = get<T>(key)

    if (value == null) {
        val def = defaultValue()
        set(key, def)
        return def
    }

    return value
}

/**
 * Returns the value for [key] if present or the [defaultValue]
 */
inline fun <T> Attributes.getOrDefault(key: String, defaultValue: () -> T): T =
    get<T>(key) ?: defaultValue()

/**
 * Returns new [Attributes] which contains all [pairs]
 */
fun attributesOf(vararg pairs: Pair<String, Any?>): Attributes = Attributes(
    mutableMapOf(*pairs)
)

/**
 * Returns new empty parameters
 */
fun attributesOf(): Attributes = Attributes(mutableMapOf())