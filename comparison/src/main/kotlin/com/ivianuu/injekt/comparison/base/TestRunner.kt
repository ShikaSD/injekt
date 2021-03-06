
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

package com.ivianuu.injekt.comparison.base

import com.ivianuu.injekt.comparison.dagger.DaggerTest
import com.ivianuu.injekt.comparison.dagger2.Dagger2Test
import com.ivianuu.injekt.comparison.dagger2reflect.Dagger2ReflectTest
import com.ivianuu.injekt.comparison.guice.GuiceTest
import com.ivianuu.injekt.comparison.injekt.InjektTest
import com.ivianuu.injekt.comparison.katana.KatanaTest
import com.ivianuu.injekt.comparison.kodein.KodeinTest
import com.ivianuu.injekt.comparison.koin.KoinTest
import com.ivianuu.injekt.comparison.toothpick.ToothpickTest
import kotlin.system.measureNanoTime

val defaultConfig = Config(
    rounds = 100_000,
    timeUnit = TimeUnit.Nanos
)

data class Config(
    val rounds: Int,
    val timeUnit: TimeUnit
)

enum class TimeUnit {
    Nanos, Millis
}

fun runAllInjectionTests(config: Config = defaultConfig) {
    runInjectionTests(
        listOf(
            DaggerTest,
            Dagger2Test,
            Dagger2ReflectTest,
            GuiceTest,
            InjektTest,
            KatanaTest,
            KodeinTest,
            KoinTest,
            ToothpickTest
        ).shuffled(),
        config
    )
}

fun runInjectionTests(vararg tests: InjectionTest, config: Config = defaultConfig) {
    runInjectionTests(tests.toList(), config)
}

fun runInjectionTests(tests: List<InjectionTest>, config: Config = defaultConfig) {
    repeat(5000) { tests.forEach { measure(it) } }

    println("Running ${config.rounds} iterations...")

    val timingsPerTest = mutableMapOf<String, MutableList<Timings>>()

    repeat(config.rounds) {
        tests.forEach { test ->
            timingsPerTest.getOrPut(test.name) { mutableListOf() } += measure(
                test
            )
        }
    }

    val results = timingsPerTest
        .mapValues { it.value.results() }

    println()

    results.print(config)
}

fun measure(test: InjectionTest): Timings {
    val setup = measureNanoTime { test.setup() }
    val firstInjection = measureNanoTime { test.inject() }
    val secondInjection = measureNanoTime { test.inject() }
    test.shutdown()
    return Timings(
        test.name,
        setup,
        firstInjection,
        secondInjection
    )
}

data class Timings(
    val injectorName: String,
    val setup: Long,
    val firstInjection: Long,
    val secondInjection: Long
)

data class Result(
    val name: String,
    val timings: List<Long>
) {
    val average = timings.average()
    val min = timings.min()!!.toDouble()
    val max = timings.max()!!.toDouble()
}

data class Results(
    val injectorName: String,
    val setup: Result,
    val firstInjection: Result,
    val secondInjection: Result
) {
    val overall = Result(
        "Overall",
        setup.timings.indices.map {
            setup.timings[it] +
                    firstInjection.timings[it] +
                    secondInjection.timings[it]
        }
    )
}

fun List<Timings>.results(): Results {
    return Results(
        injectorName = first().injectorName,
        setup = Result(
            "Setup",
            map { it.setup }),
        firstInjection = Result(
            "First injection",
            map { it.firstInjection }),
        secondInjection = Result(
            "Second injection",
            map { it.secondInjection })
    )
}

fun Double.format(config: Config): String {
    return when (config.timeUnit) {
        TimeUnit.Millis -> String.format("%.5f ms", this / 1000000.0)
        TimeUnit.Nanos -> this.toString()
    }
}

fun Result.print(name: String, config: Config) {
    println(
        "$name | " +
                "${average.format(config)} | " +
                "${min.format(config)} | " +
                "${max.format(config)}"
    )
}

fun Map<String, Results>.print(config: Config) {
    fun printCategory(
        categoryTitle: String,
        pick: Results.() -> Result
    ) {
        println("$categoryTitle:")
        println("Library | Average | Min | Max")
        toList()
            .sortedBy { it.second.pick().average }
            .forEach { (name, results) ->
                results.pick().print(name, config)
            }

        println()
    }

    printCategory("Setup") { setup }
    printCategory("First injection") { firstInjection }
    printCategory("Second injection") { secondInjection }
    printCategory("Overall") { overall }
}
