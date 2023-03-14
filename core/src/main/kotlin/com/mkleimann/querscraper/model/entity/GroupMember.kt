package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.const.QueryNames
import java.time.Instant
import javax.persistence.*

@Entity(name = "groupMember")
@Table(
    uniqueConstraints = [UniqueConstraint(
        name = "U_MEMBER_GROUP_ACCOUNT",
        columnNames = ["telegramChatId", "telegramUserId"]
    )]
)
@NamedQueries(
    value = [NamedQuery(
        name = QueryNames.GROUPMEMBER_BY_IDS,
        query = "select telegramUserId from groupMember where telegramUserId in (:ids) and telegramChatId = :chatId"
    )]
)
open class GroupMember(

    @Column(name = "telegramChatId", nullable = false)
    open var telegramChatId: Long,

    @Column(name = "telegramUserId", nullable = false)
    open var telegramUserId: Long,

    @Column(name = "joinedAt", nullable = false)
    open var joinedAt: Instant

) : EntityBase()