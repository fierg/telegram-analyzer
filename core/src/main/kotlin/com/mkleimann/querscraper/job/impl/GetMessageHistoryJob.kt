package com.mkleimann.querscraper.job.impl

import com.mkleimann.querscraper.exception.AbortJob
import com.mkleimann.querscraper.job.PersistedJob
import com.mkleimann.querscraper.model.entity.JobInfo
import com.mkleimann.querscraper.model.entity.Message
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.GroupRepository
import com.mkleimann.querscraper.repo.MessageRepository
import com.mkleimann.querscraper.service.JobHelper
import com.mkleimann.querscraper.service.MessageLinkService
import com.mkleimann.querscraper.service.TelegramService
import it.tdlight.jni.TdApi
import kotlinx.coroutines.withTimeout
import org.jboss.logging.Logger
import java.lang.Integer.min
import java.time.Instant
import javax.enterprise.context.Dependent
import javax.inject.Inject
import javax.transaction.Transactional

/**
 * Gets a telegram chat id, fetches all needed infos, persist
 */
@Dependent
class GetMessageHistoryJob : PersistedJob {

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var msgRepo: MessageRepository

    @Inject
    lateinit var groupRepo: GroupRepository

    @Inject
    lateinit var messageLinkHelper: MessageLinkService

    @Inject
    lateinit var jobHelper: JobHelper

    override fun type() = JobTypes.GetMessageHistory

