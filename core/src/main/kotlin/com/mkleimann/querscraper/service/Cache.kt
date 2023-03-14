package com.mkleimann.querscraper.service

interface Cache {

    fun get(key: String, refreshMs: Long = 1000L * 60L * 30L, valueProvider: () -> Any?): Any?
}