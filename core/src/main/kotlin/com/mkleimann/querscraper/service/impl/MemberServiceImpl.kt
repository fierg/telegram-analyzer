package com.mkleimann.querscraper.service.impl

import com.mkleimann.querscraper.const.QueryNames
import com.mkleimann.querscraper.model.entity.Account
import com.mkleimann.querscraper.model.entity.GroupMember
import com.mkleimann.querscraper.service.MemberService
import com.mkleimann.querscraper.service.QueryService
import com.mkleimann.querscraper.service.TelegramService
import it.tdlight.jni.TdApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jboss.logging.Logger
import java.time.Instant
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.transaction.Transactional

@ApplicationScoped
class MemberServiceImpl : MemberService {

    @Inject
    lateinit var queryService: QueryService

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var log: Logger

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    override fun persistBatch(batch: List<TdApi.ChatMember>, chatId: Long) {
        val userIds = batch.map { (it.memberId as TdApi.MessageSenderUser).userId }

        val persistedAccounts = queryService.getNamedQuery(
            name = QueryNames.USERS_BY_IDS,
            params = mapOf("ids" to userIds),
            resultClass = Long::class.javaObjectType
        ).resultList

        val persistedRelations = queryService.getNamedQuery(
            name = QueryNames.GROUPMEMBER_BY_IDS,
            params = mapOf("ids" to userIds, "chatId" to chatId),
            resultClass = Long::class.javaObjectType
        ).resultList

        batch.forEach {
            val userId = (it.memberId as TdApi.MessageSenderUser).userId

            if (!persistedRelations.contains(userId)) {
                val member = GroupMember(
                    telegramChatId = chatId,
                    telegramUserId = userId,
                    joinedAt = Instant.ofEpochSecond(it.joinedChatDate.toLong())
                )
                member.persist()
            }

            if (!persistedAccounts.contains(userId)) {
                val user = runBlocking { telegram.getUser(userId) }
                val userEntity = Account(
                    telegramId = userId,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    username = user.username,
                    //  bio = info.bio,
                    phoneNumber = user.phoneNumber,
                    isVerified = user.isVerified,
                    isScam = user.isScam,
                    isFake = user.isFake
                )

                userEntity.persist()

                runBlocking {
                    delay(10)
                }
            }
        }

        if (batch.isNotEmpty()) {
            log.info(
                "✅ Persisted members: ${batch.size}"
            )
        }
    }
}