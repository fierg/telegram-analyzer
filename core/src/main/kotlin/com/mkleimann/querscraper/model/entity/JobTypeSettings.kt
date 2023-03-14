package com.mkleimann.querscraper.model.entity

import com.mkleimann.querscraper.model.enum.JobTypes
import javax.persistence.*

@Entity(name = "jobTypeSettings")
@Table(
    uniqueConstraints = [UniqueConstraint(
        name = "U_JOBTYPE_SETTINGS",
        columnNames = ["type"]
    )]
)
open class JobTypeSettings(

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    open var type: JobTypes,

    @Column(name = "isPaused", nullable = false)
    open var isPaused: Boolean,

) : EntityBase()