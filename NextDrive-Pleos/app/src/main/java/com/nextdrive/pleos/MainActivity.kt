package com.nextdrive.pleos

import android.app.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.safety.*
import java.time.ZoneId
import java.util.Calendar

/** Local, manual incident journal. No sensor readings, diagnosis or remote transmission. */
class MainActivity : Activity() {
    private enum class Screen { HOME, RECORD, DETAIL, SUMMARY }
    private data class Draft(var type: SymptomType = SymptomType.NOISE, var situation: String = "",
                             var foundAt: Long = System.currentTimeMillis(), var memo: String = "")
    private lateinit var repository: SqliteIssueRepository
    private lateinit var service: CaritdaService
    private lateinit var safety: CarSafetyGate
    private lateinit var root: LinearLayout
    private var screen = Screen.HOME
    private var selectedIssue: String? = null
    private var includeResolved = false
    private var draft = Draft()
    private var situationInput: EditText? = null
    private var memoInput: EditText? = null
    private var activeDialog: Dialog? = null
    private var displayedSafety = EditingState.UNKNOWN
    private val ink = Color.rgb(235, 243, 249)
    private val muted = Color.rgb(177, 194, 209)
    private val accent = Color.rgb(82, 221, 188)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = column().apply { setBackgroundColor(Color.rgb(14, 23, 34)); setPadding(dp(24), dp(16), dp(24), dp(16)) }
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
        repository = SqliteIssueRepository(this)
        safety = CarSafetyGate(this) { state ->
            if (state != displayedSafety) {
                captureDraft()
                displayedSafety = state
                if (state != EditingState.ALLOWED) {
                    activeDialog?.dismiss()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
                    screen = Screen.HOME
                }
                draw()
            }
        }
        service = CaritdaService(repository, { safety.state == EditingState.ALLOWED })
        savedInstanceState?.let {
            screen = runCatching { Screen.valueOf(it.getString("screen") ?: "HOME") }.getOrDefault(Screen.HOME)
            selectedIssue = it.getString("issue")
            includeResolved = it.getBoolean("includeResolved")
            draft = Draft(runCatching { SymptomType.valueOf(it.getString("type") ?: "NOISE") }.getOrDefault(SymptomType.NOISE),
                it.getString("situation") ?: "", it.getLong("foundAt", System.currentTimeMillis()), it.getString("memo") ?: "")
        }
        // Restored detail/input screens wait for a new platform restriction response.
        if (screen != Screen.HOME) screen = Screen.HOME
        draw()
    }
    override fun onStart() {
        super.onStart()
        captureDraft()
        displayedSafety = EditingState.UNKNOWN
        screen = Screen.HOME
        draw()
        safety.start()
    }
    override fun onStop() { captureDraft(); activeDialog?.dismiss(); safety.stop(); displayedSafety = EditingState.UNKNOWN; super.onStop() }
    override fun onDestroy() { repository.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft()
        outState.putString("screen", screen.name); outState.putString("issue", selectedIssue)
        outState.putBoolean("includeResolved", includeResolved); outState.putString("type", draft.type.name)
        outState.putString("situation", draft.situation); outState.putLong("foundAt", draft.foundAt); outState.putString("memo", draft.memo)
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Framework back navigation")
    override fun onBackPressed() { if (screen == Screen.HOME) super.onBackPressed() else { captureDraft(); screen = Screen.HOME; draw() } }

    private fun draw() {
        if (!::service.isInitialized) return
        captureDraft()
        situationInput = null; memoInput = null
        root.removeAllViews()
        try {
            root.addView(label("CARITDA(가칭)", 28, true))
            root.addView(label("차량 문제를 기록하고 다음 운전자에게 이어드립니다.", 18))
            val driverRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            Driver.entries.forEach { driver ->
                driverRow.addView(button(if (service.driver() == driver) "${driver.label} · 현재" else driver.label,
                    "driver_${driver.name}", primary = service.driver() == driver, enabled = canEdit()) {
                    edit { service.selectDriver(driver); screen = Screen.HOME; selectedIssue = null; draw() }
                }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(0, dp(8), dp(8), 0) })
            }
            root.addView(driverRow)
            root.addView(label(when (displayedSafety) {
                EditingState.UNKNOWN -> "차량의 화면 입력 제한을 확인하고 있습니다."
                EditingState.RESTRICTED -> "주행 제한 중입니다. 정차 후 기록을 확인해 주세요."
                EditingState.ALLOWED -> "화면 입력이 허용됩니다. 정차 중에 이용해 주세요."
            }, 18).apply { tag = "safety_status"; setTextColor(if (canEdit()) accent else Color.rgb(255, 206, 120)) })
            if (screen != Screen.HOME) root.addView(button("처음으로", "home") { screen = Screen.HOME; draw() })
            val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
            val body = column().apply { setPadding(0, dp(16), 0, dp(24)) }
            scroll.addView(body)
            root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            when (screen) {
                Screen.HOME -> home(body)
                Screen.RECORD -> recordForm(body)
                Screen.DETAIL -> detail(body)
                Screen.SUMMARY -> summary(body)
            }
        } catch (e: Exception) {
            android.util.Log.e("Caritda", "Cannot load local records", e)
            root.addView(label("기록을 불러오지 못했습니다. 기존 데이터를 보존했습니다. 다시 시도해 주세요.", 22))
            root.addView(button("다시 확인하기", "retry") { screen = Screen.HOME; draw() })
        }
    }

    private fun home(body: LinearLayout) {
        val all = service.issues()
        val open = all.filter { it.status != IssueStatus.RESOLVED }
        body.addView(label("현재 운전자: ${service.driver().label}", 24, true))
        body.addView(label("같은 차량 앱 안에만 저장됩니다. 휴대폰이나 정비소로 전송되지 않습니다.", 18))
        body.addView(button("새 문제 기록하기", "new_issue", primary = true, enabled = canEdit()) {
            openScreen(Screen.RECORD) { draft = Draft(); situationInput = null; memoInput = null }
        })
        body.addView(button("정비 상담용 요약 보기", "all_summary", enabled = canEdit() && all.isNotEmpty()) {
            openScreen(Screen.SUMMARY) { selectedIssue = null }
        })
        body.addView(label("미해결 차량 문제 ${open.size}건", 26, true))
        if (open.isEmpty()) body.addView(label("등록된 미해결 문제가 없습니다. 차량의 정상 여부를 진단한 결과는 아닙니다.", 20))
        (if (includeResolved) all else open).forEach { issue ->
            val card = panel()
            card.addView(label("${issue.type.label} · ${issue.status.label}", 22, true))
            card.addView(label(issue.situation.take(100), 22))
            card.addView(label("${issue.reporter.label} · 발견 ${ConsultationSummary.time(issue.discoveredAt)}", 18))
            card.addView(label(if (service.needsAcknowledgement(issue)) "현재 운전자의 인계 확인이 필요합니다." else "등록 또는 인계 확인 기록이 있습니다.", 18))
            card.addView(button("문제와 기록 보기", "issue_${issue.id}", enabled = canEdit()) {
                openScreen(Screen.DETAIL) { selectedIssue = issue.id }
            })
            body.addView(card)
        }
        if (all.any { it.status == IssueStatus.RESOLVED }) body.addView(button(
            if (includeResolved) "미해결 문제만 보기" else "해결 확인 기록도 보기", "toggle_resolved", enabled = canEdit()) {
            includeResolved = !includeResolved; draw()
        })
        body.addView(label("출처: 운전자가 직접 입력한 기록입니다. 차량 센서나 AI 진단 결과가 아닙니다.", 18))
    }

    private fun recordForm(body: LinearLayout) {
        body.addView(label("새 문제를 기록해 주세요.", 26, true))
        body.addView(label("기록 운전자: ${service.driver().label}", 20))
        body.addView(label("증상 종류", 22, true))
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, SymptomType.entries.map { it.label }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).also { (it as TextView).textSize = 22f; it.setTextColor(Color.BLACK) }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).also {
                    (it as TextView).textSize = 22f; it.setTextColor(Color.BLACK); it.setBackgroundColor(Color.WHITE)
                    it.setPadding(dp(20), dp(16), dp(20), dp(16))
                }
        }
        val spinner = Spinner(this).apply {
            tag = "symptom_type"; setBackgroundColor(Color.WHITE); this.adapter = adapter
            setSelection(draft.type.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { draft.type = SymptomType.entries[position] }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        body.addView(spinner, LinearLayout.LayoutParams(-1, dp(64)))
        body.addView(label("어떤 상황에서 발생했나요?", 22, true))
        situationInput = input("예: 가속할 때 핸들이 진동합니다.", 1000).apply { tag = "situation"; setText(draft.situation) }
        body.addView(situationInput)
        body.addView(button("발견 시각: ${ConsultationSummary.time(draft.foundAt)}", "discovered_at") { pickTime() })
        body.addView(label("시각은 기기 설정(${ZoneId.systemDefault().id}) 기준입니다.", 18))
        body.addView(label("추가 메모 (선택)", 22, true))
        memoInput = input("추가로 전달할 내용을 적어 주세요.", 2000).apply { tag = "memo"; setText(draft.memo) }
        body.addView(memoInput)
        body.addView(button("기록 저장하기", "save_issue", primary = true, enabled = canEdit()) {
            captureDraft()
            edit {
                val issue = service.record(draft.type, draft.situation, draft.foundAt, draft.memo)
                selectedIssue = issue.id; screen = Screen.DETAIL; draft = Draft(); draw()
                toast("문제를 이 차량 앱에 저장했습니다.")
            }
        })
    }

    private fun detail(body: LinearLayout) {
        val id = selectedIssue ?: run { screen = Screen.HOME; draw(); return }
        val issue = service.issue(id)
        body.addView(label("${issue.type.label} · ${issue.status.label}", 26, true))
        body.addView(label(issue.situation, 24))
        body.addView(label("발견 ${ConsultationSummary.time(issue.discoveredAt)}\n등록 ${issue.reporter.label} · ${ConsultationSummary.time(issue.recordedAt)}", 18))
        if (issue.memo.isNotBlank()) body.addView(label("추가 메모: ${issue.memo}", 20))
        if (service.driver() == issue.reporter) body.addView(label("이 문제를 직접 등록하셨습니다.", 20))
        else if (service.needsAcknowledgement(issue)) body.addView(button("인계 내용 확인하기", "acknowledge", primary = true, enabled = canEdit()) {
            edit { service.acknowledge(id); draw(); toast("현재 운전자의 확인 이력을 저장했습니다.") }
        }) else body.addView(label("현재 운전자의 인계 확인이 저장되었습니다.", 20).apply { tag = "acknowledged" })
        body.addView(button("점검·조치 메모 남기기", "add_note", enabled = canEdit()) {
            textDialog("점검·조치 내용을 기록해 주세요.", "직접 확인하거나 수행한 내용을 적어 주세요.") { service.addNote(id, it) }
        })
        body.addView(button("상태 변경하기", "change_status", enabled = canEdit()) { statusDialog(issue) })
        body.addView(button("같은 문제의 재발 기록하기", "recurrence", enabled = canEdit()) {
            textDialog("다시 발생한 상황을 기록해 주세요.", "기존 이력을 보존하고 미해결 상태로 다시 표시합니다.") { service.recur(id, it) }
        })
        body.addView(button("이 문제의 정비 상담용 요약", "issue_summary", enabled = canEdit()) { openScreen(Screen.SUMMARY) })
        body.addView(label("확인·점검 이력", 24, true))
        service.events(id).forEach { event ->
            val card = panel()
            card.addView(label("${event.kind.label} · ${event.actor.label}", 20, true))
            card.addView(label("${ConsultationSummary.time(event.at)} · ${event.revision}차 기록\n${event.text}", 18))
            body.addView(card)
        }
        body.addView(label("상태는 운전자가 직접 표시한 기록입니다. 차량의 안전이나 수리 성공을 자동 판정하지 않습니다.", 18))
    }

    private fun summary(body: LinearLayout) {
        body.addView(label("정비 상담용 요약", 26, true))
        body.addView(label("시간대: ${ZoneId.systemDefault().id}\n외부 정비소에는 전송되지 않았습니다.", 18))
        val issues = selectedIssue?.let { listOf(service.issue(it)) } ?: service.issues()
        issues.forEach { issue ->
            val card = panel()
            card.addView(label(ConsultationSummary.build(issue, service.events(issue.id)), 21).apply { tag = "consultation_summary" })
            body.addView(card)
        }
    }

    private fun statusDialog(issue: VehicleIssue) {
        if (!canEdit()) { toast("정차 후 이용해 주세요."); return }
        activeDialog = AlertDialog.Builder(this).setTitle("기록할 상태를 선택해 주세요.")
            .setItems(IssueStatus.entries.map { it.label }.toTypedArray()) { _, which ->
                val state = IssueStatus.entries[which]
                if (state == IssueStatus.RESOLVED) {
                    activeDialog = AlertDialog.Builder(this).setTitle("해결 확인을 기록할까요?")
                        .setMessage("직접 확인한 결과를 기록합니다. 앱이 고장이나 안전을 진단하는 것은 아닙니다.")
                        .setPositiveButton("확인 기록") { _, _ -> edit { service.changeStatus(issue.id, state); draw() } }
                        .setNegativeButton("취소", null).show()
                } else edit { service.changeStatus(issue.id, state); draw() }
            }.setNegativeButton("취소", null).show()
    }
    private fun textDialog(title: String, hint: String, save: (String) -> Unit) {
        if (!canEdit()) { toast("정차 후 이용해 주세요."); return }
        val field = input(hint, 2000).apply { tag = "action_note" }
        val wrapper = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)); addView(field) }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(wrapper)
            .setPositiveButton("저장", null).setNegativeButton("취소", null).create()
        activeDialog = dialog
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            edit { save(field.text.toString()); dialog.dismiss(); draw(); toast("기록을 저장했습니다.") }
        } }
        dialog.show()
    }
    private fun pickTime() {
        if (!canEdit()) return
        captureDraft()
        val calendar = Calendar.getInstance().apply { timeInMillis = draft.foundAt }
        val date = DatePickerDialog(this, { _, year, month, day ->
            if (!canEdit()) return@DatePickerDialog
            calendar.set(year, month, day)
            activeDialog = TimePickerDialog(this, { _, hour, minute ->
                if (canEdit()) { calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute); calendar.set(Calendar.SECOND, 0); draft.foundAt = calendar.timeInMillis; draw() }
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).also { it.show() }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        date.datePicker.maxDate = System.currentTimeMillis(); activeDialog = date; date.show()
    }
    private fun openScreen(target: Screen, prepare: () -> Unit = {}) {
        if (!canEdit()) { toast("정차 상태가 확인된 뒤 이용해 주세요."); return }
        captureDraft(); prepare(); screen = target; draw()
    }
    private fun edit(action: () -> Unit) {
        try { action() }
        catch (e: IllegalArgumentException) { toast(e.message ?: "입력 내용을 확인해 주세요.") }
        catch (e: EditingBlockedException) { toast(e.message ?: "정차 후 이용해 주세요.") }
        catch (e: Exception) { android.util.Log.e("Caritda", "Local write failed", e); toast("저장하지 못했습니다. 기존 기록을 유지했습니다. 다시 시도해 주세요.") }
    }
    private fun canEdit() = ::safety.isInitialized && safety.state == EditingState.ALLOWED
    private fun captureDraft() { if (screen == Screen.RECORD) { situationInput?.let { draft.situation = it.text.toString() }; memoInput?.let { draft.memo = it.text.toString() } } }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
    private fun panel() = column().apply {
        setPadding(dp(20), dp(16), dp(20), dp(16)); background = shape(Color.rgb(27, 41, 56))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(12), 0, dp(12)) }
    }
    private fun label(value: String, size: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(if (size < 21) muted else ink)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(8)); layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    private fun button(value: String, testTag: String, primary: Boolean = false, enabled: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = value; tag = testTag; textSize = 20f; isAllCaps = false; minHeight = dp(64)
        setPadding(dp(16), dp(12), dp(16), dp(12)); setTextColor(if (primary) Color.rgb(10, 30, 30) else ink)
        background = shape(if (primary) accent else Color.rgb(43, 61, 79)); isEnabled = enabled; alpha = if (enabled) 1f else .45f
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(8)) }
        setOnClickListener { action() }
    }
    private fun input(hintText: String, max: Int) = EditText(this).apply {
        hint = hintText; textSize = 22f; minLines = 3; maxLines = 6
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        filters = arrayOf(InputFilter.LengthFilter(max)); setTextColor(Color.BLACK); setHintTextColor(Color.DKGRAY)
        setPadding(dp(16), dp(12), dp(16), dp(12)); background = shape(Color.rgb(242, 246, 249))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(12)) }
    }
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat() }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
