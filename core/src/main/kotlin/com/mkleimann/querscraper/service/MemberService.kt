package com.mkleimann.querscraper.service

import it.tdlight.jni.TdApi

interface MemberService {

    fun persistBatch(batch: List<TdApi.ChatMember>, chatId: Long)
}