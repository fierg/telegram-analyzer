package com.mkleimann.querscraper.service.impl

import com.mkleimann.querscraper.service.Cache
import io.quarkus.scheduler.Scheduled
import org.jboss.logging.Logger
import java.util.concurrent.ConcurrentHashMap
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class CacheImpl : Cache {

    @Inject
    lateinit var log: Logger

    private val cacheInvalidating = ConcurrentHashMap<String, Boolean>()

    private val cache = ConcurrentHashMap<String, Triple<Any?, Long, () -> Any?>>()

    override fun get(key: String, refreshMs: Long, valueProvider: () -> Any?): Any? {
        val now = System.currentTimeMillis()
        val result = cache.getOrPut(key) {
            Triple(valueProvider(), now, valueProvider)
        }

        if (cacheInvalidating[key] != true && (now - result.second > refreshMs)) {
            cacheInvalidating[key] = true
        }

        return result.first
    }

    @Scheduled(delayed = "1m", every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun refreshCaches() {
        cacheInvalidating.filterValues { it }.keys.forEach {
            log.info("Invalidating Cache [${it}]")
            val remappingFunction = cache[it]!!.third
            cache[it] = Triple(remappingFunction.invoke(), System.currentTimeMillis(), remappingFunction)
            cacheInvalidating[it] = false
        }
    }

}