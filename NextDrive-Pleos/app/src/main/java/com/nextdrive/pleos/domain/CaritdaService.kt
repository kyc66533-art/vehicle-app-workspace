package com.nextdrive.pleos.domain

import com.nextdrive.pleos.data.IssueRepository
import com.nextdrive.pleos.model.*
import java.util.UUID

class EditingBlockedException : IllegalStateException("정차 상태와 화면 제한이 확인된 뒤 이용해 주세요.")

class CaritdaService(
    private val repository: IssueRepository,
    private val editingAllowed: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun driver() = repository.currentDriver()
    fun issues() = repository.issues()
    fun issue(id: String) = repository.issue(id) ?: throw IllegalArgumentException("문제를 찾을 수 없습니다.")
    fun events(id: String) = repository.events(id)
    fun selectDriver(driver: Driver) { requireEditing(); repository.selectDriver(driver) }
    fun record(type: SymptomType, situation: String, discoveredAt: Long, memo: String): VehicleIssue {
        requireEditing()
        val description = text(situation, 1000, "발생 상황을 입력해 주세요.")
        require(memo.trim().length <= 2000) { "추가 메모는 2,000자 이내로 입력해 주세요." }
        val now = clock()
        require(discoveredAt in 0..(now + 60_000)) { "발견 시각을 현재 시각 이전으로 확인해 주세요." }
        val issue = VehicleIssue(UUID.randomUUID().toString(), type, description, discoveredAt, now, driver(), memo.trim())
        repository.create(issue, IssueEvent(EventKind.CREATED, issue.reporter, now, "운전자가 직접 기록했습니다.", 1))
        return issue
    }
    fun needsAcknowledgement(issue: VehicleIssue, driver: Driver = driver()) =
        driver != issue.reporter && events(issue.id).none {
            it.kind == EventKind.ACKNOWLEDGED && it.actor == driver && it.revision == issue.revision
        }
    fun acknowledge(id: String): Boolean {
        requireEditing()
        val issue = issue(id)
        require(driver() != issue.reporter) { "다른 운전자가 인계 내용을 확인해 주세요." }
        return repository.append(id, IssueEvent(EventKind.ACKNOWLEDGED, driver(), clock(), "이 차량 앱에서 인계 내용을 확인했습니다.", issue.revision))
    }
    fun addNote(id: String, note: String) {
        requireEditing()
        val issue = issue(id)
        repository.append(id, IssueEvent(EventKind.NOTE, driver(), clock(), text(note, 2000, "점검·조치 내용을 입력해 주세요."), issue.revision))
    }
    fun changeStatus(id: String, status: IssueStatus): Boolean {
        requireEditing()
        val issue = issue(id)
        if (issue.status == status) return false
        return repository.append(id, IssueEvent(EventKind.STATUS, driver(), clock(),
            "${issue.status.label} → ${status.label}", issue.revision, status), status)
    }
    fun recur(id: String, description: String) {
        requireEditing()
        val issue = issue(id)
        repository.append(id, IssueEvent(EventKind.RECURRENCE, driver(), clock(),
            text(description, 2000, "다시 발생한 상황을 입력해 주세요."), issue.revision + 1),
            IssueStatus.UNRESOLVED, recurrence = true)
    }
    private fun requireEditing() { if (!editingAllowed()) throw EditingBlockedException() }
    private fun text(value: String, max: Int, message: String): String {
        val result = value.trim()
        require(result.isNotEmpty()) { message }
        require(result.length <= max) { "${max}자 이내로 입력해 주세요." }
        return result
    }
}
