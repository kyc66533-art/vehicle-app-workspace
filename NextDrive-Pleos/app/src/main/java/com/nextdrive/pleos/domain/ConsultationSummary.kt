package com.nextdrive.pleos.domain

import com.nextdrive.pleos.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ConsultationSummary {
    fun time(at: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

    fun build(issue: VehicleIssue, events: List<IssueEvent>): String = buildString {
        appendLine("정비 상담용 기록")
        appendLine("증상 유형: ${issue.type.label}")
        appendLine("발견 시각: ${time(issue.discoveredAt)}")
        appendLine("등록: ${issue.reporter.label} · ${time(issue.recordedAt)}")
        appendLine("발생 상황: ${issue.situation}")
        if (issue.memo.isNotBlank()) appendLine("추가 메모: ${issue.memo}")
        appendLine("현재 상태: ${issue.status.label}")
        appendLine("기록 차수: ${issue.revision}")
        appendLine()
        appendLine("운전자 확인 이력")
        val acknowledgements = events.filter { it.kind == EventKind.ACKNOWLEDGED }
        if (acknowledgements.isEmpty()) appendLine("다른 운전자의 확인 기록이 아직 없습니다.")
        acknowledgements.forEach { appendLine("${time(it.at)} · ${it.actor.label} · ${it.revision}차 기록 확인") }
        appendLine()
        appendLine("점검·조치 및 상태 이력")
        val actions = events.filter { it.kind in setOf(EventKind.NOTE, EventKind.STATUS, EventKind.RECURRENCE) }
        if (actions.isEmpty()) appendLine("점검·조치 기록이 아직 없습니다.")
        actions.forEach { appendLine("${time(it.at)} · ${it.actor.label} · ${it.kind.label}\n${it.text}") }
        appendLine()
        append("운전자가 직접 입력하고 확인한 로컬 기록입니다. 고장 진단이나 안전 판정이 아닙니다. 외부 정비소에는 전송되지 않았습니다.")
    }
}
