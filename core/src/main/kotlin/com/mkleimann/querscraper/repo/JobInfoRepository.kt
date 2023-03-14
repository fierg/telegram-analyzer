package com.mkleimann.querscraper.repo

import com.mkleimann.querscraper.model.entity.JobInfo
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class JobInfoRepository : PanacheRepositoryBase<JobInfo, String>