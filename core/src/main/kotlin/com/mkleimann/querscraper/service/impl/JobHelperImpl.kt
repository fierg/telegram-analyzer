package com.mkleimann.querscraper.service.impl

import com.mkleimann.querscraper.const.QueryNames
import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.service.JobHelper
import com.mkleimann.querscraper.service.QueryService
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.enterprise.context.RequestScoped
import javax.inject.Inject
import javax.transaction.Transactional

@ApplicationScoped
class JobHelperImpl : JobHelper {

    @Inject
    lateinit var queryService: QueryService

    @Inject
    lateinit var log: Logger

    val recentJobs = mutableSetOf<JobInfo>()

    @Transactional(Transactional.TxType.REQUIRED)
    override fun createJob(job: JobInfo) {
        if (!recentJobs.contains(job) && queryService.getNamedQuery(
                name = QueryNames.JOB_COUNT_BY_TYPE_AND_PARAMS,
                params = mapOf(
                    "type" to job.type,
                    "param1" to job.param1,
                    "param2" to job.param2,
                    "param3" to job.param3,
                    "param4" to job.param4,
                ),
                resultClass = Long::class.javaObjectType
            ).singleResult == 0L
        ) {
            job.persistAndFlush()
        } else {
            log.debug("Skipped creating duplicated job!: ${job.type}: ${job.createdByClass}")
        }
        recentJobs.add(job)
    }
}