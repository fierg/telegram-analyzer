package com.mkleimann.querscraper.repo

import com.mkleimann.querscraper.model.entity.Account
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class AccountRepository : PanacheRepositoryBase<Account, String>