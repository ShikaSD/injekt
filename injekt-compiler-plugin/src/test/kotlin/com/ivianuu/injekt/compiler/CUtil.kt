package com.ivianuu.injekt.compiler

import com.ivianuu.injekt.Scope
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.intellij.lang.annotations.Language
import kotlin.reflect.KClass

fun source(
    @Language("kotlin") source: String,
    name: String = "File.kt",
    injektImports: Boolean = true
) = SourceFile.kotlin(
    name = name,
    contents = buildString {
        if (injektImports) {
            appendln("import com.ivianuu.injekt.*")
            appendln("import com.ivianuu.injekt.compiler.*")
            appendln()
        }

        append(source)
    }
)

fun codegenTest(
    @Language("kotlin") source: String,
    assertions: KotlinCompilation.Result.() -> Unit = {}
) = codegenTest(source(source), assertions = assertions)

fun codegenTest(
    vararg sources: SourceFile,
    assertions: KotlinCompilation.Result.() -> Unit = {}
) {
    val result = KotlinCompilation().apply {
        this.sources = sources.toList()
        compilerPlugins = listOf(InjektComponentRegistrar())
        inheritClassPath = true
        useIR = true
        jvmTarget = "1.8"
        verbose = false

    }.compile()
    println("Result: ${result.exitCode} m: ${result.messages}")
    assertions(result)
}

fun KotlinCompilation.Result.assertOk() {
    assertEquals(KotlinCompilation.ExitCode.OK, exitCode)
}

fun KotlinCompilation.Result.expectNoErrorsWhileInvokingSingleFile() {
    assertOk()

    try {
        invokeSingleFile()
    } catch (e: Exception) {
        throw AssertionError(e)
    }
}

@JvmName("invokeSingleFileTypeless")
fun KotlinCompilation.Result.invokeSingleFile(): Any? = invokeSingleFile<Any?>()

fun <T> KotlinCompilation.Result.invokeSingleFile(): T {
    val generatedClass = getSingleClass().java
    return generatedClass.declaredMethods
        .single { it.name == "invoke" }
        .invoke(null) as T
}

private fun KotlinCompilation.Result.getSingleClass(): KClass<*> =
    classLoader.loadClass("FileKt").kotlin

fun KotlinCompilation.Result.assertInternalError(
    message: String? = null
) {
    assertEquals(KotlinCompilation.ExitCode.INTERNAL_ERROR, exitCode)
    message?.let { assertTrue(messages.toLowerCase().contains(it.toLowerCase())) }
}

class Foo

class Bar(foo: Foo)

@Scope
annotation class TestScope

@Scope
annotation class TestScope2