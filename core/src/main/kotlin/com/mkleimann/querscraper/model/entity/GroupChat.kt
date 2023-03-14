package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.const.QueryNames
import org.hibernate.search.engine.backend.types.Projectable
import org.hibernate.search.engine.backend.types.Searchable
import org.hibernate.search.engine.backend.types.Sortable
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed
import javax.persistence.*

/**
 * A public telegram group/superchat.
 *
 * TODO: add photo?
 */
@Entity(name = "groupChat")
@Table(
    indexes = [
        Index(name = "U_GROUP_TG_ID", unique = true, columnList = "telegramId"),
        Index(name = "U_GROUP_MEMBERCOUNT", unique = false, columnList = "memberCount")]
)
@NamedQueries(
    value = [NamedQuery(
        name = QueryNames.LIST_GROUPS_BY_FOLLOWERS,
        query = "select g.telegramId, g.title, g.description, g.memberCount, count(m.id) \n" +
                "from groupChat g \n" +
                "join message m on g.telegramId=m.telegramChatId \n" +
                "group by g.id, g.title, g.memberCount\n" +
                "order by g.memberCount desc"
    ), NamedQuery(
        name = QueryNames.CONNECTION_GRAPH,
        query = "select count(m.id), " +
                "g.telegramId, g.title, o.telegramId, o.title, g.memberCount, o.memberCount " +
                "from groupChat g " +
                "join message m on g.telegramId=m.telegramChatId " +
                "join groupChat o on o.telegramId=m.forwardOriginChatId " +
                "where (g.memberCount = 0 or g.memberCount > :minMemberCount) " +
                "and m.isForwarded = true and m.telegramChatId != m.forwardOriginChatId " +
                "group by g.telegramId, g.title, o.telegramId, o.title, g.memberCount, o.memberCount " +
                "having count(m.telegramId) > 10"
    ), NamedQuery(
        name = QueryNames.LIST_GROUPS_WITH_MSGCOUNT,
        query = "select g.telegramId, count(m.telegramId), m.isForwarded " +
                "from groupChat g " +
                "join message m on m.telegramChatId=g.telegramId " +
                // "where m.forwardOriginChatId != 0 " +
                "group by g.telegramId, m.isForwarded " +
                "having count(m.telegramId) > 1"
    ), NamedQuery(
        name = QueryNames.FINISHED_PRIVATE_GROUPS,
        query = "select g.telegramId " +
                "from groupChat g " +
                "where g.telegramId not in (select telegramChatId from privateChatHistory) and " +
                "g.telegramId in (" +
                "   select cast(j.param1 as long) " +
                "   from jobInfo j " +
                "   where j.createdByClass like '%FollowInviteLink%'" +
                "   and j.type = 'GetGroupInfo' " +
                "   and j.state = 'FINISHED') " +
                "and ((" +
                "   select count(*) " +
                "   from jobInfo j " +
                "   where j.type = 'GetMessageHistory'" +
                "   and j.state in ('NEW', 'RUNNING')" +
                "   and cast(j.param1 as long) = g.telegramId" +
                ") = 0 or g.deleted = true)"
    )

    ]
)
@Indexed
open class GroupChat(

    /**
     * The internal telegram chat id.
     */
    @Column(name = "telegramId", nullable = false, unique = true)
    @GenericField(name = "telegramId", projectable = Projectable.YES)
    open var telegramId: Long,

    /**
     * The internal telegram supergroup id.
     */
    @Column(name = "supergroupId", nullable = true, unique = false)
    open var supergroupId: Long?,

    /**
     * The group title/name.
     */
    @Column(name = "title", nullable = false)
    @FullTextField(projectable = Projectable.YES, searchable = Searchable.YES)
    open var title: String,

    /**
     * The group title/name.
     */
    @Column(name = "handle", nullable = true)
    open var handle: String? = null,

    /**
     * The group description.
     */
    @Column(name = "description", nullable = true)
    @FullTextField(projectable = Projectable.YES, searchable = Searchable.YES)
    open var description: String?,

    /**
     * The current member count. 0 if unknown.
     *
     * TODO: create table for member counts to track difference over time ??
     */
    @Column(name = "memberCount", nullable = false)
    @GenericField(name = "memberCount", projectable = Projectable.YES, sortable = Sortable.YES)
    open var memberCount: Int,

    /**
     * If the group is a channel, only the admins can write.
     */
    @Column(name = "isChannel", nullable = false)
    open var isChannel: Boolean,

    /**
     * If the member list is public, it is possible to retrieve the group members.
     */
    @Column(name = "isMemberListPublic", nullable = false)
    open var isMemberListPublic: Boolean,

    /**
     * If the history is available, new members can read old chat messages.
     */
    @Column(name = "isAllHistoryAvailable", nullable = false)
    open var isAllHistoryAvailable: Boolean,

    /**
     * If the statistics are enabled, statistics can be retrieved from telegram API.
     */
    @Column(name = "isStatisticsEnabled", nullable = false)
    open var isStatisticsEnabled: Boolean,

    /**
     * True, if the user can send text messages, contacts, locations, and venues.
     */
    @Column(name = "canSendMessages", nullable = false)
    open var canSendMessages: Boolean,

    /**
     * True, if the user can send audio files, documents, photos, videos, video notes, and voice notes. Implies can_send_messages permissions.
     */
    @Column(name = "canSendMediaMessages", nullable = false)
    open var canSendMediaMessages: Boolean,

    /**
     * True, if the user can send polls. Implies can_send_messages permissions.
     */
    @Column(name = "canSendPolls", nullable = false)
    open var canSendPolls: Boolean,

    /**
     * True, if the user can send animations, games, stickers, and dice and use inline bots. Implies can_send_messages permissions.
     */
    @Column(name = "canSendOtherMessages", nullable = false)
    open var canSendOtherMessages: Boolean,

    /**
     * True, if the user may add a web page preview to their messages. Implies can_send_messages permissions.
     */
    @Column(name = "canAddWebPagePreviews", nullable = false)
    open var canAddWebPagePreviews: Boolean,


    @Column(name = "isSupergroup", nullable = false)
    open var isSupergroup: Boolean,

    @Column(name = "deleted", nullable = false)
    @GenericField(name = "deleted", searchable = Searchable.YES)
    open var deleted: Boolean = false


) : EntityBase()