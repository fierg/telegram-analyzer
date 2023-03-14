package com.mkleimann.querscraper.job.impl

import com.mkleimann.querscraper.exception.AbortJob
import com.mkleimann.querscraper.job.PersistedJob
import com.mkleimann.querscraper.model.entity.GroupChat
import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.GroupRepository
import com.mkleimann.querscraper.service.JobHelper
import com.mkleimann.querscraper.service.MemberService
import com.mkleimann.querscraper.service.TelegramService
import it.tdlight.jni.TdApi
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.transaction.Transactional

/**
 * Gets a telegram chat id, fetches all needed infos, persist
 */
@Dependent
class GetGroupInfoJob : PersistedJob {

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var groupRepo: GroupRepository

    @Inject
    lateinit var jobHelper: JobHelper

    @Inject
    lateinit var memberService: MemberService

    override fun type() = JobTypes.GetGroupInfo

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    override suspend fun execute(task: JobInfo) {
        val chatId = task.param1?.toLongOrNull()
            ?: throw IllegalStateException("GetGroupInfo Job: 'param1' (chatId) is not type long! WHY IS THIS PERSISTED? FIX IT!")
        val depth = task.depth ?: 0

        if (groupRepo.count("telegramId=?1", chatId) > 0L) {
            throw AbortJob("Group with id '$chatId' already exists!")
        }

        val chat = telegram.getChat(chatId)
        if (chat.type !is TdApi.ChatTypeSupergroup && chat.type !is TdApi.ChatTypeBasicGroup) {
            throw AbortJob("'$chatId' is not a (Super/Basic/Private)group: ${chat.type.javaClass.simpleName}")
        }

        var supergroupId: Long?
        var isSupergroup: Boolean
        var isChannel: Boolean
        var description: String
        var memberCount: Int
        var isMemberListPublic: Boolean = false
        var isAllHistoryAvailable: Boolean = false
        var isStatisticsEnabled: Boolean = false

        if (chat.type is TdApi.ChatTypeSupergroup) {
            val chatType = chat.type as TdApi.ChatTypeSupergroup
            supergroupId = chatType?.supergroupId
            isSupergroup = true
            isChannel = chatType.isChannel

            val info = try {
                telegram.getSupergroupInfo(supergroupId!!)
            } catch (ex: Throwable) {
                log.warn("Could not get supergroup full info for chat '${chat.title}'")
                null
            }
            description = info?.description ?: ""
            memberCount = info?.memberCount ?: 0

            isMemberListPublic = info?.canGetMembers ?: true
            isAllHistoryAvailable = info?.isAllHistoryAvailable ?: true
            isStatisticsEnabled = info?.canGetStatistics ?: false

            if (info?.canGetMembers != false) {
                jobHelper.createJob(
                    JobInfo(
                        type = JobTypes.GetMemberList,
                        param1 = "$supergroupId",
                        param2 = "$chatId",
                        priority = task.priority,
                        createdByClass = GetGroupInfoJob::class.qualifiedName!!
                    )
                )
            }
        } else {
            val chatType = chat.type as TdApi.ChatTypeBasicGroup
            supergroupId = chatType.basicGroupId
            isSupergroup = false
            isChannel = false

            val info = try {
                telegram.getBasicGroupFullInfo(supergroupId)
            } catch (ex: Throwable) {
                log.warn("Could not get basicgroup full info for chat '${chat.title}'")
                null
            }
            description = info?.description ?: ""
            memberCount = info?.members?.size ?: 0

            if (info != null) {
                // persist basic group members
                memberService.persistBatch(info.members.toList(), chatId)
            }
        }

        val group = persistGroup(
            chatId,
            supergroupId,
            chat,
            isSupergroup,
            isChannel,
            description,
            memberCount,
            isMemberListPublic,
            isAllHistoryAvailable,
            isStatisticsEnabled
        )

        log.info("✅ Persisted group: ${chat.title}")

        if (group.isAllHistoryAvailable) {
            jobHelper.createJob(
                JobInfo(
                    type = JobTypes.GetMessageHistory,
                    param1 = "$chatId",
                    depth = depth,
                    priority = task.priority,
                    createdByClass = GetGroupInfoJob::class.qualifiedName!!
                )
            )
        }
    }


    fun persistGroup(
        chatId: Long,
        supergroupId: Long,
        chat: TdApi.Chat,
        isSupergroup: Boolean,
        isChannel: Boolean,
        description: String,
        memberCount: Int,
        isMemberListPublic: Boolean,
        isAllHistoryAvailable: Boolean,
        isStatisticsEnabled: Boolean
    ): GroupChat {
        val group = GroupChat(
            telegramId = chatId,
            supergroupId = supergroupId,
            isSupergroup = isSupergroup,
            title = chat.title,
            description = description,
            memberCount = memberCount,
            isChannel = isChannel,
            isMemberListPublic = isMemberListPublic,
            isAllHistoryAvailable = isAllHistoryAvailable,
            isStatisticsEnabled = isStatisticsEnabled,
            canSendMessages = chat.permissions.canSendMessages,
            canSendMediaMessages = chat.permissions.canSendMediaMessages,
            canSendPolls = chat.permissions.canSendPolls,
            canSendOtherMessages = chat.permissions.canSendOtherMessages,
            canAddWebPagePreviews = chat.permissions.canAddWebPagePreviews
        )

        group.persist()
        return group
    }
}