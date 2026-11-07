package com.nextdrive.pleos.model

enum class Driver(val label: String) { A("운전자 A"), B("운전자 B") }
enum class SymptomType(val label: String) { NOISE("소음"), VIBRATION("진동"), WARNING("경고등"), OTHER("기타") }
enum class IssueStatus(val label: String) {
    UNRESOLVED("미해결"), PLANNED("점검 예정"), INSPECTED("점검 완료"), RESOLVED("해결 확인")
}
enum class EventKind(val label: String) {
    CREATED("문제 등록"), ACKNOWLEDGED("인계 확인"), NOTE("점검·조치 메모"), STATUS("상태 변경"), RECURRENCE("재발 기록")
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
)
data class IssueEvent(
    val kind: EventKind,
    val actor: Driver,
    val at: Long,
    val text: String,
    val revision: Int,
    val status: IssueStatus? = null,
)
