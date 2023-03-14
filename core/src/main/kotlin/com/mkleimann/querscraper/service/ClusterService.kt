package com.mkleimann.querscraper.service

interface ClusterService {

    fun readCluster(filename: String): Map<Long, Int>
    fun exportGraph(
        minConnectionsCount: Int,
        minMemberCount: Int,
        minMessageCount: Int,
        minLinkStrength: Float,
        result: Map<String, Set<Map<String, Any>>>
    ): String

    fun generateCluster(filename: String): String
}