package io.mcarle.lib.kmapper.converter

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

class EnumToEnumConverterITest : ConverterITest() {

    companion object {

        @JvmStatic
        fun types(): List<Arguments> = listOf("MyFirstEnum").toConverterTestArguments {
            it to "MySecondEnum"
        }
    }

    @ParameterizedTest
    @MethodSource("types")
    fun converterTest(sourceTypeName: String, targetTypeName: String) {
        super.converterTest(EnumToEnumConverter(), sourceTypeName, targetTypeName)
    }

    @ParameterizedTest
    @MethodSource("types")
    fun missingEnumValues(sourceTypeName: String, targetTypeName: String) {
        expectedResultCode = KotlinCompilation.ExitCode.COMPILATION_ERROR
        super.converterTest(EnumToEnumConverter(), targetTypeName, sourceTypeName)
    }

    override fun generateAdditionalCode(): SourceFile = SourceFile.kotlin(
        name = "MyEnums.kt",
        contents =
        """
enum class MyFirstEnum { 
    XXX,
    YYY,
    ZZZ
}
enum class MySecondEnum {
    AAA,
    ZZZ,
    XXX,
    YYY,
}
        """.trimIndent()
    )

    override fun verifyMapper(
        sourceTypeName: String,
        targetTypeName: String,
        mapperInstance: Any,
        mapperFunction: KCallable<*>,
        sourceKClass: KClass<*>,
        targetKClass: KClass<*>
    ) {
        val enumValue =
            (sourceKClass.members.first { it.name == "test" }.returnType.classifier as KClass<*>).java.enumConstants.random() as Enum<*>
        val sourceInstance = sourceKClass.constructors.first().call(
            enumValue
        )

        val targetInstance = mapperFunction.call(mapperInstance, sourceInstance)

        val targetValue = assertDoesNotThrow {
            targetKClass.members.first { it.name == "test" }.call(targetInstance)
        }
        assertEquals(enumValue.toString(), targetValue.toString())
    }

}
