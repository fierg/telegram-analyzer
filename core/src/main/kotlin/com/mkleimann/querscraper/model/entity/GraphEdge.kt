package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.const.QueryNames
import javax.persistence.*

@Entity(name = "graphEdges")
@Table(
    /*uniqueConstraints = [UniqueConstraint(
        name = "U_TYPE_STATE_PARAMS",
        columnNames = ["type", "state", "param1", "param2", "param3"]
    )]*/
)
@NamedQueries(
    value = [
        NamedQuery(
            name = QueryNames.LIST_GRAPHEDGES,
            query = "select e from graphEdges e " +
                    "where (e.targetForwardMessageCount + e.targetOriginalMessageCount) > :minMessageCount " +
                    "and (e.sourceForwardMessageCount + e.sourceOriginalMessageCount) > :minMessageCount " +
                    "and e.connections > :minConnectionCount " +
                    "and (e.targetMemberCount > :minMemberCount or e.targetMemberCount = 0) "
            //"and e.sourceMemberCount > :minMemberCount",
        )
    ]
)
open class GraphEdge(

    @Column(name = "connections", nullable = false)
    open var connections: Int,

    @Column(name = "targetId", nullable = false)
    open var targetId: Long,

    @Column(name = "targetName", nullable = false)
    open var targetName: String,

    @Column(name = "targetMemberCount", nullable = false)
    open var targetMemberCount: Int,

    @Column(name = "targetForwardMessageCount", nullable = false)
    open var targetForwardMessageCount: Int,

    @Column(name = "targetOriginalMessageCount", nullable = false)
    open var targetOriginalMessageCount: Int,

    @Column(name = "sourceId", nullable = false)
    open var sourceId: Long,

    @Column(name = "sourceName", nullable = false)
    open var sourceName: String,

    @Column(name = "sourceMemberCount", nullable = false)
    open var sourceMemberCount: Int,

    @Column(name = "sourceForwardMessageCount", nullable = false)
    open var sourceForwardMessageCount: Int,

    @Column(name = "sourceOriginalMessageCount", nullable = false)
    open var sourceOriginalMessageCount: Int,

    @Column(name = "clusterId", nullable = true)
    open var clusterId: Int? = null,

    ) : EntityBase()