package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.const.QueryNames
import org.hibernate.jpa.QueryHints
import org.hibernate.search.engine.backend.types.Projectable
import org.hibernate.search.engine.backend.types.Searchable
import org.hibernate.search.engine.backend.types.Sortable
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed
import java.time.Instant
import javax.persistence.*

@Entity(name = "message")
@Table(
    uniqueConstraints = [UniqueConstraint(
        name = "U_MSG_ID_CHATID",
        columnNames = ["telegramId", "telegramChatId"]
    )],
    indexes = [
        Index(
            name = "IDX_MSG_CHAT_ID",
            columnList = "telegramChatId"
        ),
        Index(
            name = "IDX_MSG_FORWARD_CHAT_ID",
            columnList = "forwardOriginChatId"
        ),
        Index(
            name = "IDX_MSG_AUTHOR_ID",
            columnList = "telegramAuthorId"
        ),
        Index(
            name = "IDX_MSG_TYPE",
            columnList = "messageType"
        ),
        Index(
            name = "IDX_MSG_ISFORWARDED",
            columnList = "isForwarded"
        ), Index(
            name = "IDX_MSG_DATE",
            columnList = "date"
        )
    ]
)
@NamedQueries(
    value = [NamedQuery(
        name = QueryNames.GROUP_MESSAGES_BY_TYPE_FILTERED_BY_CHATID,
        query = "select messageType, count(id) from message " +
                "where telegramChatId=:chatId " +
                "group by messageType",
        hints = [QueryHint(
            name = QueryHints.HINT_READONLY,
            value = "true"
        )]
    ), NamedQuery(
        name = QueryNames.GROUP_MESSAGES_BY_TYPE,
        query = "select messageType, count(id) from message " +
                "group by messageType",
        hints = [QueryHint(
            name = QueryHints.HINT_READONLY,
            value = "true"
        )]
    ), NamedQuery(
        name = QueryNames.GROUP_MESSAGES_BY_FORWARDED,
        query = "select isForwarded, count(id) from message group by isForwarded",
        hints = [QueryHint(
            name = QueryHints.HINT_READONLY,
            value = "true"
        )]
    ), NamedQuery(
        name = QueryNames.GROUP_MESSAGES_BY_FORWARDED_FILTERED_BY_CHATID,
        query = "select isForwarded, count(id) from message " +
                "where telegramChatId=:chatId " +
                "group by isForwarded",
        hints = [QueryHint(
            name = QueryHints.HINT_READONLY,
            value = "true"
        )]
    ), NamedQuery(
        name = QueryNames.GROUP_MESSAGES_BY_DATE,
        query = "select TO_CHAR(date,'YYYY-MM-DD') as dateOnly, count(id) from message where date > '2019-12-31' group by dateOnly order by dateOnly asc",
        hints = [QueryHint(
            name = QueryHints.HINT_READONLY,
            value = "true"
        )]
    ), NamedQuery(
        name = QueryNames.GROUP_MESSAGES_BY_DATE_FILTERED_BY_CHATID,
        query = "select TO_CHAR(date,'YYYY-MM-DD') as dateOnly, count(id) from message " +
                "where telegramChatId=:chatId and date > '2019-12-31' " +
                "group by dateOnly order by dateOnly asc",
        hints = [QueryHint(
            name = QueryHints.HINT_READONLY,
            value = "true"
        )]
    )]
)
@Indexed
open class Message(

    @Column(name = "telegramId", nullable = false)
    @GenericField(name = "messageId", projectable = Projectable.YES)
    open var telegramId: Long,

    @Column(name = "telegramChatId", nullable = false)
    @GenericField(name = "telegramChatId", projectable = Projectable.YES)
    open var telegramChatId: Long,

    @Column(name = "telegramAuthorId", nullable = false)
    @GenericField(name = "telegramAuthorId", projectable = Projectable.YES)
    open var telegramAuthorId: Long,

    @Column(name = "date", nullable = false)
    @GenericField(name = "date", projectable = Projectable.YES, sortable = Sortable.YES)
    open var date: Instant,

    @Column(name = "isForwarded", nullable = false)
    @GenericField(name = "isForwarded", projectable = Projectable.YES)
    open var isForwarded: Boolean,

    @Column(name = "forwardOriginChatId", nullable = true)
    open var forwardOriginChatId: Long?,

    @Column(name = "forwardOriginMessageId", nullable = true)
    open var forwardOriginMessageId: Long?,

    @Column(name = "textContent", nullable = false, length = 8192)
    @FullTextField(
        name = "textContent",
        searchable = Searchable.YES,
        analyzer = "german",
        projectable = Projectable.YES
    )
    open var textContent: String,

    @Column(name = "messageType", nullable = false)
    @GenericField(name = "messageType", projectable = Projectable.YES)
    open var messageType: String,

    @Column(name = "fileId", nullable = true)
    open var fileId: Int?,

    @Column(name = "remoteFileId", nullable = true)
    open var remoteFileId: String?,

    @Column(name = "remoteUniqueId", nullable = true)
    open var remoteUniqueId: String?,

    @Column(name = "fileName", nullable = true)
    open var fileName: String?,

    @Column(name = "duration", nullable = true)
    @GenericField(name = "duration", projectable = Projectable.YES)
    open var duration: Int?,

    @Column(name = "width", nullable = true)
    @GenericField(name = "width", projectable = Projectable.YES)
    open var width: Int?,

    @Column(name = "height", nullable = true)
    @GenericField(name = "height", projectable = Projectable.YES)
    open var height: Int?,

    @ElementCollection(targetClass = TextEntity::class, fetch = FetchType.LAZY)
    @CollectionTable(
        name = "messageTextEntities",
        joinColumns = [JoinColumn(name = "messageId")],
        // indexes = [Index(name = "IDX_MSG_TEXTENTITY", columnList = "messageId")]
    )
    open var textEntities: Collection<TextEntity> = mutableListOf()

) : EntityBase() {

    @Embeddable
    open class TextEntity(

        @Column(name = "type", nullable = false)
        open var type: String,

        @Column(name = "startIdx", nullable = false)
        open var startIdx: Int,

        @Column(name = "length", nullable = false)
        open var length: Int
    )
}