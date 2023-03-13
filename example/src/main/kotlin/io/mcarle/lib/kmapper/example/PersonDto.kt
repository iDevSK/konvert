package io.mcarle.lib.kmapper.example

import io.mcarle.lib.kmapper.api.annotation.KMap
import io.mcarle.lib.kmapper.api.annotation.KMapTo

@KMapTo(
    value = Person::class,
    mappings = [
        KMap(target = "age", expression = "42")
    ]
)
data class PersonDto(
    val name: String,
    val address: AddressDto,
    val ageX: ULong
)