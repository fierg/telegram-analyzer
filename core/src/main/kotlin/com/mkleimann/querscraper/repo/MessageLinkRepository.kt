package com.mkleimann.querscraper.repo

import com.mkleimann.querscraper.model.entity.MessageLink
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class MessageLinkRepository : PanacheRepositoryBase<MessageLink, String>