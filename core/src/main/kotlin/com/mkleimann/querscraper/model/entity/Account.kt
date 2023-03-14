package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.const.QueryNames
import javax.persistence.*

@Entity(name = "account")
@Table(
    /*uniqueConstraints = [UniqueConstraint(
        name = "U_TYPE_STATE_PARAMS",
        columnNames = ["type", "state", "param1", "param2", "param3"]
    )]*/
)
@NamedQueries(
    value = [NamedQuery(
        name = QueryNames.USERS_BY_IDS,
        query = "select telegramId from account where telegramId in (:ids)"
    )]
)
open class Account(

    @Column(name = "telegramId", nullable = false, unique = true)
    open var telegramId: Long,

    @Column(name = "firstName", nullable = true)
    open var firstName: String?,

    @Column(name = "lastName", nullable = true)
    open var lastName: String?,

    @Column(name = "username", nullable = true)
    open var username: String?,

    // @Column(name = "bio", nullable = true)
    // open var bio: String?,

    @Column(name = "phoneNumber", nullable = true)
    open var phoneNumber: String,

    @Column(name = "isVerified", nullable = false)
    open var isVerified: Boolean,

    @Column(name = "isScam", nullable = false)
    open var isScam: Boolean,

    @Column(name = "isFake", nullable = false)
    open var isFake: Boolean

) : EntityBase()