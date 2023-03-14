package com.mkleimann.querscraper.job.impl

import com.mkleimann.querscraper.const.QueryNames
import com.mkleimann.querscraper.job.RecurringJob
import com.mkleimann.querscraper.model.entity.GraphEdge
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.GraphEdgeRepository
import com.mkleimann.querscraper.service.QueryService
import io.quarkus.cache.CacheInvalidate
import org.jboss.logging.Logger
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.enterprise.context.control.ActivateRequestContext
import javax.inject.Inject
import javax.transaction.Transactional


@Dependent
class CalculateGraphJob : RecurringJob {

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var queryService: QueryService

    @Inject
    lateinit var graphRepo: GraphEdgeRepository

    override fun type() = JobTypes.CalculateGraphJob

    @ActivateRequestContext
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    override fun execute() {
        try {
            val messageCount: MutableMap<Long, MutableMap<Boolean, Long>> = mutableMapOf()

            queryService.getNamedQuery(QueryNames.LIST_GROUPS_WITH_MSGCOUNT).resultList.forEach { row ->
                messageCount.computeIfAbsent(row[0] as Long) {
                    mutableMapOf()
                }[row[2] as Boolean] = row[1] as Long
            }

            graphRepo.deleteAll()

            queryService.getNamedQuery(
                name = QueryNames.CONNECTION_GRAPH, params = mapOf(
                    "minMemberCount" to 100,
                )
            ).resultList.chunked(500).forEach { chunk ->
                persistChunk(chunk, messageCount)
            }

            log.info("Persisted new graph")
        } catch (ex: Throwable) {
            log.error(ex)
        }
    }

    @Transactional(Transactional.TxType.REQUIRED)
    fun persistChunk(chunk: List<Array<Any?>>, messageCount: MutableMap<Long, MutableMap<Boolean, Long>>) {
        chunk.forEach {
            val targetId = it[1] as Long
            val sourceId = it[3] as Long
            val targetForwardMsgCount = messageCount[targetId]?.get(true) ?: 0
            val targetOriginalMsgCount = messageCount[targetId]?.get(false) ?: 0
            val sourceForwardMsgCount = messageCount[sourceId]?.get(true) ?: 0
            val sourceOriginalMsgCount = messageCount[sourceId]?.get(false) ?: 0
            if ((targetForwardMsgCount + targetOriginalMsgCount) > 100L
                && (sourceForwardMsgCount + sourceOriginalMsgCount) > 100L
            ) {
                val edge = GraphEdge(
                    connections = (it[0] as Long).toInt(),
                    targetId = targetId,
                    targetName = it[2] as String,
                    sourceId = sourceId,
                    sourceName = it[4] as String,
                    targetMemberCount = it[5] as Int,
                    sourceMemberCount = it[6] as Int,
                    targetForwardMessageCount = targetForwardMsgCount.toInt(),
                    targetOriginalMessageCount = targetOriginalMsgCount.toInt(),
                    sourceForwardMessageCount = sourceForwardMsgCount.toInt(),
                    sourceOriginalMessageCount = sourceOriginalMsgCount.toInt(),
                )
                edge.persist()
            }
        }
    }


}