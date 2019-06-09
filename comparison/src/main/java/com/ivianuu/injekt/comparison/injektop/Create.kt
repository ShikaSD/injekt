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

package com.ivianuu.injekt.comparison.injektop

fun main() {
    println(create())
}

fun create(): String = buildString {
    (3..100).forEach {
        append(factoryBlock(it))
        append("\n")
    }
}

private fun factoryBlock(n: Int): String {
    val b1 = n - 1
    val b2 = n - 2

    return "object Fib${n}Binding : LinkedBinding<Fib${n}>() {\n" +
            "    override fun get(parameters: ParametersDefinition?) = \n" +
            "        Fib${n}(Fib${b1}Binding.get(), Fib${b2}Binding.get())\n" +
            "}"
}