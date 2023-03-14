package com.mkleimann.querscraper.controller

import com.mkleimann.querscraper.const.CacheNames
import com.mkleimann.querscraper.const.QueryNames
import com.mkleimann.querscraper.model.entity.GraphEdge
import com.mkleimann.querscraper.model.entity.GroupChat
import com.mkleimann.querscraper.model.entity.Message
import com.mkleimann.querscraper.model.enum.JobState
import com.mkleimann.querscraper.model.enum.JobTypes
import com.mkleimann.querscraper.repo.GroupRepository
import com.mkleimann.querscraper.repo.JobInfoRepository
import com.mkleimann.querscraper.repo.MessageRepository
import com.mkleimann.querscraper.service.Cache
import com.mkleimann.querscraper.service.ClusterService
import com.mkleimann.querscraper.service.QueryService
import com.mkleimann.querscraper.service.TelegramService
import it.tdlight.jni.TdApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.microprofile.context.ManagedExecutor
import org.hibernate.search.engine.search.aggregation.AggregationKey
import org.hibernate.search.engine.search.projection.dsl.SearchProjectionFactory
import org.hibernate.search.engine.search.query.SearchResult
import org.hibernate.search.mapper.orm.session.SearchSession
import org.jboss.logging.Logger
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.transaction.Transactional
import javax.ws.rs.*
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("")
class Controller {

    @Inject
    lateinit var telegram: TelegramService

    @Inject
    lateinit var groupRepo: GroupRepository

    @Inject
    lateinit var msgRepo: MessageRepository

    @Inject
    lateinit var queryService: QueryService

    @Inject
    lateinit var searchSession: SearchSession

    @Inject
    lateinit var jobRepo: JobInfoRepository

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var executor: ManagedExecutor

    @Inject
    lateinit var cache: Cache

    @Inject
    lateinit var clusterService: ClusterService

    @Path("/groups/")
    @GET
    fun getTopGroupsByFollowers(): Response? {
        val results =
            queryService.getNamedQuery(name = QueryNames.LIST_GROUPS_BY_FOLLOWERS, size = 100).resultList.forEach {
                mapOf(
                    "id" to it[0],
                    "title" to it[1],
                    "description" to it[2],
                    "memberCount" to it[3],
                    "messageCount" to it[4]
                )
            }
        return Response.ok(results).build()
    }

    @Path("/groups/search")
    @GET
    fun searchGroups(
        @QueryParam("q") query: String?, @QueryParam("offset") offset: Int = 0, @QueryParam("limit") limit: Int = 20
    ): Response? {
        val searchResult = searchSession.search(
            GroupChat::class.java
        ).select { f ->
            f.composite(
                f.field("telegramId", Long::class.javaObjectType),
                f.field("title", String::class.java),
                f.field("description", String::class.java),
                f.field("memberCount", Int::class.javaObjectType),
            )
        }.where {
            if (query.isNullOrBlank()) it.match().field("deleted").matching(false)
            else it.bool().must(it.match().field("deleted").matching(false)).must(
                it.simpleQueryString().field("title").field("description")//.boost(0.5f)
                    .matching(query)
            )
        }.sort { s ->
            s.field("memberCount").desc()
        }.fetch(offset, limit)

        val result = mapOf("count" to searchResult.total().hitCount(),
            "took" to searchResult.took().toMillis(),
            "results" to searchResult.hits().map {
                it as List<Any>
                mapOf(
                    "id" to it[0],
                    "title" to it[1],
                    "description" to it[2],
                    "memberCount" to it[3],
                    "messageCount" to msgRepo.count("telegramChatId=?1", it[0])
                )
            })

        return Response.ok(result).build()
    }

