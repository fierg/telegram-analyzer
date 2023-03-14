package com.mkleimann.querscraper.service

import it.tdlight.jni.TdApi

interface TelegramService {

    fun authorize()
    fun isAuthorized(): Boolean
    fun findConspiracyGroups(): Set<Long>
    suspend fun searchGroups(query: String): LongArray
    suspend fun getSupergroupInfo(supergroupId: Long): TdApi.SupergroupFullInfo
    suspend fun getUser(userId: Long): TdApi.User
    suspend fun getFullUserInfo(userId: Long): TdApi.UserFullInfo
    fun getSupergroupMembers(supergroupId: Long, callback: (List<TdApi.ChatMember>) -> Unit)
    suspend fun getGroupStatistics(chatId: Long): TdApi.ChatStatistics?
    suspend fun getMessageForwards(chatId: Long, messageId: Long, callback: (List<TdApi.Message>) -> Unit)
    suspend fun getChatByHandle(handle: String): TdApi.Chat?
    suspend fun checkInviteLink(inviteLink: String): TdApi.ChatInviteLinkInfo
    suspend fun joinChatByInviteLink(inviteLink: String): TdApi.Chat
    suspend fun getBasicGroupFullInfo(supergroupId: Long): TdApi.BasicGroupFullInfo

    suspend fun getChatHistory(chatId: Long, firstMessageId: Long): List<TdApi.Message>
    suspend fun getChat(id: Long): TdApi.Chat
    suspend fun leaveChat(it: Long)
    suspend fun getChats(): TdApi.Chats
    suspend fun downloadFile(fileId: Int): TdApi.File
    suspend fun getMessage(chatId: Long, messageId: Long): TdApi.Message
}