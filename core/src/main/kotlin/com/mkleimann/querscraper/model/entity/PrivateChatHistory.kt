package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.model.enum.JobTypes
import javax.persistence.*

@Entity(name = "privateChatHistory")
open class PrivateChatHistory(

    @Column(name = "telegramChatId", nullable = false, unique = true)
    open var telegramChatId: Long,

) : EntityBase()