    @Path("/exportGraph")
    @GET
    fun getChordlink(
        @QueryParam("connections") minConnectionsCount: Int,
        @QueryParam("members") minMemberCount: Int,
        @QueryParam("messages") minMessageCount: Int,
        @QueryParam("linkstrength") minLinkStrength: Float
    ): Response {
        val result = mapOf<String, MutableSet<MutableMap<String, Any>>>(
            "nodes" to mutableSetOf(),
            "links" to mutableSetOf(),
        )

        queryService.getNamedQuery<GraphEdge>(
            QueryNames.LIST_GRAPHEDGES, GraphEdge::class.java, mapOf(
                "minMessageCount" to minMessageCount,
                "minConnectionCount" to minConnectionsCount,
                "minMemberCount" to minMemberCount,
            )
        ).resultStream.forEach {
            val linkStrength = (it.connections / it.targetForwardMessageCount.toFloat())
            if (linkStrength < minLinkStrength) { // cut weak links
                return@forEach
            }


            result["nodes"]!!.add(
                mutableMapOf(
                    "val" to it.sourceMemberCount,
                    "originalContent" to it.sourceOriginalMessageCount / (it.sourceOriginalMessageCount + it.sourceForwardMessageCount).toFloat(),
                    "id" to it.sourceId,
                    "name" to it.sourceName

                )
            )
            result["nodes"]!!.add(
                mutableMapOf(
                    "val" to it.targetMemberCount,
                    "originalContent" to it.targetOriginalMessageCount / (it.targetOriginalMessageCount + it.targetForwardMessageCount).toFloat(),
                    "id" to it.targetId,
                    "name" to it.targetName,
                )
            )
            result["links"]!!.add(
                mutableMapOf(
                    "source" to it.sourceId,
                    "target" to it.targetId,
                    "value" to linkStrength,
                    "connections" to it.connections
                )
            )
        }

        result["nodes"]!!.forEach { node ->
            val links = result["links"]!!.filter { link -> link["target"] == node["id"] || link["source"] == node["id"] }
            node["neighbors"] = links.map { if (it["source"] == node["id"]) it["target"] else it["source"] }
            node["links"] = links
        }

        val export = clusterService.exportGraph(minConnectionsCount, minMemberCount, minMessageCount, minLinkStrength, result)

        return Response.ok(export).build()
    }

    @Path("/calcCluster")
    @GET
    fun getCluster(@QueryParam("filename") filename: String) : Response {
        val result = clusterService.generateCluster(filename)
        return Response.ok(result).build()
    }

    @Path("/readCluster")
    @GET
    fun readCluster(@QueryParam("filename") filename: String) : Response {
        val cluster = clusterService.readCluster(filename)
        return Response.ok(cluster).build()
    }

    @Path("/graph")
    @GET
    // @CacheResult(cacheName = "graph")
    fun getGraph(
        @QueryParam("connections") minConnectionsCount: Int,
        @QueryParam("members") minMemberCount: Int,
        @QueryParam("messages") minMessageCount: Int,
        @QueryParam("linkstrength") minLinkStrength: Float
    ): Response {
        val result = mapOf<String, MutableSet<MutableMap<String, Any>>>(
            "nodes" to mutableSetOf(),
            "links" to mutableSetOf(),
        )

        queryService.getNamedQuery<GraphEdge>(
            QueryNames.LIST_GRAPHEDGES, GraphEdge::class.java, mapOf(
                "minMessageCount" to minMessageCount,
                "minConnectionCount" to minConnectionsCount,
                "minMemberCount" to minMemberCount,
            )
        ).resultStream.forEach {
            val linkStrength = (it.connections / it.targetForwardMessageCount.toFloat())
            if (linkStrength < minLinkStrength) { // cut weak links
                return@forEach
            }

            val clusterID = (if (it.clusterId == null) -1 else it.clusterId)!!

            result["nodes"]!!.add(
                mutableMapOf(
                    "val" to it.sourceMemberCount,
                    "originalContent" to it.sourceOriginalMessageCount / (it.sourceOriginalMessageCount + it.sourceForwardMessageCount).toFloat(),
                    "id" to it.sourceId,
                    "name" to it.sourceName,
                    "cluster" to clusterID
                )
            )
            result["nodes"]!!.add(
                mutableMapOf(
                    "val" to it.targetMemberCount,
                    "originalContent" to it.targetOriginalMessageCount / (it.targetOriginalMessageCount + it.targetForwardMessageCount).toFloat(),
                    "id" to it.targetId,
                    "name" to it.targetName,
                )
            )
            result["links"]!!.add(
                mutableMapOf(
                    "source" to it.sourceId,
                    "target" to it.targetId,
                    "value" to linkStrength,
                    "connections" to it.connections
                )
            )
        }

        result["nodes"]!!.forEach { node ->
            val links =
                result["links"]!!.filter { link -> link["target"] == node["id"] || link["source"] == node["id"] }
            node["neighbors"] = links.map { if (it["source"] == node["id"]) it["target"] else it["source"] }
            node["links"] = links
        }

        return Response.ok(result).build()
    }

