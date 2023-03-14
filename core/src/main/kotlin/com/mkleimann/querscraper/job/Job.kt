package com.mkleimann.querscraper.job

import com.mkleimann.querscraper.model.enum.JobTypes

interface Job {

    fun type(): JobTypes

}