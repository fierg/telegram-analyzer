package com.mkleimann.querscraper.service.impl

import com.mkleimann.querscraper.model.entity.MessageLink
import com.mkleimann.querscraper.repo.MessageLinkRepository
import com.mkleimann.querscraper.service.MessageLinkService
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class MessageLinkServiceImpl : MessageLinkService {

    val prevLinks = mutableSetOf<String>()

    val rgxDomain = Regex("\\b(?:https?://)?(?:www\\.|ww2\\.)?((?:[ëïàèìòùâêîôûéíñóçúäöüß\\w-]+\\.)+\\w+)\\b\\/?.*")

    @Inject
    lateinit var messageLinkRepo: MessageLinkRepository

    @Inject
    lateinit var log: Logger

    //@Transactional(Transactional.TxType.MANDATORY)
    override fun createMessageLink(chatId: Long, messageId: Long, _url: String) {
        val url = cleanUrl(_url)

        val key = "${chatId}-${messageId}-${url}"
        if (prevLinks.contains(key)) {
            return
        }

        prevLinks.add(key)

        if (messageLinkRepo.find(
                "chatId = :chatId and messageId = :messageId and url = :url",
                mapOf("chatId" to chatId, "messageId" to messageId, "url" to url)
            ).count() > 0
        ) {
            return
        }

        val match = rgxDomain.find(url)
        if (match != null) {
            val domain = when (match.groupValues[1]) {
                "youtu.be" -> "youtube.com"
                "fb.watch", "fb.com", "fb.me" -> "facebook.com"
                "de.m.wikipedia.org" -> "de.wikipedia.org"
                "en.m.wikipedia.org" -> "en.wikipedia.org"
                "deutsch.rt.com" -> "de.rt.com"
                else -> match.groupValues[1]
            }.removePrefix("m.")

            val msgLink = MessageLink(chatId, messageId, url, domain.lowercase())
            msgLink.persist()
        } else {
            log.error("ERROR: DOMAIN REGEX DID NOT MATCH: $url")
        }

    }

    private fun cleanUrl(rawUrl: String): String {
        val url = rawUrl.lowercase().removeSuffix("/")

        return with(url) {
            when {
                startsWith("http://www.paypal.me") -> "https://${url.substring(11)}"
                startsWith("https://www.paypal.me") -> "https://${url.substring(12)}"
                startsWith("paypal.me") -> "https://$url"
                startsWith("http://paypal.me") -> "https://${url.substring(7)}"
                startsWith("www.paypal.me") -> "https://${url.substring(4)}"
                startsWith(".paypal.me") -> "https://${url.substring(1)}"
                else -> url
            }
        }
    }
}