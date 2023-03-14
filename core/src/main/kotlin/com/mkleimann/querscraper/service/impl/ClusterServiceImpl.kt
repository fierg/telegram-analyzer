package com.mkleimann.querscraper.service.impl

import com.google.gson.Gson
import com.mkleimann.querscraper.repo.GraphEdgeRepository
import com.mkleimann.querscraper.service.ClusterService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jboss.logging.Logger
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.control.ActivateRequestContext
import javax.inject.Inject
import javax.transaction.Transactional

@ApplicationScoped
class ClusterServiceImpl : ClusterService {

    @Inject
    lateinit var log: Logger

    @Inject
    lateinit var graphEdgeRepository: GraphEdgeRepository

    override fun readCluster(filename: String): Map<Long, Int> {
        val outString = this::class.java.getResourceAsStream("/gclu/$filename.out")?.bufferedReader()?.readLines()!!
        val mapString = this::class.java.getResourceAsStream("/gclu/$filename.map")?.bufferedReader()?.readText()!!

        val nodeMap = Gson().fromJson(mapString, Map::class.java)
        val reversed = nodeMap.entries.associate { (k, v) -> (v as Double).toInt() to k }

        val cluster = mutableMapOf<Long, Int>()

        outString.forEachIndexed { i, line ->
            val index = i - 5
            if (index > 4) {
                cluster[(reversed[index] as String).toLong()] = line.toInt()
            }
        }

        updateClusterIds(cluster)

        return cluster

    }

    @ActivateRequestContext
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun updateClusterIds(cluster: MutableMap<Long, Int>) {
        try {
            cluster.forEach { (t, u) ->
                graphEdgeRepository.find("sourceid", t).list().forEach { edge ->
                    edge.clusterId = u
                    edge.persist()
                }
            }
            log.info("Updated cluster IDs")
        } catch (ex: Throwable) {
            log.error(ex)
        }

    }

    override fun exportGraph(
        minConnectionsCount: Int,
        minMemberCount: Int,
        minMessageCount: Int,
        minLinkStrength: Float,
        result: Map<String, Set<Map<String, Any>>>
    ): String {

        val mappedIDs = mutableMapOf<Long, Int>()
        result["nodes"]!!.forEachIndexed { index, node ->
            mappedIDs[node["id"] as Long] = index
        }

        val gson = Gson()

        File("graph_c${minConnectionsCount}_me${minMessageCount}_ms${minMessageCount}_l${(minLinkStrength * 100).toInt()}.map").bufferedWriter()
            .use { out ->
                out.write(gson.toJson(mappedIDs).toString())
            }

        val sb = StringBuilder()
        var numConnections = 0

        result["nodes"]!!.forEach { node ->
            val linkMap = mutableMapOf<Int, Int>()
            val links = (node["links"] as ArrayList<LinkedHashMap<String, Any>>)
            links.forEach {
                if (node["id"] != it["target"])
                    linkMap.putIfAbsent(mappedIDs[it["target"]]!!, 0)
            }
            links.forEach {
                if (node["id"] != it["target"])
                    linkMap[mappedIDs[it["target"]]!!] =
                        it["connections"] as Int + linkMap.getValue(mappedIDs[it["target"]]!!)
            }

            sb.append("${mappedIDs[node["id"]]} ")
            sb.append("${linkMap.size} ")
            numConnections += linkMap.size
            val entries = linkMap.entries.toList()
            entries.forEach {
                sb.append("${it.key} ")
            }
            entries.forEach {
                sb.append("${it.value}.000000 ")
            }
            sb.append("\n")
        }

        File("graph_c${minConnectionsCount}_me${minMessageCount}_ms${minMessageCount}_l${(minLinkStrength * 100).toInt()}.gclu").bufferedWriter()
            .use { out ->
                out.write(sb.toString())
            }
        log.info("Persisted gclu graph with ${result["nodes"]!!.size} nodes and ${numConnections / result["nodes"]!!.size.toFloat()} avg neighbors.")

        return sb.toString()
    }

    override fun generateCluster(filename: String): String {
        //TODO fix me
        //neither running the entrypoint nor starting ./gclu from bash seems to work, dont know why

        //data/graph_c30_me100_ms100_l2.gclu
        //val response = "docker run --entrypoint bash gclu:0.1 ./gclu -o $filename.cluster $filename --algo m -K 10 -R 5".runCommand(File("."))
        val response = "docker run -v $(pwd):/home/git/gclu/data gclu:0.1 -o data/$filename.cluster data/$filename --algo m -K 10 -R 5".runCommand(File("."))

        return response
    }


    private fun String.runCommand(workingDir: File): String {
        return try {
            val parts = this.split("\\s".toRegex())
            val proc = ProcessBuilder(*parts.toTypedArray())
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            proc.waitFor(60, TimeUnit.MINUTES)
            proc.inputStream.bufferedReader().readText()
        } catch (e: IOException) {
            e.printStackTrace()
            e.message!!
        }
    }
}