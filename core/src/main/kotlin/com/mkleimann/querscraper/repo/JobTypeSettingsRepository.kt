package com.mkleimann.querscraper.repo

import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.entity.JobTypeSettings
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class JobTypeSettingsRepository : PanacheRepositoryBase<JobTypeSettings, String>