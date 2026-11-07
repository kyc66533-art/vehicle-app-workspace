package com.nextdrive.pleos

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import java.util.UUID

/** Framework-only instrumentation: real SQLite, restart, local handover and real production UI. */
class MvpScenarioInstrumentation : Instrumentation() {
    private var checks = 0
    private val output = StringBuilder()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            databaseScenario()
            uiScenario(result)
            result.putString("stream", "CARITDA_MVP_PASS checks=$checks\n$output")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "CARITDA_MVP_FAIL checks=$checks\n$output\n${failure.stackTraceToString()}")
            result.putString("shortMsg", failure.message ?: failure.javaClass.name)
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun verify(condition: Boolean, label: String) {
        check(condition) { label }; checks++; output.appendLine("PASS $label")
    }
    private fun databaseScenario() {
        val name = "caritda-test-${UUID.randomUUID()}.db"
        var repo = SqliteIssueRepository(targetContext, name)
        var allowed = true
        var now = System.currentTimeMillis()
        var service = CaritdaService(repo, { allowed }, { now++ })
        service.selectDriver(Driver.A)
        val issue = service.record(SymptomType.VIBRATION, "가속할 때 핸들이 진동함", now - 60_000, "기술 검증용 예시입니다.")
        verify(issue.reporter == Driver.A && issue.status == IssueStatus.UNRESOLVED, "A records unresolved vibration")
        repo.close()
        repo = SqliteIssueRepository(targetContext, name)
        service = CaritdaService(repo, { allowed }, { now++ })
        verify(service.issue(issue.id).situation == "가속할 때 핸들이 진동함", "SQLite persists after close/reopen")
        verify(service.events(issue.id).single().actor == Driver.A, "reporter and registration history persist")
        service.selectDriver(Driver.B)
        verify(service.needsAcknowledgement(service.issue(issue.id)), "B sees unacknowledged A issue")
        verify(service.acknowledge(issue.id), "B acknowledgement saved")
        verify(!service.acknowledge(issue.id), "repeated acknowledgement is idempotent")
        verify(service.events(issue.id).count { it.kind == EventKind.ACKNOWLEDGED } == 1, "one B confirmation for current revision")
        service.addNote(issue.id, "점검 결과 메모를 남겼습니다.")
        IssueStatus.entries.forEach { service.changeStatus(issue.id, it) }
        verify(service.issue(issue.id).status == IssueStatus.RESOLVED, "all four statuses supported")
        var summary = ConsultationSummary.build(service.issue(issue.id), service.events(issue.id))
        verify(summary.contains("운전자 B") && summary.contains("점검 결과 메모") && summary.contains("진동"), "consultation summary includes handover and action")
        service.recur(issue.id, "같은 진동이 다시 발생했습니다.")
        verify(service.issue(issue.id).revision == 2 && service.issue(issue.id).status == IssueStatus.UNRESOLVED, "recurrence reopens the same card")
        verify(service.needsAcknowledgement(service.issue(issue.id)), "new recurrence needs a new B confirmation")
        service.acknowledge(issue.id)
        verify(service.events(issue.id).count { it.kind == EventKind.ACKNOWLEDGED } == 2, "old acknowledgement history preserved")
        repo.close()
        repo = SqliteIssueRepository(targetContext, name)
        service = CaritdaService(repo, { allowed }, { now++ })
        verify(service.driver() == Driver.B, "selected local profile persists")
        summary = ConsultationSummary.build(service.issue(issue.id), service.events(issue.id))
        verify(summary.contains("다시 발생") && summary.contains("점검 결과 메모"), "recurrence and prior note survive restart")
        val before = service.events(issue.id).size
        allowed = false
        val blocked = listOf<() -> Unit>(
            { service.selectDriver(Driver.A) },
            { service.record(SymptomType.NOISE, "test", now, "") },
            { service.addNote(issue.id, "must not save") },
            { service.changeStatus(issue.id, IssueStatus.RESOLVED) },
            { service.acknowledge(issue.id) },
            { service.recur(issue.id, "must not save") },
        ).count { operation -> try { operation(); false } catch (_: EditingBlockedException) { true } }
        verify(blocked == 6 && service.events(issue.id).size == before && service.issues().size == 1,
            "unknown/restricted editing blocks all six mutations without changing SQLite")
        allowed = true
        val invalid = listOf<() -> Unit>(
            { service.record(SymptomType.OTHER, "  ", now, "") },
            { service.record(SymptomType.OTHER, "test", now + 120_000, "") },
            { service.addNote(issue.id, "  ") },
            { service.recur(issue.id, " ") },
        ).count { operation -> try { operation(); false } catch (_: IllegalArgumentException) { true } }
        verify(invalid == 4 && service.issues().size == 1, "invalid input is rejected without partial records")
        repo.close()
    }

    private fun uiScenario(result: Bundle) {
        val probe = SqliteIssueRepository(targetContext)
        val originalIds = probe.issues().map { it.id }.toSet()
        var activity = launch()
        await("platform UX restriction permits parked editing") { main { tagged(activity, "new_issue")?.isEnabled == true } }
        click(activity, "driver_A")
        click(activity, "new_issue")
        main {
            (tagged(activity, "symptom_type") as Spinner).setSelection(SymptomType.VIBRATION.ordinal)
            (tagged(activity, "situation") as EditText).setText("가속할 때 핸들이 진동함")
            (tagged(activity, "memo") as EditText).setText("에뮬레이터 시나리오 검증용 수기 예시입니다.")
        }
        waitForIdleSync()
        click(activity, "save_issue")
        val issue = probe.issues().single { it.id !in originalIds }
        verify(issue.type == SymptomType.VIBRATION && issue.reporter == Driver.A, "UI saves A vibration record")
        main { activity.finish() }; waitForIdleSync()
        activity = launch()
        await("parked UX reconnects after activity restart") { main { tagged(activity, "new_issue")?.isEnabled == true } }
        verify(probe.issue(issue.id)?.situation == "가속할 때 핸들이 진동함", "UI-created record survives app activity restart")
        click(activity, "driver_B")
        click(activity, "issue_${issue.id}")
        click(activity, "acknowledge")
        verify(probe.events(issue.id).any { it.kind == EventKind.ACKNOWLEDGED && it.actor == Driver.B }, "UI stores B handover confirmation")
        click(activity, "add_note")
        await("action dialog becomes accessible") { findNode(uiAutomation.rootInActiveWindow) { it.className == "android.widget.EditText" } != null }
        val field = findNode(uiAutomation.rootInActiveWindow) { it.className == "android.widget.EditText" }!!
        verify(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "진동 관련 점검 결과를 확인했습니다.")
        }), "UI action memo entered")
        val save = findNode(uiAutomation.rootInActiveWindow) { it.text?.toString() == "저장" && it.isClickable }
            ?: error("Dialog save button not found")
        check(save.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await("UI memo saved") { probe.events(issue.id).any { it.kind == EventKind.NOTE } }
        click(activity, "issue_summary")
        val text = main { (tagged(activity, "consultation_summary") as TextView).text.toString() }
        verify(text.contains("가속할 때 핸들이 진동함") && text.contains("운전자 B") && text.contains("진동 관련 점검 결과"),
            "all nine requested steps reach a consultation summary in the real UI")
        verify(probe.issues().map { it.id }.containsAll(originalIds), "existing production records are preserved")
        click(activity, "home")
        val content = main { (activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) }
        val originalWidth = main { content.layoutParams.width }
        val narrowWidth = (600 * activity.resources.displayMetrics.density).toInt()
        try {
            main { content.layoutParams = content.layoutParams.apply { width = narrowWidth }; content.requestLayout() }
            await("600dp viewport lays out") { main { content.width == narrowWidth } }
            verify(main { tagged(activity, "new_issue")!!.width <= narrowWidth }, "home fits a 600dp viewport")
            click(activity, "new_issue")
            verify(main { tagged(activity, "situation")!!.width <= narrowWidth }, "record form fits a 600dp viewport")
            click(activity, "home")
        } finally {
            main { content.layoutParams = content.layoutParams.apply { width = originalWidth }; content.requestLayout() }
        }
        result.putString("createdIssueId", issue.id)
        output.appendLine("UI_ISSUE_ID=${issue.id}")
        probe.close()
    }
    private fun launch(): MainActivity = startActivitySync(Intent(Intent.ACTION_MAIN).apply {
        setClassName(targetContext, MainActivity::class.java.name)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }) as MainActivity
    private fun click(activity: Activity, tag: String) {
        main { val view = tagged(activity, tag) ?: error("Missing UI: $tag"); check(view.isEnabled) { "Disabled UI: $tag" }; check(view.performClick()) }
        waitForIdleSync()
    }
    private fun tagged(activity: Activity, tag: String): View? = activity.window.decorView.findViewWithTag(tag)
    private fun findNode(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) findNode(node.getChild(i), predicate)?.let { return it }
        return null
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) { if (condition()) return; Thread.sleep(100) }
        error("Timed out: $label")
    }
    private fun <T> main(action: () -> T): T {
        var result: T? = null; var failure: Throwable? = null
        runOnMainSync { try { result = action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
