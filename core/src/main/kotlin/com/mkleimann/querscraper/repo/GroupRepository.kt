package com.mkleimann.querscraper.repo

import com.mkleimann.querscraper.model.entity.GroupChat
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class GroupRepository : PanacheRepositoryBase<GroupChat, String>