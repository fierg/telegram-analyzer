package com.mkleimann.querscraper.service.impl

import com.mkleimann.querscraper.exception.AbortJob
import com.mkleimann.querscraper.exception.ResetJob
import com.mkleimann.querscraper.exception.TooManyRequestsException
import com.mkleimann.querscraper.job.PersistedJob
import com.mkleimann.querscraper.job.RecurringJob
import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.enum.JobState
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.JobInfoRepository
import com.mkleimann.querscraper.repo.JobTypeSettingsRepository
import com.mkleimann.querscraper.service.Scheduler
import com.mkleimann.querscraper.service.TelegramService
import io.quarkus.panache.common.Sort
import io.quarkus.runtime.configuration.ConfigurationException
import io.quarkus.scheduler.Scheduled
import kotlinx.coroutines.*
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.util.concurrent.atomic.AtomicInteger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Any
import javax.enterprise.inject.Instance
import javax.inject.Inject
import javax.transaction.Transactional

@ApplicationScoped
class SchedulerImpl : Scheduler {

    @Inject
    lateinit var jobInfoRepo: JobInfoRepository

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    @Any
    lateinit var persistedJobTypes: Instance<PersistedJob>

    @Inject
    @Any
    lateinit var recurringJobs: Instance<RecurringJob>

    @Inject
    lateinit var jobTypeSettingsRepo: JobTypeSettingsRepository

    @ConfigProperty(name = "com.mkleimann.job.batchsize", defaultValue = "20")
    lateinit var batchSizeStr: String

    @ConfigProperty(name = "com.mkleimann.job.delay", defaultValue = "10")
    lateinit var defaultDelay: String

    @ConfigProperty(name = "com.mkleimann.crawler.max-depth", defaultValue = "3")
    lateinit var maxDepth: String

    @OptIn(DelicateCoroutinesApi::class)
    var threadPoolCtx = newFixedThreadPoolContext(32, "scheduler-coroutine")

    private fun getMaxDepth() = maxDepth.toIntOrNull()
        ?: throw ConfigurationException("Property 'com.mkleimann.crawler.max-depth' must be Integer: $maxDepth")


    @Scheduled(delayed = "1m", every = "30m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun executeGraphJob() {
        executeRecurringJob(JobTypes.CalculateGraphJob)
    }

