package com.mkleimann.querscraper.job.impl

import com.mkleimann.querscraper.const.QueryNames
import com.mkleimann.querscraper.exception.AbortJob
import com.mkleimann.querscraper.job.RecurringJob
import com.mkleimann.querscraper.model.entity.PrivateChatHistory
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.service.QueryService
import com.mkleimann.querscraper.service.TelegramService
import kotlinx.coroutines.runBlocking
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.enterprise.context.control.ActivateRequestContext
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.transaction.Transactional

@Dependent
class LeaveGroupsJob : RecurringJob {

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var queryService: QueryService

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var em: EntityManager

    @ActivateRequestContext
    override fun execute() {
        val ids = queryService.getNamedQuery(
            name = QueryNames.FINISHED_PRIVATE_GROUPS, resultClass = Long::class.javaObjectType, size = 5
        ).resultList

        ids.forEach {
            val privateChat = PrivateChatHistory(it)
            try {
                runBlocking { telegram.leaveChat(it) }
                persist(privateChat)
                log.info("Left chat '$it'")
            } catch (abort: AbortJob) {
                persist(privateChat)
                throw abort
            }
        }
    }

    @Transactional
    fun persist(privateChat: PrivateChatHistory) {
        if (em.createQuery("select count(*) from privateChatHistory where telegramChatId=${privateChat.telegramChatId}").singleResult as Long > 0L) {
            return
        }

        privateChat.persist()
    }

    override fun type() = JobTypes.LeaveGroups
}