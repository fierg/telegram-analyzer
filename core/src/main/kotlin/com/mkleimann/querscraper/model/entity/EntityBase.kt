package com.mkleimann.querscraper.model.entity

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.aventrix.jnanoid.jnanoid.NanoIdUtils.DEFAULT_ALPHABET
import com.fasterxml.jackson.annotation.JsonIgnore
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator
import java.time.Instant
import java.util.*
import javax.persistence.*

const val ID_GENERATOR_NAME = "nanoid"
const val ID_GENERATOR_STRATEGY = "com.mkleimann.querscraper.model.entity.IdGenerator"
const val ID_LENGTH = 16

@MappedSuperclass
abstract class EntityBase(
    @Id
    @Column(name = "ID", length = ID_LENGTH)
    @GenericGenerator(name = ID_GENERATOR_NAME, strategy = ID_GENERATOR_STRATEGY)
    @GeneratedValue(generator = ID_GENERATOR_NAME)
    open var id: String? = null
) : PanacheEntityBase {

    @JsonIgnore
    @Version
    private var version: Long? = null

    @JsonIgnore
    @field:CreationTimestamp
    open var createdAt: Instant? = null

    @JsonIgnore
    @field:UpdateTimestamp
    open var updatedAt: Instant? = null

}

class IdGenerator : IdentifierGenerator {
    override fun generate(session: SharedSessionContractImplementor?, obj: Any?): String =
        NanoIdUtils.randomNanoId(Random(), DEFAULT_ALPHABET, ID_LENGTH)
}