package com.mkleimann.querscraper.job.impl

import com.mkleimann.querscraper.exception.AbortJob
import com.mkleimann.querscraper.job.PersistedJob
import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.GroupRepository
import com.mkleimann.querscraper.service.JobHelper
import com.mkleimann.querscraper.service.TelegramService
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.enterprise.context.control.ActivateRequestContext
import javax.inject.Inject
import javax.transaction.Transactional


/** Resolves the id of a group by handle and creates a GetGroupInfo Job.*/

@Dependent
class GetGroupInfoByHandleJob : PersistedJob {

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var groupRepo: GroupRepository

    @Inject
    lateinit var jobHelper: JobHelper

    override fun type() = JobTypes.GetGroupInfoByHandle

    @Transactional
    @ActivateRequestContext
    override suspend fun execute(task: JobInfo) {
        val handle = task.param1?.ifBlank {
            throw IllegalStateException("GetGroupInfoByHandle Job: 'param1' (handle) is blank! WHY IS THIS PERSISTED? FIX IT!")
        }!!
        val depth = task.depth ?: 0

        val chat = telegram.getChatByHandle(handle)
        if (chat != null) {
            if (groupRepo.count(
                    "telegramId=?1", chat.id
                ) != 0L
            ) {
                throw AbortJob("Group already exists (${chat.id})")
            }

            log.info("Found group by handle: '${chat.title}'")
            jobHelper.createJob(
                JobInfo(
                    type = JobTypes.GetGroupInfo,
                    param1 = "${chat.id}",
                    depth = depth,
                    priority = 100 + depth * 10,
                    createdByClass = GetGroupInfoByHandleJob::class.qualifiedName!!
                )
            )
        } else {
            throw AbortJob("Group not found by handle 'handle'")
        }
    }
}
