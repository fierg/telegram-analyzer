package com.mkleimann.querscraper.const

import javax.persistence.NamedQuery

object QueryNames {

    const val JOB_COUNT_BY_TYPE_AND_STATE = "jobCountByTypeAndState"
    const val LIST_GROUPS_BY_FOLLOWERS = "listGroupsByFollowers"
    const val GROUP_MESSAGES_BY_TYPE = "groupMessagesByType"
    const val GROUP_MESSAGES_BY_TYPE_FILTERED_BY_CHATID = "groupMessagesByTypeFilteredByChatId"
    const val GROUP_MESSAGES_BY_FORWARDED = "groupMessagesByForwarded"
    const val GROUP_MESSAGES_BY_FORWARDED_FILTERED_BY_CHATID = "groupMessagesByForwardedFilteredByChatId"
    const val GROUP_MESSAGES_BY_DATE = "groupMessagesByDate"
    const val GROUP_MESSAGES_BY_DATE_FILTERED_BY_CHATID = "groupMessagesByDateFilteredByChatId"
    const val CONNECTION_GRAPH = "connectionGraph"
    const val LIST_GROUPS_WITH_MSGCOUNT = "listGroupsWithMsgCount"
    const val LIST_GRAPHEDGES = "listGraphEdges"
    const val JOB_COUNT_BY_TYPE_AND_PARAMS = "jobCountByTypeAndParams"
    const val USERS_BY_IDS = "usersByIds"
    const val GROUPMEMBER_BY_IDS = "groupMembersByIdsAndChatId"
    const val FINISHED_PRIVATE_GROUPS = "finishedPrivateGroups"
    const val TOP_DOMAINS_BY_GROUP = "topDomainsByGroup"
}