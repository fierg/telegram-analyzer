package com.mkleimann.querscraper.const

import javax.persistence.NamedQuery

object CacheNames {

    const val MESSAGES_BY_TYPE = "messagesByType"
    const val MESSAGES_BY_FORWARDS = "messagesByForwards"
    const val MESSAGES_BY_DAY = "messagesByDay"
    const val MESSAGES_COUNT = "messagesCount"
    const val GROUPS_COUNT = "groupsCount"
}