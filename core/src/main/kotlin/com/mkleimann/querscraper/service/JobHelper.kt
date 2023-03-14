package com.mkleimann.querscraper.service

import com.mkleimann.querscraper.model.entity.JobInfo

interface JobHelper {

    fun createJob(job: JobInfo)
}