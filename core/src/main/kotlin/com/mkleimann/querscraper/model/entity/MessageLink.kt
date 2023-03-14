package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.const.QueryNames.TOP_DOMAINS_BY_GROUP
import javax.persistence.*

@Entity(name = "messageLink")
@Table(
    uniqueConstraints = [UniqueConstraint(
        name = "U_MSGLINK_URL",
        columnNames = ["chatId", "messageId", "url"]
    )],
    indexes = [Index(name = "IDX_MSGLINK_CHATID", columnList = "chatId"), Index(
        name = "IDX_MSGLINK_DOMAIN",
        columnList = "domain"
    )]
)
@NamedQueries(
    value = [NamedQuery(
        name = TOP_DOMAINS_BY_GROUP,
        query = "select count(*) as c, domain " +
                "from messageLink " +
                "where chatId = :chatId " +
                "group by domain " +
                "order by c desc"
    )]
)
open class MessageLink(

    @Column(name = "chatId", nullable = false)
    open var chatId: Long,

    @Column(name = "messageId", nullable = false)
    open var messageId: Long,

    @Column(name = "url", nullable = false, length = 2048)
    open var url: String,

    @Column(name = "domain", nullable = false)
    open var domain: String

) : EntityBase()