    override suspend fun execute(task: JobInfo) {
        val chatId = task.param1?.toLongOrNull()
            ?: throw IllegalStateException("GetMessageHistory Job: 'param1' (chatId) is not type long! WHY IS THIS PERSISTED? FIX IT!")
        val pageCount = task.param2?.toIntOrNull() ?: 1
        val firstMessageId = task.param3?.toLongOrNull() ?: 0
        val depth = task.depth ?: 0

        val messages =
            withTimeout(30_000) {
                telegram.getChatHistory(chatId, firstMessageId)
            }

        executeTransactional(messages, chatId, depth, firstMessageId, pageCount, task.priority)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun executeTransactional(
        messages: List<TdApi.Message>,
        chatId: Long,
        depth: Int,
        firstMessageId: Long,
        pageCount: Int,
        priority: Int
    ) {
        processMessageBatch(messages.toMutableList(), chatId, depth, priority)

        val lastMessageId = if (messages.isEmpty()) -1 else messages.last().id
        if (lastMessageId > -1 && lastMessageId != firstMessageId) {
            jobHelper.createJob(
                JobInfo(
                    type = JobTypes.GetMessageHistory,
                    param1 = "$chatId",
                    param2 = "${pageCount + 1}",
                    param3 = "$lastMessageId",
                    depth = depth,
                    priority = priority + (depth * 10) - pageCount,
                    createdByClass = GetMessageHistoryJob::class.qualifiedName!!
                )
            )
        }

        if (messages.isNotEmpty()) {
            log.info(
                "✅ Persisted messages: ${messages.size}   (chatId=$chatId, page=$pageCount, timestamp=${
                    Instant.ofEpochSecond(
                        messages.last().date.toLong()
                    )
                })"
            )
        }
    }

    fun processMessageBatch(messages: MutableList<TdApi.Message>, chatId: Long, depth: Int, priority: Int) {
        val forwardedGroupIds = mutableSetOf<Long>()

        val persisted = msgRepo.list("telegramChatId=?1 and telegramId in (?2)", chatId, messages.map { it.id })
            .map { it.telegramId }
        messages.removeIf { persisted.contains(it.id) }
        // TODO: abort if messages already persisted?

        return messages.forEach { message ->
            val msgTimestamp = Instant.ofEpochSecond(message.date.toLong())
            if (msgTimestamp.isBefore(Instant.parse("2020-01-01T00:00:00.00Z"))) {
                throw AbortJob("Skip messages before 2020")
            }

            val senderId = when (message.senderId) {
                is TdApi.MessageSenderUser -> (message.senderId as TdApi.MessageSenderUser).userId
                else -> message.chatId
            }

            var fileId: Int? = null
            var remoteFileId: String? = null
            var remoteUniqueId: String? = null
            var fileName: String? = null
            var duration: Int? = null
            var width: Int? = null
            var height: Int? = null
            val textEntities = mutableListOf<TdApi.TextEntity>()

            val content = when (message.content) {
                is TdApi.MessageText -> {
                    val msg = (message.content as TdApi.MessageText)
                    textEntities.addAll(msg.text.entities)

                    msg.text.text
                }
                is TdApi.MessageAnimation -> {
                    val msg = (message.content as TdApi.MessageAnimation)
                    textEntities.addAll(msg.caption.entities)

                    msg.caption.text
                }
                //  is TdApi.MessageAnimatedEmoji -> (message.content as TdApi.MessageAnimatedEmoji).emoji
                // is TdApi.MessageSticker -> (message.content as TdApi.MessageSticker).sticker.emoji
                is TdApi.MessagePhoto -> {
                    val msg = (message.content as TdApi.MessagePhoto)
                    val photoSize = msg.photo.sizes.last()
                    width = photoSize.width
                    height = photoSize.height
                    remoteFileId = photoSize.photo.remote.id
                    remoteUniqueId = photoSize.photo.remote.uniqueId
                    fileId = photoSize.photo.id

                    textEntities.addAll(msg.caption.entities)

                    msg.caption.text
                }
                is TdApi.MessageVideo -> {
                    val msg = (message.content as TdApi.MessageVideo)
                    width = msg.video.width
                    height = msg.video.height
                    duration = msg.video.duration
                    fileName = msg.video.fileName
                    remoteFileId = msg.video.video.remote.id
                    remoteUniqueId = msg.video.video.remote.uniqueId
                    fileId = msg.video.video.id

                    textEntities.addAll(msg.caption.entities)

                    msg.caption.text
                }
                is TdApi.MessageVideoNote -> {
                    val msg = (message.content as TdApi.MessageVideoNote)
                    duration = msg.videoNote.duration
                    remoteFileId = msg.videoNote.video.remote.id
                    remoteUniqueId = msg.videoNote.video.remote.uniqueId
                    fileId = msg.videoNote.video.id

                    "<video note without caption>"
                }
                is TdApi.MessageDocument -> {
                    val msg = (message.content as TdApi.MessageDocument)
                    fileName = msg.document.fileName
                    remoteFileId = msg.document.document.remote.id
                    remoteUniqueId = msg.document.document.remote.uniqueId
                    fileId = msg.document.document.id

                    textEntities.addAll(msg.caption.entities)

                    msg.caption.text
                }
                is TdApi.MessageAudio -> {
                    val msg = (message.content as TdApi.MessageAudio)
                    fileName = msg.audio.fileName
                    duration = msg.audio.duration
                    remoteFileId = msg.audio.audio.remote.id
                    remoteUniqueId = msg.audio.audio.remote.uniqueId
                    fileId = msg.audio.audio.id

                    textEntities.addAll(msg.caption.entities)

                    msg.caption.text
                }
                is TdApi.MessageVoiceNote -> {
                    val msg = (message.content as TdApi.MessageVoiceNote)
                    duration = msg.voiceNote.duration
                    remoteFileId = msg.voiceNote.voice.remote.id
                    remoteUniqueId = msg.voiceNote.voice.remote.uniqueId
                    fileId = msg.voiceNote.voice.id

                    textEntities.addAll(msg.caption.entities)

                    msg.caption.text
                }
                is TdApi.MessagePoll -> {
                    val msg = (message.content as TdApi.MessagePoll)
                    "${msg.poll.question}\n${
                        msg.poll.options.joinToString {
                            "\n- ${it.text}"
                        }
                    }\n\n${msg.poll.totalVoterCount} votes (${if (msg.poll.isAnonymous) "anonymous" else "public"})"
                }
                is TdApi.MessageVideoChatScheduled,
                is TdApi.MessageInviteVideoChatParticipants,
                is TdApi.MessageDice,
                is TdApi.MessageCustomServiceAction,
                is TdApi.MessageLocation,
                is TdApi.MessageSticker,
                is TdApi.MessageAnimatedEmoji,
                is TdApi.MessageChatJoinByLink,
                is TdApi.MessageChatSetTtl,
                is TdApi.MessageChatChangeTitle,
                is TdApi.MessageChatChangePhoto,
                is TdApi.MessageChatDeletePhoto,
                is TdApi.MessageChatDeleteMember,
                is TdApi.MessageVenue,
                is TdApi.MessageUnsupported,
                is TdApi.MessageChatAddMembers,
                is TdApi.MessagePinMessage,
                is TdApi.MessageSupergroupChatCreate,
                is TdApi.MessageVideoChatEnded,
                is TdApi.MessageVideoChatStarted,
                is TdApi.MessageChatUpgradeFrom -> {
                    // ignore
                    return@forEach
                }

                else -> {
                    log.warn("Unhandled message type: ${message.content.javaClass.simpleName}: ${message.content}")
                    "Not Implemented"
                }
            }

            var originChatId = 0L
            var originMsgId = 0L
            if (message.forwardInfo != null && message.forwardInfo.origin is TdApi.MessageForwardOriginChannel) {
                val casted = (message.forwardInfo.origin as TdApi.MessageForwardOriginChannel)
                originChatId = casted.chatId
                originMsgId = casted.messageId
            }

            val msgEntity = Message(telegramId = message.id,
                telegramChatId = chatId,
                telegramAuthorId = senderId,
                date = msgTimestamp,
                isForwarded = message.forwardInfo != null,
                forwardOriginChatId = originChatId,
                forwardOriginMessageId = originMsgId,
                textContent = content,
                messageType = message.content.javaClass.simpleName,
                fileId = fileId,
                remoteFileId = remoteFileId,
                fileName = fileName,
                duration = duration,
                width = width,
                height = height,
                remoteUniqueId = remoteUniqueId,
                textEntities = textEntities.map {
                    Message.TextEntity(
                        it.type.javaClass.simpleName, it.offset, it.length
                    )
                })

            val processedUrls = mutableSetOf<String>()
            textEntities.forEach {
                val url = when (it.type) {
                    is TdApi.TextEntityTypeUrl -> content.substring(it.offset, it.offset + it.length)
                    is TdApi.TextEntityTypeTextUrl -> {
                        val url = (it.type as TdApi.TextEntityTypeTextUrl).url
                        // add hidden URL to message content
                        msgEntity.textContent += "\n\n$url"
                        url
                    }
                    is TdApi.TextEntityTypeMention ->
                        "https://t.me/${content.substring(it.offset, it.offset + it.length)}"

                    else -> return@forEach
                }

                messageLinkHelper.createMessageLink(chatId, message.id, url)

                val matchResult =
                    Regex("https?:\\/\\/t\\.me\\/(?!iv\\?url)(?:joinchat\\/|\\+|([^?/\\s]*)\\/?)([^?]*)").matchEntire(
                        url
                    )
                if (matchResult != null && !processedUrls.contains(matchResult.groupValues.first())) {
                    val handle = matchResult.groupValues[1].replaceFirst("@", "")

                    if (!handle.isNullOrBlank()) {
                        jobHelper.createJob(
                            JobInfo(
                                type = JobTypes.GetGroupInfoByHandle,
                                param1 = handle,
                                depth = depth + 1,
                                createdByClass = GetMessageHistoryJob::class.qualifiedName!!
                            )
                        )
                        processedUrls.add(matchResult.groupValues.first())
                    } else {
                        jobHelper.createJob(
                            JobInfo(
                                type = JobTypes.FollowInviteLink,
                                param1 = matchResult.groupValues.first(),
                                depth = depth + 1,
                                createdByClass = GetMessageHistoryJob::class.qualifiedName!!
                            )
                        )
                    }
                }
            }

            // trim textcontent
            msgEntity.textContent = msgEntity.textContent.substring(0, min(content.length, 8192))

            msgEntity.persist()

            if (!forwardedGroupIds.contains(originChatId) && msgEntity.isForwarded && originChatId != 0L && groupRepo.count(
                    "telegramId=?1", originChatId
                ) == 0L
            ) {
                jobHelper.createJob(
                    JobInfo(
                        type = JobTypes.GetGroupInfo,
                        param1 = "$originChatId",
                        depth = depth + 1,
                        createdByClass = GetMessageHistoryJob::class.qualifiedName!!
                    )
                )
            }
            forwardedGroupIds.add(originChatId)
        }
    }
}