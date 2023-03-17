package io.mcarle.lib.kmapper.processor.shared

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import io.mcarle.lib.kmapper.api.annotation.KMap

class CodeGenerator(
    private val logger: KSPLogger
) {

    fun generateCode(
        mappings: List<KMap>,
        constructorTypes: List<KSClassDeclaration>,
        paramName: String?,
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        mappingCodeParentDeclaration: KSDeclaration
    ): String {
        val sourceProperties = PropertyMappingResolver(logger).determinePropertyMappings(paramName, mappings, source)

        val constructor = ConstructorResolver(logger)
            .determineConstructor(mappingCodeParentDeclaration, target, sourceProperties, constructorTypes)

        val targetElements = determineTargetElements(sourceProperties, constructor, target)

        verifyPropertiesAndMandatoryParametersExist(sourceProperties, targetElements)

        val targetPropertiesWithoutParameters = extractDistinctProperties(targetElements)

        return MappingCodeGenerator().generateMappingCode(
            sourceProperties.sortedByDescending { it.isBasedOnAnnotation },
            constructor,
            targetPropertiesWithoutParameters
        )
    }

    private fun extractDistinctProperties(targetElements: List<TargetElement>) = targetElements
        .mapNotNull { it.property }
        .filterNot { property ->
            targetElements
                .mapNotNull { it.parameter }
                .any { parameter ->
                    property.simpleName.asString() == parameter.name?.asString() && property.type.resolve() == parameter.type.resolve()
                }
        }

    private fun determineTargetElements(
        sourceProperties: List<PropertyMappingInfo>,
        constructor: KSFunctionDeclaration,
        target: KSClassDeclaration
    ) = if (propertiesMatchingExact(sourceProperties, constructor.parameters)) {
        // constructor params matching sourceParams
        constructor.parameters
    } else {
        // constructor params not matching sourceParams, combine with mutable properties
        constructor.parameters + determineMutableProperties(target)
    }.map { TargetElement(it) }

    private fun verifyPropertiesAndMandatoryParametersExist(
        sourceProperties: List<PropertyMappingInfo>,
        targetElements: List<TargetElement>
    ) {
        val propertyOrParamWithoutSource = targetElements.firstOrNull { propertyOrParam ->
            val name = if (propertyOrParam.property != null) {
                propertyOrParam.property.simpleName.asString()
            } else if (propertyOrParam.parameter != null) {
                if (propertyOrParam.parameter.hasDefault) return@firstOrNull false // break, as optional
                propertyOrParam.parameter.name?.asString()
            } else {
                // should not occur...
                null
            }
            sourceProperties.none { name == it.targetName }
        }
        if (propertyOrParamWithoutSource != null) {
            throw RuntimeException("Could not determine source property for property/parameter $propertyOrParamWithoutSource")
        }
    }

    @OptIn(KspExperimental::class)
    private fun determineMutableProperties(ksClassDeclaration: KSClassDeclaration): List<KSPropertyDeclaration> {
        return ksClassDeclaration.getAllProperties()
            .filter { it.extensionReceiver == null }
            .filter { it.isMutable }
            .filter { !it.isAnnotationPresent(Transient::class) } // CHECKME: is it correct to exclude transient?
            .toList()
    }

    private fun propertiesMatchingExact(props: List<PropertyMappingInfo>, parameters: List<KSValueParameter>): Boolean {
        if (parameters.isEmpty()) return props.isEmpty()
        return props
            .filter { it.isBasedOnAnnotation }
            .filterNot { it.ignore }
            .all { property ->
                parameters.any { parameter ->
                    property.targetName == parameter.name?.asString()
                }
            }
    }

    class TargetElement private constructor(
        val property: KSPropertyDeclaration? = null,
        val parameter: KSValueParameter? = null
    ) {
        constructor(annotated: KSAnnotated) : this(annotated as? KSPropertyDeclaration, annotated as? KSValueParameter)

        override fun toString(): String {
            return if (property != null) {
                "propertyDeclaration=$property"
            } else {
                "valueParameter=$parameter"
            }
        }

    }

}