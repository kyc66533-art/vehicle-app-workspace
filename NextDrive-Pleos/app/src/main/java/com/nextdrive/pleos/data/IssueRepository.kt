package com.nextdrive.pleos.data

import com.nextdrive.pleos.model.*

interface IssueRepository : AutoCloseable {
    fun currentDriver(): Driver
    fun selectDriver(driver: Driver)
    fun issues(): List<VehicleIssue>
    fun issue(id: String): VehicleIssue?
    fun events(id: String): List<IssueEvent>
    fun create(issue: VehicleIssue, initialEvent: IssueEvent)
    /** Atomically appends history and updates status/revision; acknowledgements are idempotent. */
    fun append(id: String, event: IssueEvent, newStatus: IssueStatus? = null, recurrence: Boolean = false): Boolean
}
