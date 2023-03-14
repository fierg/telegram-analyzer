package com.mkleimann.querscraper.service

interface MessageLinkService {

    fun createMessageLink(chatId: Long, messageId: Long, url: String)

}