    @Scheduled(delayed = "20s", every = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun executeLeaveGroupsJob() {
        executeRecurringJob(JobTypes.LeaveGroups)
    }

    @Scheduled(delayed = "20s", every = "1m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun executeFollowInviteLinkJob() {
        executeBatch(type = JobTypes.FollowInviteLink, batchSize = 1, tooManyRequestsDelay = 1000 * 60 * 10)
    }

    var messageJobCounter = AtomicInteger(0)

    @Scheduled(
        delayed = "20s",
        every = "3s",
        concurrentExecution = Scheduled.ConcurrentExecution.PROCEED,
    )
    fun executeMessageJob() {
        if (messageJobCounter.get() > 4) {
            log.info("5 Message Jobs already running...")
            return
        }

        messageJobCounter.incrementAndGet()
        executeBatch(type = JobTypes.GetMessageHistory, batchSize = 15)
        messageJobCounter.decrementAndGet()
    }

    @Scheduled(delayed = "5s", every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun executeMemberJob() {
        executeBatch(type = JobTypes.GetMemberList, batchSize = 2)
    }

    @Scheduled(delayed = "5s", every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun executeGroupJob() {
        executeBatch(JobTypes.GetGroupInfo)
    }

    @Scheduled(delayed = "5s", every = "90s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun executeGroupByHandleJob() {
        executeBatch(type = JobTypes.GetGroupInfoByHandle, tooManyRequestsDelay = 1000 * 60 * 60, batchSize = 1)
    }

    override fun executeRecurringJob(type: JobTypes, tooManyRequestsDelay: Long) {
        if (isPaused(type)) {
            return
        }

        //GlobalScope.launch {
        val jobImpl = recurringJobs.find { it.type() == type }
            ?: throw IllegalStateException("Could not find recurring job executor implementation for type '${type.name}'")

        val jobInstance = createRecurringJobInstance(type)

        preExecute(listOf(jobInstance))

        try {
            jobImpl.execute()
            updateState(jobInstance.id!!, JobState.FINISHED)
        } catch (ex: AbortJob) {
            updateState(jobInstance.id!!, JobState.CANCELLED)
            log.warn("⚠️  ${type.name} aborted: ${ex.message}", ex)
        } catch (ex: TooManyRequestsException) {
            log.warn("⚠️  ${type.name}: Too many requests! Hold thread for ${tooManyRequestsDelay / 1000}s..")
            runBlocking { delay(tooManyRequestsDelay) }
        } catch (ex: Throwable) {
            updateState(jobInstance.id!!, JobState.ERROR)
            log.error("❌ Job ${jobInstance.type} (${jobInstance.id}) failed: ${getCause(ex).message}", ex)
        }
        //  }
    }

    private fun isPaused(type: JobTypes) =
        jobTypeSettingsRepo.find("type = ?1", type).firstResult()?.isPaused != false

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun createRecurringJobInstance(type: JobTypes): JobInfo {
        val jobInfo = JobInfo(type = type, createdByClass = SchedulerImpl::class.java.name, state = JobState.NEW)
        jobInfo.persist()
        return jobInfo
    }

    private fun executeBatch(
        type: JobTypes,
        delayMs: Long = getDefaultDelay(),
        tooManyRequestsDelay: Long = 1000 * 60,
        batchSize: Int = getBatchsize(),
    ) {
        if (!telegram.isAuthorized() || isPaused(type)) {
            return
        }
        val maxDepth = getMaxDepth()
        val running = jobInfoRepo.count(
            "state='RUNNING' and type=:type  and (depth=null or depth<:maxDepth)",
            mapOf("type" to type, "maxDepth" to maxDepth)
        )

        if (running >= batchSize) {
            return
        }

        val jobs = jobInfoRepo.find(
            "state=:state and type=:type and (depth = null or depth<:maxDepth)",
            Sort.ascending("priority", "createdAt"),
            mapOf("state" to JobState.NEW, "type" to type, "maxDepth" to maxDepth)
        ).page(
            0,
            batchSize - running.toInt()
        ).list()

        val jobExecutor = persistedJobTypes.find { it.type().name == type.name }
            ?: throw IllegalStateException("Could not find persisted job executor implementation for type '${type.name}'")

        executeJobs(jobExecutor, jobs, delayMs, tooManyRequestsDelay)
    }

    private fun getBatchsize() = (batchSizeStr.toIntOrNull()
        ?: throw ConfigurationException("Property 'com.mkleimann.job.batchsize' must be Integer! Value: $batchSizeStr"))

    private fun getDefaultDelay() = (defaultDelay.toLongOrNull()
        ?: throw ConfigurationException("Property 'com.mkleimann.job.delay' must be Long! Value: $defaultDelay"))

    private fun executeJobs(
        jobExecutor: PersistedJob,
        jobs: List<JobInfo>,
        delayMs: Long,
        tooManyRequestsDelay: Long,
    ) {
        preExecute(jobs)
        //try {
        //   withTimeout(60_000) {
        GlobalScope.launch(threadPoolCtx) {
            jobs.forEach { job ->
                delay(delayMs)

                this.launch {
                    executePersistedJob(jobExecutor, job, tooManyRequestsDelay)
                }
            }
        }
        //    }
        // } catch (timeout: TimeoutCancellationException) {
        //    log.warn("Timeout executing jobs (60s)")
        //    jobs.forEach { updateState(it.id!!, JobState.NEW) }
        // }
    }

    private suspend fun executePersistedJob(
        jobExecutor: PersistedJob,
        job: JobInfo,
        tooManyRequestsDelay: Long,
    ) {
        try {
            jobExecutor.execute(job)
            updateState(job.id!!, JobState.FINISHED)
        } catch (abort: AbortJob) {
            updateState(job.id!!, JobState.CANCELLED)
            log.warn("⚠️  ${job.type.name} aborted: ${abort.message}")
        } catch (reset: ResetJob) {
            updateState(job.id!!, JobState.NEW)
            log.warn("⚠️  ${job.type.name} resetted: ${reset.message}")
        } catch (timeout: TimeoutCancellationException) {
            updateState(job.id!!, JobState.NEW)
            log.warn("⚠️  ${job.type.name} resetted: timeout!")
        } catch (ex: TooManyRequestsException) {
            updateState(job.id!!, JobState.NEW)
            log.warn("⚠️  ${job.type.name}: Too many requests! Hold thread for ${tooManyRequestsDelay / 1000}s..")

            // FIXME: this will not work in coroutines
            delay(tooManyRequestsDelay)
        } catch (ex: Throwable) {
            updateState(job.id!!, JobState.ERROR)
            log.error("❌ Job ${job.type} (${job.id}) failed: ${getCause(ex).message}", ex)
        }
    }


    private fun getCause(ex: Throwable): Throwable {
        if (ex.cause == null) {
            return ex
        }
        return getCause(ex.cause!!)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
// @ActivateRequestContext
    fun preExecute(jobs: Collection<JobInfo>) {
        jobs.forEach { job ->
            updateState(job.id!!, JobState.RUNNING)
        }
    }


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun updateState(id: String, state: JobState) {
        jobInfoRepo.update("state=?1 where id=?2", state, id)
    }

}