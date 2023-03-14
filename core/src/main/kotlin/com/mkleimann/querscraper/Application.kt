package com.mkleimann.querscraper

import com.mkleimann.querscraper.model.entity.GroupChat
import com.mkleimann.querscraper.model.entity.JobTypeSettings
import com.mkleimann.querscraper.model.entity.Message
import com.mkleimann.querscraper.model.enum.JobState
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.JobInfoRepository
import com.mkleimann.querscraper.repo.JobTypeSettingsRepository
import com.mkleimann.querscraper.service.MessageLinkService
import com.mkleimann.querscraper.service.QueryService
import com.mkleimann.querscraper.service.TelegramService
import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.configuration.ConfigurationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.hibernate.search.mapper.orm.session.SearchSession
import org.jboss.logging.Logger
import java.math.BigInteger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.transaction.Transactional

@ApplicationScoped
class Application {

    @ConfigProperty(name = "quarkus.profile")
    lateinit var quarkusProfile: String

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var jobRepo: JobInfoRepository

    @Inject
    lateinit var jobTypeRepo: JobTypeSettingsRepository

    @Inject
    lateinit var searchSession: SearchSession

    @ConfigProperty(name = "com.mkleimann.search.indexer.enabled", defaultValue = "true")
    lateinit var searchIndexerEnabled: String

    @Inject
    lateinit var queryService: QueryService

    /*
    @Inject
    lateinit var messageLinkService: MessageLinkService

    @Inject
    lateinit var em: EntityManager

    fun DELETEME_createMessageLinks() {
        val regex =
            Regex("(?:https?:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&//=]*))|(?:[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z]{1,6}\\b\\/(?:[-a-zA-Z0-9()@:%_\\+.~#?&/=]*))")
        val size = 1000

        var page = 0
        var stop = false
        val query = em.createNativeQuery(
            "select m.id " +
                    "from message m " +
                    //"join message m " +
                    //"on m.id=e.messageId " +
                    //"where e.type in ('TextEntityTypeUrl', 'TextEntityTypeTextUrl') " +
                    "order by m.id desc"
        )

        while (!stop) {
            val ids = query.setFirstResult(page * size)
                .setMaxResults(size).resultList

            val batch =
                em.createQuery("select telegramChatId, telegramId, textContent from message where id in (:ids)")
            batch.setParameter("ids", ids)

            stop = processBatch(
                batch, regex
            )

            page++
            log.info("Processed ${page * size} messages")
        }

        log.info("FINISHED SCRAPING OLD MESSAGE LINKS! :)")
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun processBatch(query: Query, regex: Regex): Boolean {
        val result = query.resultList as List<Array<Any>>

        if (result.isNotEmpty()) {
            result.forEach { row ->
                val chatId = row[0] as Long
                val messageId = row[1] as Long
                val textContent = row[2] as String

                regex.findAll(textContent).forEach { match ->

                    if (match.value.length > 2048) {
                        log.warn("TOO LONG URL: ${match.value}")
                        return@forEach
                    }

                    messageLinkService.createMessageLink(chatId, messageId, match.value)
                }
            }
        } else {
            return true
        }
        return false
    }*/

    fun onStart(@Observes ev: StartupEvent?) {
        log.info(">>> Starting ($quarkusProfile mode)...")

        resetRunningJobs()
        createJobTypeSettings()

        if (searchIndexerEnabled.toBooleanStrictOrNull()
                ?: throw ConfigurationException("Property 'com.mkleimann.search.indexer.enabled' must be boolean (true/false): $searchIndexerEnabled")
        ) {
            startSearchIndexer()
        }

        runBlocking {
            telegram.authorize()

            delay(2000)

            if (telegram.isAuthorized() && jobRepo.count() == 0L) {
                telegram.findConspiracyGroups()
            }
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun createJobTypeSettings() {
        val persisted = jobTypeRepo.listAll().map { it.type }.toSet()

        JobTypes.values().forEach {
            if (!persisted.contains(it)) {
                JobTypeSettings(it, false).persist()
            }
        }
    }

    fun startSearchIndexer() {
        log.info("Starting search indexer..")
        searchSession.massIndexer(GroupChat::class.java)
            .threadsToLoadObjects(4)
            .batchSizeToLoadObjects(500)
            .idFetchSize(500)
            .transactionTimeout(60 * 60)
            .typesToIndexInParallel(2)
            .start()

        searchSession.massIndexer(Message::class.java)
            .threadsToLoadObjects(4)
            .batchSizeToLoadObjects(500)
            .idFetchSize(500)
            .transactionTimeout(60 * 60)
            .typesToIndexInParallel(2)
            .start()
    }

    /**
     * Reschedules persisted jobs which were running at time of application shutdown.
     *
     * 1) Sets [PersistedJob] state to [JobState.NEW] where the state is [JobState.RUNNING]
     * 2) Sets [RecurringJob] state to [JobState.CANCELLED] where the state is [JobState.NEW] or [JobState.RUNNING]
     */
    @Transactional(Transactional.TxType.REQUIRED)
    fun resetRunningJobs() {
        resetPersistedJobs()
        resetRecurringJobs()
    }

    private fun resetRecurringJobs() {
        val cancelledJobs = jobRepo.update(
            "state=?1 where state in (?2) and createdByClass = 'com.mkleimann.querscraper.service.impl.SchedulerImpl'",
            JobState.CANCELLED,
            listOf(JobState.NEW, JobState.RUNNING)
        )
        if (cancelledJobs > 0) {
            log.info("Set $cancelledJobs running recurring job(s) to state ${JobState.CANCELLED.name}")
        }
    }

    private fun resetPersistedJobs() {
        val resetJobs = jobRepo.update(
            "state=?1 where state=?2 and createdByClass != 'com.mkleimann.querscraper.service.impl.SchedulerImpl'",
            JobState.NEW,
            JobState.RUNNING
        )
        if (resetJobs > 0) {
            log.info("Reset $resetJobs running job(s) to state ${JobState.NEW.name}")
        }
    }
}