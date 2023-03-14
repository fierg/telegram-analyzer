package com.mkleimann.querscraper.service.impl

import com.mkleimann.querscraper.exception.AbortJob
import com.mkleimann.querscraper.exception.TooManyRequestsException
import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.JobInfoRepository
import com.mkleimann.querscraper.service.CsvReader
import com.mkleimann.querscraper.service.TelegramService
import io.quarkus.runtime.configuration.ConfigurationException
import it.tdlight.client.APIToken
import it.tdlight.client.AuthenticationData
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.TDLibSettings
import it.tdlight.common.Init
import it.tdlight.jni.TdApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Paths
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.transaction.Transactional
import javax.ws.rs.BadRequestException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@InternalCoroutinesApi
@ApplicationScoped
class TelegramServiceImpl : TelegramService {

    @ConfigProperty(name = "com.mkleimann.telegram.api.id")
    lateinit var rawApiId: String

    @ConfigProperty(name = "com.mkleimann.telegram.api.hash")
    lateinit var apiHash: String

    @ConfigProperty(name = "com.mkleimann.telegram.directory")
    lateinit var telegramDirectoryOverride: String

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var csvReader: CsvReader

    @Inject
    lateinit var jobRepo: JobInfoRepository

    lateinit var client: SimpleTelegramClient

    var authorized = false

    override fun authorize() {
        val apiId = rawApiId.toIntOrNull()
            ?: throw ConfigurationException("Invalid API ID: com.mkleimann.telegram.api.id=$rawApiId")
        if (apiHash.isBlank()) {
            throw ConfigurationException("Missing API hash: com.mkleimann.telegram.api.hash")
        }

        Init.start()

        val settings = TDLibSettings.create(APIToken(apiId, apiHash));

        // Configure the session directory
        val sessionPath = if (telegramDirectoryOverride.isBlank()) {
            Paths.get(System.getProperty("user.home"))
                .resolve("querscraper-session") // default to ~/querscraper-session
        } else Paths.get(telegramDirectoryOverride)

        log.info("Telegram directory: ${sessionPath.toAbsolutePath()}")

        settings.isMessageDatabaseEnabled = true
        settings.databaseDirectoryPath = sessionPath.resolve("data")
        settings.downloadedFilesDirectoryPath = sessionPath.resolve("downloads")

        val authData = AuthenticationData.qrCode()

        client = SimpleTelegramClient(settings)
        client.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) {
            when (it.authorizationState) {
                is TdApi.AuthorizationStateReady -> {
                    log.info("✅ Logged in\n")
                    authorized = true
                    // request(TdApi.GetChats())
                }
                is TdApi.AuthorizationStateClosing -> {
                    log.info("Closing...");
                }
                is TdApi.AuthorizationStateClosed -> {
                    log.info("Closed");
                }
                is TdApi.AuthorizationStateLoggingOut -> {
                    log.info("Logging out...");

                    authorized = false
                }
            }
        }

