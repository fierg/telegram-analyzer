package com.mkleimann.querscraper.controller

import com.mkleimann.querscraper.const.QueryNames
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.JobTypeSettingsRepository
import com.mkleimann.querscraper.service.QueryService
import com.mkleimann.querscraper.service.Scheduler
import io.quarkus.runtime.configuration.ConfigurationException
import org.eclipse.microprofile.config.inject.ConfigProperty
import javax.inject.Inject
import javax.transaction.Transactional
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/jobs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class JobsController {

    @Inject
    lateinit var scheduler: Scheduler

    @Inject
    lateinit var queryService: QueryService

    @Inject
    lateinit var jobSettingsRepo: JobTypeSettingsRepository

    @ConfigProperty(name = "com.mkleimann.crawler.max-depth", defaultValue = "3")
    lateinit var maxDepth: String

    @Path("/grouped")
    @GET
    fun getGroupedJobState(
    ): Response? {
        val depth = maxDepth.toIntOrNull()
            ?: throw ConfigurationException("Property 'com.mkleimann.crawler.max-depth' must be Integer: $maxDepth")

        val results = queryService.getNamedQuery(
            QueryNames.JOB_COUNT_BY_TYPE_AND_STATE, mapOf(
                "maxDepth" to depth
            )
        ).resultList.groupBy { it[0] }
            .mapValues { byType ->
                byType.value.groupBy { byState -> byState[1] }
                    .mapValues { it.value[0][2] }
            }

        return Response.ok(results).build()
    }

    @Path("/settings")
    @GET
    fun getJobSettings(): Response =
        Response.ok(jobSettingsRepo.listAll().associate { it.type to it.isPaused }).build()

    @POST
    @Path("/pause")
    @Transactional(Transactional.TxType.REQUIRED)
    fun pauseJob(@QueryParam("pause") pause: Boolean, @QueryParam("type") type: JobTypes): Response {
        jobSettingsRepo.update(
            "isPaused = :pause where type = :type",
            mapOf("pause" to pause, "type" to type)
        )

        if (!pause) {
            scheduler.executeRecurringJob(type)
        }

        return getJobSettings()
    }

    @POST
    @Path("/run")
    @Transactional(Transactional.TxType.REQUIRED)
    fun runRecurringJob(@QueryParam("type") type: JobTypes): Response {
        scheduler.executeRecurringJob(type)

        return Response.ok().build()
    }

}

