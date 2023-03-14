package com.mkleimann.querscraper.job

import com.mkleimann.querscraper.model.entity.JobInfo

interface PersistedJob : Job {

    suspend fun execute(task: JobInfo)

}