    @Path("/stats/messagetypes")
    @GET
    fun messageTypes(): Response? {
        return Response.ok(cache.get(CacheNames.MESSAGES_BY_TYPE) {
            val audioMessageTypes = setOf("MessageAudio", "MessageVoiceNote")
            val videoMessageTypes = setOf("MessageVideo", "MessageVideoNote")
            val mainMessageTypes = setOf("MessageDocument", "MessagePhoto", "MessageText")

            val messageTypes = mutableMapOf<String, Long>()
            queryService.getNamedQuery(QueryNames.GROUP_MESSAGES_BY_TYPE).resultList.forEach { row ->
                val type = row[0] as String
                if (audioMessageTypes.contains(type)) {
                    messageTypes["audio"] = messageTypes.computeIfAbsent("audio") { 0L } + row[1] as Long
                } else if (videoMessageTypes.contains(type)) {
                    messageTypes["video"] = messageTypes.computeIfAbsent("video") { 0L } + row[1] as Long
                } else if (mainMessageTypes.contains(type)) {
                    val shortType = type.substring(7).lowercase()
                    messageTypes[shortType] = messageTypes.computeIfAbsent(shortType) { 0L } + row[1] as Long
                } else {
                    messageTypes["other"] = messageTypes.computeIfAbsent("other") { 0L } + row[1] as Long
                }
            }

            messageTypes
        }).build()
    }

    @Path("/stats/messageforwards")
    @GET
    fun messageForwards(): Response? {
        return Response.ok(cache.get(CacheNames.MESSAGES_BY_FORWARDS) {
            queryService.getNamedQuery(QueryNames.GROUP_MESSAGES_BY_FORWARDED).resultList.map { row ->
                mapOf(
                    "forwarded" to row[0] as Boolean, "count" to row[1] as Long
                )
            }
        }).build()
    }

    @Path("/messages/groupbydate")
    @GET
    fun messagesGroupByDate(): Response? {
        return Response.ok(cache.get(CacheNames.MESSAGES_BY_DAY) {
            queryService.getNamedQuery(QueryNames.GROUP_MESSAGES_BY_DATE).resultList.map { row ->
                mapOf(
                    "date" to row[0], "count" to row[1]
                )
            }
        }).build()
    }

    @Path("/messages/search")
    @GET
    fun searchMessages(@QueryParam("q") query: String?, @QueryParam("offset") offset: Int = 0, @QueryParam("limit") limit: Int = 20): Response? {
        if (query.isNullOrBlank()) {
            throw BadRequestException("Empty query not allowed!")
        }

        val searchResult = searchSession.search(
            Message::class.java
        ).select(::messageSearchComposite).where {
            it.match().fields("textContent").matching("$query").fuzzy(1)
        }.fetch(offset, limit)

        val result = messageSearchResultToMap(searchResult)

        return Response.ok(result).build()
    }

    private fun messageSearchResultToMap(searchResult: SearchResult<out Any>): Map<String, Any> {
        val result = mapOf("count" to searchResult.total().hitCount(),
            "took" to searchResult.took().toMillis(),
            "results" to searchResult.hits().map {
                it as List<*>
                mapOf(
                    "textContent" to it[0],
                    "date" to it[1],
                    "telegramChatId" to it[2],
                    "telegramAuthorId" to it[3],
                    "isForwarded" to it[4],
                    "messageType" to it[5],
                    "id" to it[6],
                    //"author" to accountRepo.find("telegramAuthorId=?1", it[3]),
                    "width" to it[7],
                    "height" to it[8],
                    "duration" to it[9],
                )
            })
        return result
    }

    @GET
    @Path("/messages/tagcloud")
    fun tagCloud(): Response {
        val result = searchSession.search(Message::class.java).where {
            it.matchAll()
        }.aggregation(AggregationKey.of("content_agg")) {
            it.terms().field("message_tags", String::class.java)
        }.fetchAll()

        return Response.ok(result).build()
    }


