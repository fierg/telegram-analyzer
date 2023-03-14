package com.mkleimann.querscraper.job.impl

import com.mkleimann.querscraper.job.PersistedJob
import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.service.MemberService
import com.mkleimann.querscraper.service.QueryService
import com.mkleimann.querscraper.service.TelegramService
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.inject.Inject

/**
 * Gets a telegram chat id, fetches all needed infos, persist
 */
@Dependent
class GetMemberListJob : PersistedJob {

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var queryService: QueryService

    @Inject
    lateinit var memberService: MemberService

    override fun type() = JobTypes.GetMemberList

    override suspend fun execute(task: JobInfo) {
        val supergroupId = task.param1?.toLongOrNull()
            ?: throw IllegalStateException("GetMemberList Job: 'param1' (supergroupId) is not type long! WHY IS THIS PERSISTED? FIX IT!")
        val chatId = task.param2?.toLongOrNull()
            ?: throw IllegalStateException("GetMemberList Job: 'param2' (chatId) is not type long! WHY IS THIS PERSISTED? FIX IT!")

        telegram.getSupergroupMembers(supergroupId) { batch ->
            memberService.persistBatch(batch, chatId)
        }
    }

}