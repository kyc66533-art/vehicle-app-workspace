package com.nextdrive.pleos.data

import com.nextdrive.pleos.model.*

interface IssueRepository : AutoCloseable {
    fun currentDriver(): Driver?
    fun drivers(includeArchived: Boolean = false): List<Driver>
    fun registerDriver(name: String): Driver
    fun renameDriver(id: String, name: String)
    fun archiveDriver(id: String)
    fun draft(): JournalDraft?
    fun saveDraft(draft: JournalDraft)
    fun discardDraft(id: String)
    fun selectDriver(driver: Driver)
    fun issues(): List<VehicleIssue>
    fun issue(id: String): VehicleIssue?
    fun events(id: String): List<IssueEvent>
    fun create(issue: VehicleIssue, initialEvent: IssueEvent)
    /** Atomically appends history and updates status/revision; acknowledgements are idempotent. */
    fun append(id: String, event: IssueEvent, newStatus: IssueStatus? = null, recurrence: Boolean = false): Boolean
}