    @Path("/groups/count")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun countGroups(): Response? {
        return Response.ok(cache.get(CacheNames.GROUPS_COUNT) {
            groupRepo.count()
        }).build()
    }

    @Path("/groups/{id}")
    @GET
    fun findByTelegramId(@PathParam("id") id: Long): Response? {
        return Response.ok(groupRepo.find("telegramId=?1", id).singleResult()).build()
    }

    @Path("/groups/{id}/messages")
    @GET
    fun getMessagesForGroup(
        @PathParam("id") groupId: Long,
        @QueryParam("q") query: String?,
        @QueryParam("offset") offset: Int = 0,
        @QueryParam("limit") limit: Int = 20
    ): Response? {
        val searchResult = searchSession.search(
            Message::class.java
        ).select(::messageSearchComposite).where {
            val bool = it.bool().filter { f -> f.match().field("telegramChatId").matching(groupId) }
            if (!query.isNullOrBlank()) {
                bool.must { f -> f.simpleQueryString().field("textContent").matching(query) }
            }
            bool
        }.sort { it.field("date").desc() }.fetch(offset, limit)

        val result = messageSearchResultToMap(searchResult)

        return Response.ok(result).build()
    }

    private fun messageSearchComposite(f: SearchProjectionFactory<*, *>) = f.composite(
        f.field("textContent", String::class.java),
        f.field("date", Instant::class.java),
        f.field("telegramChatId", Long::class.javaObjectType),
        f.field("telegramAuthorId", Long::class.javaObjectType),
        f.field("isForwarded", Boolean::class.javaObjectType),
        f.field("messageType", String::class.java),
        f.field("messageId", Long::class.javaObjectType),
        f.field("width", Int::class.javaObjectType),
        f.field("height", Int::class.javaObjectType),
        f.field("duration", Int::class.javaObjectType),
    )

    @Path("/groups/{id}/messageforwards")
    @GET
    fun messageForwardsByGroup(@PathParam("id") groupId: Long): Response? {
        val results = queryService.getNamedQuery(
            QueryNames.GROUP_MESSAGES_BY_FORWARDED_FILTERED_BY_CHATID, mapOf("chatId" to groupId)
        ).resultList.map { row ->
            mapOf(
                "forwarded" to row[0] as Boolean, "count" to row[1] as Long
            )
        }

        return Response.ok(results).build()
    }

    @Path("/groups/{id}/messagesbydate")
    @GET
    fun messagesGroupByDate(@PathParam("id") groupId: Long): Response? {
        val data = queryService.getNamedQuery(
            QueryNames.GROUP_MESSAGES_BY_DATE_FILTERED_BY_CHATID, mapOf("chatId" to groupId)
        ).resultList.associate { row ->
            row[0] to row[1]
        }

        val result = mutableListOf<Map<String, Any>>()

        var currentDay = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"))
        val lastDay = ZonedDateTime.now().withHour(0).withMinute(0)

        while (currentDay.isBefore(lastDay)) {
            val dateKey = "${currentDay.year}-${
                (currentDay.monthValue).toString().padStart(2, '0')
            }-${(currentDay.dayOfMonth).toString().padStart(2, '0')}"
            result.add(
                mapOf(
                    "date" to dateKey, "count" to data.getOrElse(dateKey) { 0 }!!
                )
            )
            currentDay = currentDay.plusDays(1L)
        }

