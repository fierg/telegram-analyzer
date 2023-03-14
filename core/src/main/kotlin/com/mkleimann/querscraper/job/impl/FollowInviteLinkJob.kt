package com.mkleimann.querscraper.job.impl

import com.mkleimann.querscraper.exception.AbortJob
import com.mkleimann.querscraper.exception.ResetJob
import com.mkleimann.querscraper.exception.TooManyRequestsException
import com.mkleimann.querscraper.job.PersistedJob
import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.GroupRepository
import com.mkleimann.querscraper.service.JobHelper
import com.mkleimann.querscraper.service.TelegramService
import kotlinx.coroutines.delay
import org.jboss.logging.Logger
import javax.enterprise.context.Dependent
import javax.enterprise.context.control.ActivateRequestContext
import javax.inject.Inject
import javax.transaction.Transactional

@Dependent
class FollowInviteLinkJob : PersistedJob {

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var groupRepo: GroupRepository

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var jobHelper: JobHelper

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @ActivateRequestContext
    override suspend fun execute(task: JobInfo) {
        val inviteLink = task.param1?.ifBlank {
            throw IllegalStateException("FollowInviteLink Job: 'param1' (inviteLink) is blank or null! WHY IS THIS PERSISTED? FIX IT!")
        }!!
        val depth = task.depth ?: 0

        val chats = telegram.getChats()
        if (chats.totalCount > 200) {
            throw ResetJob("Max number of chats exceeded: ${chats.totalCount}")
        } else {
            log.info("Currently joined ${chats.totalCount} chats")
        }


        // 1) check invite link
        log.info("Check invite link: $inviteLink")
        val inviteLinkInfo = telegram.checkInviteLink(inviteLink)

        if (groupRepo.count("telegramId=?1", inviteLinkInfo.chatId) == 0L) {
            if (inviteLinkInfo.isPublic) {
                log.info("Found public group by invite link, no need to join: ${inviteLinkInfo.title}")
                jobHelper.createJob(
                    JobInfo(
                        type = JobTypes.GetGroupInfo,
                        param1 = "${inviteLinkInfo.chatId}",
                        depth = depth,
                        priority = 100 + depth * 10,
                        createdByClass = FollowInviteLinkJob::class.qualifiedName!!
                    )
                )
            } else {
                // 2) if private group, join
                if (!inviteLinkInfo.createsJoinRequest) {
                    log.info("Found private group by invite link, trying to join: ${inviteLinkInfo.title}")
                    try {
                        val chat = telegram.joinChatByInviteLink(inviteLink)
                        jobHelper.createJob(
                            JobInfo(
                                type = JobTypes.GetGroupInfo,
                                param1 = "${chat.id}",
                                depth = depth,
                                priority = depth * 10, // higher priority for private groups (before getting kicked out)
                                createdByClass = FollowInviteLinkJob::class.qualifiedName!!
                            )
                        )
                        delay(30000) // before trying to join, wait 30s
                    } catch (tooManyRequests: TooManyRequestsException) {
                        throw tooManyRequests
                    } catch (ex: Throwable) {
                        delay(30000) // before trying to join, wait 30s
                        throw AbortJob("Can't join group ${inviteLinkInfo.chatId}: ${ex.message}")
                    }
                } else {
                    throw AbortJob("Invite link creates join request! $inviteLink")
                }
            }
        } else {
            throw AbortJob("Group '${inviteLinkInfo.chatId}' already exists! No need to join again.")
        }


    }

    override fun type() = JobTypes.FollowInviteLink
}