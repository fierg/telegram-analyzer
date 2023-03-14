package com.mkleimann.querscraper.service

import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.enum.JobTypes
import io.quarkus.scheduler.Scheduled

interface Scheduler {


    fun executeRecurringJob(type: JobTypes, tooManyRequestsDelay: Long = 1000 * 60)
}