        return Response.ok(result).build()
    }

    @Path("/groups/{id}/messagetypes")
    @GET
    fun messageTypesByGroup(@PathParam("id") groupId: Long): Response? {
        val audioMessageTypes = setOf("MessageAudio", "MessageVoiceNote")
        val videoMessageTypes = setOf("MessageVideo", "MessageVideoNote")
        val mainMessageTypes = setOf("MessageDocument", "MessagePhoto", "MessageText")

        val messageTypes = mutableMapOf<String, Long>()
        queryService.getNamedQuery(
            QueryNames.GROUP_MESSAGES_BY_TYPE_FILTERED_BY_CHATID, mapOf("chatId" to groupId)
        ).resultList.forEach { row ->
            val type = row[0] as String
            if (audioMessageTypes.contains(type)) {
                messageTypes["audio"] = messageTypes.computeIfAbsent("audio") { 0L } + row[1] as Long
            } else if (videoMessageTypes.contains(type)) {
                messageTypes["video"] = messageTypes.computeIfAbsent("video") { 0L } + row[1] as Long
            } else if (mainMessageTypes.contains(type)) {
                val shortType = type.substring(7).lowercase()
                messageTypes[shortType] = messageTypes.computeIfAbsent(shortType) { 0L } + row[1] as Long
            } else {
                messageTypes["other"] = messageTypes.computeIfAbsent("other") { 0L } + row[1] as Long
            }
        }

        return Response.ok(messageTypes).build()
    }

    @Path("/groups/{id}/domains")
    @GET
    fun groupTopDomains(@PathParam("id") groupId: Long): Response? {
        val results = queryService.getNamedQuery(
            name = QueryNames.TOP_DOMAINS_BY_GROUP, params = mapOf("chatId" to groupId)
        ).resultList.map {
            mapOf(
                "count" to it[0], "domain" to it[1]
            )
        }

        return Response.ok(results).build()
    }

    @Path("/messages/count")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun countMessages(): Response =
        Response.ok(cache.get(CacheNames.MESSAGES_COUNT) {
            msgRepo.count()
        }).build()


    @Path("/groups/{chatId}/messages/{msgId}/downloadfile")
    @GET
    @Produces(MediaType.MULTIPART_FORM_DATA)
    fun downloadFile(
        @Suspended asyncResponse: AsyncResponse, @PathParam("chatId") chatId: Long, @PathParam("msgId") msgId: Long
    ) {
        val message = msgRepo.find(
            "telegramChatId = :chatId and telegramId = :msgId", mapOf("chatId" to chatId, "msgId" to msgId)
        ).firstResult() ?: throw NotFoundException("Message '$chatId-$msgId' not found!")

        if (message.remoteFileId == null) {
            throw NotFoundException("Message '$chatId-$msgId' has no downloadable contents!")
        }

        if (message.fileId == null) {
            val tgMsg = runBlocking { telegram.getMessage(chatId, msgId) }
            message.fileId = when (tgMsg.content) {
                is TdApi.MessagePhoto -> (tgMsg.content as TdApi.MessagePhoto).photo.sizes.last().photo.id
                is TdApi.MessageVideo -> (tgMsg.content as TdApi.MessageVideo).video.video.id
                is TdApi.MessageVideoNote -> (tgMsg.content as TdApi.MessageVideoNote).videoNote.video.id
                is TdApi.MessageDocument -> (tgMsg.content as TdApi.MessageDocument).document.document.id
                is TdApi.MessageVoiceNote -> (tgMsg.content as TdApi.MessageVoiceNote).voiceNote.voice.id
                is TdApi.MessageAudio -> (tgMsg.content as TdApi.MessageAudio).audio.audio.id
                else -> throw NotFoundException("Message type '${tgMsg.content::class.simpleName}' is not downloadable!")
            }
        }

        executor.execute {
            try {

                val fileResult = runBlocking { telegram.downloadFile(message.fileId!!) }

                val localFile = File(fileResult.local.path)

                val response = Response.ok(localFile)
                    .header("Content-Disposition", "attachment; filename=\"${message.fileName ?: localFile.name}\"")
                    .build()

                localFile.deleteOnExit()
                GlobalScope.launch {
                    delay(1000 * 60 * 5)
                    localFile.delete()
                }

                asyncResponse.resume(response)

            } catch (ex: Throwable) {
                asyncResponse.cancel()
            }
        }
    }

    @POST
    @Path("/groups/{chatId}/delete")
    @Transactional
    fun deleteGroup(@PathParam("chatId") chatId: Long): Response {
        log.info("Deleting Chat: $chatId")
        val result = groupRepo.update(
            "set deleted = true where telegramId = :chatId", mapOf("chatId" to chatId)
        )

        jobRepo.update(
            "set state=:cancelled where type in (:types) and param1=:chatId and state=:new", mapOf(
                "chatId" to chatId.toString(),
                "cancelled" to JobState.CANCELLED,
                "new" to JobState.NEW,
                "types" to listOf(
                    JobTypes.GetGroupInfo, JobTypes.GetMessageHistory
                )
            )
        )

        if (result == 0) throw NotFoundException("Could not delete chat '$chatId', not found!")
        else return Response.ok().build()
    }
}

