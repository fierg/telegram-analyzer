package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.const.QueryNames
import com.mkleimann.querscraper.model.enum.JobState
import com.mkleimann.querscraper.model.enum.JobTypes
import javax.persistence.*

@Entity(name = "jobInfo")
@Table(
    uniqueConstraints = [UniqueConstraint(
        name = "U_TYPE_PARAMS",
        columnNames = ["type", "param1", "param2", "param3", "param4"]
    )],
    indexes = [Index(name="IDX_JOB_TYPE_STATE", columnList="type,state")]
)
@NamedQueries(
    value = [
        NamedQuery(
            name = QueryNames.JOB_COUNT_BY_TYPE_AND_STATE,
            query = "select i.type, i.state, count(*) " +
                    "from jobInfo i " +
                    "where i.depth = null or i.depth < :maxDepth " +
                    "group by i.type, i.state"
        ),
        NamedQuery(
            name = QueryNames.JOB_COUNT_BY_TYPE_AND_PARAMS,
            query = "select count(*) " +
                    "from jobInfo i " +
                    "where type=:type " +
                    "and param1=:param1 " +
                    "and param2=:param2 " +
                    "and param3=:param3 " +
                    "and param4=:param4"
        ),
    ]
)
open class JobInfo(

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    open var type: JobTypes,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    open var state: JobState = JobState.NEW,

    @Column(name = "priority", nullable = false)
    open var priority: Int = 100,

    @Column(name = "param1", nullable = true)
    open var param1: String? = null,

    @Column(name = "param2", nullable = true)
    open var param2: String? = null,

    @Column(name = "param3", nullable = true)
    open var param3: String? = null,

    @Column(name = "param4", nullable = true)
    open var param4: String? = null,

    @Column(name = "depth", nullable = true)
    open var depth: Int? = null,

    @Column(name = "createdByClass", nullable = false)
    open var createdByClass: String

) : EntityBase() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JobInfo

        if (type != other.type) return false
        if (param1 != other.param1) return false
        if (param2 != other.param2) return false
        if (param3 != other.param3) return false
        if (param4 != other.param4) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (param1?.hashCode() ?: 0)
        result = 31 * result + (param2?.hashCode() ?: 0)
        result = 31 * result + (param3?.hashCode() ?: 0)
        result = 31 * result + (param4?.hashCode() ?: 0)
        return result
    }
}