        // Start the client
        client.start(authData);
    }

    override fun isAuthorized(): Boolean {
        return authorized
    }

    @Transactional
    override fun findConspiracyGroups(): Set<Long> {
        val result = mutableSetOf<Long>()
        csvReader.forEachLine("GroupSearchQueries.csv") { queryObj ->
            val query = queryObj["query"]!!
            runBlocking {
                val groups = searchGroups(query)

                jobRepo.persist(groups.map {
                    JobInfo(
                        type = JobTypes.GetGroupInfo, param1 = "$it", createdByClass = this::javaClass.get().name
                    )
                })

                result.addAll(groups.toList())
            }
        }
        return result.toSet()
    }

    override suspend fun searchGroups(query: String): LongArray {
        var chatIds = request(TdApi.SearchPublicChats(query)).chatIds

        if (chatIds.isEmpty()) {
            // try again because sometimes it just finds nothing?! fml
            chatIds = request(TdApi.SearchPublicChats(query)).chatIds

            if (chatIds.isEmpty()) {
                log.warn("Nothing found for query: $query")
            }
        }
        return chatIds
    }

    override suspend fun getChat(id: Long): TdApi.Chat {
        return request(TdApi.GetChat(id), "chatId=$id")
    }

    override suspend fun leaveChat(id: Long) {
        request(TdApi.LeaveChat(id), "chatId=$id")
    }

    override suspend fun getChats(): TdApi.Chats {
        return request(TdApi.GetChats(TdApi.ChatListMain(), 1))
    }

    override suspend fun downloadFile(fileId: Int): TdApi.File {
        return request(TdApi.DownloadFile(fileId, 1, 0, 0, true))
    }

    override suspend fun getMessage(chatId: Long, messageId: Long): TdApi.Message {
        return request(TdApi.GetMessage(chatId, messageId))
    }

    override suspend fun getSupergroupInfo(supergroupId: Long): TdApi.SupergroupFullInfo {
        return request(TdApi.GetSupergroupFullInfo(supergroupId), "supergroupId=$supergroupId")
    }

    override fun getSupergroupMembers(supergroupId: Long, callback: (List<TdApi.ChatMember>) -> Unit) {
        var noOfResults = 0

        do {
            var chatMembers: TdApi.ChatMembers = TdApi.ChatMembers()
            var failed = false

            try {
                chatMembers = runBlocking {
                    request(
                        TdApi.GetSupergroupMembers(
                            supergroupId, TdApi.SupergroupMembersFilterSearch(), noOfResults, 200
                        )
                    )
                }
                noOfResults += chatMembers.members.size
                callback(chatMembers.members.toList())
                runBlocking { delay(20) }
            } catch (ex: TooManyRequestsException) {
                failed = true
                log.warn("⚠️  getChatMembers(): Too many requests! Hold thread for 30s..")
                runBlocking {
                    delay(30000) // wait 30 seconds
                }
            }
        } while (failed || chatMembers.members.isNotEmpty())
    }

    override suspend fun getGroupStatistics(chatId: Long): TdApi.ChatStatistics? {
        return request(TdApi.GetChatStatistics(chatId, true))
    }

    override suspend fun getMessageForwards(chatId: Long, messageId: Long, callback: (List<TdApi.Message>) -> Unit) {
        var nextOffset = ""

        do {
            val results = request(TdApi.GetMessagePublicForwards(chatId, messageId, nextOffset, 100))
            callback(results.messages.toList())
            nextOffset = results.nextOffset
            runBlocking { delay(50) }
        } while (results.messages.isNotEmpty())
    }

    override suspend fun getChatByHandle(handle: String): TdApi.Chat? {
        return request(TdApi.SearchPublicChat(handle))
    }

    override suspend fun checkInviteLink(inviteLink: String): TdApi.ChatInviteLinkInfo {
        return request(TdApi.CheckChatInviteLink(inviteLink))
    }

    override suspend fun joinChatByInviteLink(inviteLink: String): TdApi.Chat {
        return request(TdApi.JoinChatByInviteLink(inviteLink))
    }

    override suspend fun getBasicGroupFullInfo(basicGroupId: Long): TdApi.BasicGroupFullInfo {
        return request(TdApi.GetBasicGroupFullInfo(basicGroupId))
    }

    override suspend fun getChatHistory(
        chatId: Long,
        firstMessageId: Long,
    ): List<TdApi.Message> {
        return request(
            TdApi.GetChatHistory(chatId, firstMessageId, 0, 100, false),
            "chatId=$chatId, firstMessageId=$firstMessageId"
        ).messages.toList()
    }

    override suspend fun getUser(userId: Long): TdApi.User {
        return request(TdApi.GetUser(userId), "userId=$userId")
    }

    override suspend fun getFullUserInfo(userId: Long): TdApi.UserFullInfo {
        return request(TdApi.GetUserFullInfo(userId), "userId=$userId")
    }

    private fun handleError(
        error: TdApi.Error,
        exceptionDetails: String
    ) {
        when (error.code) {
            400 -> throw AbortJob("$exceptionDetails: ${error.message}")
            429 -> throw TooManyRequestsException("$exceptionDetails: ${error.message}")

            else -> throw BadRequestException("$exceptionDetails: [${error.code}] ${error.message}")
        }
    }

    private final suspend inline fun <reified T : TdApi.Object> request(
        function: TdApi.Function<T>, exceptionDetails: String = "Error"
    ): T = run {
        suspendCoroutine { continuation ->
            client.send(function) { result ->
                if (result.isError) {
                    continuation.resumeWithException(
                        when (result.error.code) {
                            400 -> AbortJob("$exceptionDetails: ${result.error.message}")
                            429 -> TooManyRequestsException("$exceptionDetails: ${result.error.message}")

                            else -> BadRequestException("$exceptionDetails: [${result.error.code}] ${result.error.message}")

                        }
                    )
                } else {
                    continuation.resume(result.get())
                }
            }
        }
    }
}