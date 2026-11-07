package com.nextdrive.pleos.model

/** Stable identity; labels on issues/events are immutable snapshots. Legacy IDs remain A/B. */
data class Driver(val id: String, val label: String, val archived: Boolean = false) {
    val name: String get() = id
    companion object {
        val A = Driver("A", "운전자 A")
        val B = Driver("B", "운전자 B")
        val entries = listOf(A, B)
    }
}

data class JournalDraft(val id: String, val ownerId: String, val ownerName: String,
    val kind: String, val target: String? = null, val revision: Int = 1,
    val type: SymptomType = SymptomType.NOISE, val body: String = "", val memo: String = "",
    val foundAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())
enum class SymptomType(val label: String) { NOISE("소음"), VIBRATION("진동"), WARNING("경고등"), OTHER("기타") }
enum class IssueStatus(val label: String) {
    UNRESOLVED("미해결"), PLANNED("점검 예정"), INSPECTED("점검 완료"), RESOLVED("해결 확인")
}
enum class RecordFilter(val label:String) { ALL("전체 기록"), MANAGED("관리 중"), COMPLETED("완료"), MINE("내가 작성한 기록") }
enum class EventKind(val label: String) {
    CREATED("문제 등록"), ACKNOWLEDGED("인계 확인"), NOTE("점검·조치 메모"), STATUS("상태 변경"), RECURRENCE("재발 기록"),
    EDITED("작성 내용 수정"), DELETED("작성자 삭제")
}
data class VehicleIssue(
    val id: String,
    val type: SymptomType,
    val situation: String,
    val discoveredAt: Long,
    val recordedAt: Long,
    val reporter: Driver,
    val memo: String,
    val status: IssueStatus = IssueStatus.UNRESOLVED,
    val revision: Int = 1,
    val updatedAt: Long = recordedAt,
    val deletedAt: Long? = null,
    val deletedById: String? = null,
)
data class IssueEvent(
    val kind: EventKind,
    val actor: Driver,
    val at: Long,
    val text: String,
    val revision: Int,
    val status: IssueStatus? = null,
    val draftId: String? = null,
)
