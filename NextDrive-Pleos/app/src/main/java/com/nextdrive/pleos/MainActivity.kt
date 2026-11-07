package com.nextdrive.pleos

import android.app.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.content.res.Configuration
import android.text.Editable
import android.text.TextWatcher
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
import com.nextdrive.pleos.vehicle.*
import java.time.ZoneId
import java.util.Calendar

/** Local, manual incident journal. No sensor readings, diagnosis or remote transmission. */
class MainActivity : Activity() {
    private enum class Screen { HOME, ISSUES, DRIVERS, DRIVER_MANAGE, DRIVER_EDIT, RECORD, DETAIL, SUMMARY, VEHICLE, ACTION }
    private lateinit var observations: ObservationStore
    private lateinit var pleos: PleosReadClient
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
    private var journal: JournalDraft? = null
    private var pendingDriver: String? = null
    private var editingDriver: String? = null
    private var driverNameDraft: String? = null
    private var driverNameInput: EditText? = null
    private var actionField: EditText? = null
    private var notice = ""
    private var feedback: TextView? = null
    private var menuOpen = false
    private val history = java.util.ArrayDeque<Screen>()
    private val positions = mutableMapOf<Screen,Int>()
    private var scrollView: ScrollView? = null
    private var lastDraft: JournalDraft? = null
    private var situationInput: EditText? = null
    private var memoInput: EditText? = null
    private var activeDialog: Dialog? = null
    private var displayedSafety = EditingState.UNKNOWN
    private var ink = Color.rgb(240,244,248)
    private var muted = Color.rgb(183,196,209)
    private var accent = Color.rgb(140,200,255)
    private var backdrop = Color.rgb(16,24,32)
    private var cardColor = Color.rgb(28,41,54)
    private var actionColor = Color.rgb(6,43,73)
    private var dark = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = column().apply { setBackgroundColor(Color.rgb(14, 23, 34)); setPadding(dp(24), dp(16), dp(24), dp(16)) }
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
        root.addOnLayoutChangeListener { _,l,_,r,_,oldL,_,oldR,_ -> if(oldR>oldL && r-l!=oldR-oldL)root.post{draw()} }
        repository = SqliteIssueRepository(this)
        observations = ObservationStore(this)
        pleos = PleosReadClient(this, observations) { if (screen == Screen.VEHICLE) draw() }
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
        service.restoreConfiguration(lastNonConfigurationInstance as? String)
        journal = runCatching { service.draft() }.getOrElse { notice="초안을 복원하지 못했습니다. 기존 초안은 보존됩니다.";null }
        lastDraft = journal
        pendingDriver = journal?.ownerId ?: repository.currentDriver()?.id
        savedInstanceState?.let {
            pendingDriver=journal?.ownerId?:it.getString("pendingDriver",pendingDriver)
            editingDriver=it.getString("editingDriver")
            driverNameDraft=it.getString("driverNameDraft")
            notice=it.getString("notice",notice)
            if(lastNonConfigurationInstance!=null)it.getStringArrayList("history")?.forEach { name->runCatching{history.addLast(Screen.valueOf(name))} }
            Screen.entries.forEach{target->positions[target]=it.getInt("position_${target.name}")}
            screen = runCatching { Screen.valueOf(it.getString("screen") ?: "HOME") }.getOrDefault(Screen.HOME)
            selectedIssue = it.getString("issue")
            includeResolved = it.getBoolean("includeResolved")
            draft = Draft(runCatching { SymptomType.valueOf(it.getString("type") ?: "NOISE") }.getOrDefault(SymptomType.NOISE),
                it.getString("situation") ?: "", it.getLong("foundAt", System.currentTimeMillis()), it.getString("memo") ?: "")
        }
        // Restored detail/input screens wait for a new platform restriction response.
        if (!service.confirmed()) screen = Screen.DRIVERS
        draw()
    }
    override fun onStart() {
        super.onStart()
        captureDraft()
        displayedSafety = EditingState.UNKNOWN
        draw()
        safety.start()
    }
    override fun onStop() { captureDraft(); activeDialog?.dismiss(); pleos.stop(); safety.stop(); displayedSafety = EditingState.UNKNOWN; super.onStop() }
    override fun onDestroy() { repository.close(); observations.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft()
        outState.putString("screen", screen.name); outState.putString("issue", selectedIssue)
        outState.putBoolean("includeResolved", includeResolved); outState.putString("type", draft.type.name)
        outState.putString("situation", draft.situation); outState.putLong("foundAt", draft.foundAt); outState.putString("memo", draft.memo)
        outState.putString("pendingDriver",pendingDriver);outState.putString("editingDriver",editingDriver)
        outState.putString("driverNameDraft",driverNameInput?.text?.toString()?:driverNameDraft);outState.putString("notice",notice)
        outState.putStringArrayList("history",ArrayList(history.map{it.name}))
        positions[screen]=scrollView?.scrollY?:0;positions.forEach{(target,position)->outState.putInt("position_${target.name}",position)}
        super.onSaveInstanceState(outState)
    }
    override fun onRetainNonConfigurationInstance(): Any? = if(service.confirmed()) service.driver().id else null
    @Deprecated("Framework back navigation")
    override fun onBackPressed() { captureDraft(); if(history.isNotEmpty()) screen=history.removeLast() else if(screen!=Screen.HOME) screen=Screen.HOME else {super.onBackPressed();return};draw() }

    private fun draw() {
        if (!::service.isInitialized || isDestroyed || isFinishing) return
        captureDraft()
        driverNameDraft=driverNameInput?.text?.toString()?:driverNameDraft;driverNameInput=null
        situationInput=null;memoInput=null;actionField=null
        val preference=getPreferences(MODE_PRIVATE).getString("theme","system")
        dark=when(preference){"dark"->true;"light"->false;else->resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES}
        ink=Color.parseColor(if(dark)"#F0F4F8" else "#17212B")
        muted=Color.parseColor(if(dark)"#B7C4D1" else "#4E5D6C")
        accent=Color.parseColor(if(dark)"#8CC8FF" else "#075BAA")
        backdrop=Color.parseColor(if(dark)"#101820" else "#F4F6F8")
        cardColor=Color.parseColor(if(dark)"#1C2936" else "#FFFFFF")
        actionColor=Color.parseColor(if(dark)"#062B49" else "#FFFFFF")
        root.setBackgroundColor(backdrop);root.setPadding(dp(24),dp(8),dp(24),dp(8));root.removeAllViews()
        val scroll=ScrollView(this).apply{isFillViewport=true;clipToPadding=false};scrollView=scroll
        val body=column();scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        try {
            body.addView(label("CARITDA · 차량 기록",28,true))
            val recent=repository.currentDriver()
            body.addView(button(if(service.confirmed())"기록 운전자: ${service.driver().label} · 변경" else "최근 운전자: ${recent?.label ?: "없음"} · 운전 시작", "change_driver",enabled=canEdit()) {
                if(service.draft()!=null && service.confirmed()) {notice="작성 중인 초안을 먼저 저장하거나 취소해 주세요.";draw()} else {pendingDriver=service.draft()?.ownerId?:recent?.id;openScreen(Screen.DRIVERS)}
            })
            body.addView(label(when(displayedSafety){EditingState.UNKNOWN->"차량의 화면 입력 허용 상태를 확인하고 있습니다.";EditingState.RESTRICTED->"주행 제한 중입니다. 입력은 정차 후 이용해 주세요.";EditingState.ALLOWED->"화면 입력이 허용됩니다. 정차 중에 이용해 주세요."},20).apply{tag="safety_status"})
            feedback=label(notice,22,true).apply{tag="feedback";visibility=if(notice.isBlank())View.GONE else View.VISIBLE};body.addView(feedback)
            val available=if(root.width>0)root.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat()
            val compact=available<600 || resources.configuration.fontScale>=1.5f
            if(compact){body.addView(button("메뉴 · ${sectionName()}","menu"){menuOpen=!menuOpen;draw()})}
            if(!compact || menuOpen) {
                val nav=LinearLayout(this).apply{orientation=if(compact)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL}
                listOf("홈" to Screen.HOME,"문제" to Screen.ISSUES,"차량" to Screen.VEHICLE).forEach { (name,target)->
                    val item=button(if(screen==target)"✓ $name" else name,"nav_${target.name}",enabled=canEdit()){menuOpen=false;openScreen(target)}
                    if(compact) nav.addView(item) else nav.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(8),dp(8),dp(8),dp(8))})
                };body.addView(nav)
            }
            if(screen !in setOf(Screen.HOME,Screen.ISSUES,Screen.VEHICLE,Screen.DRIVERS)) body.addView(button("뒤로", "home"){onBackPressed()})
            when(screen){
                Screen.HOME->home(body)
                Screen.ISSUES->issues(body)
                Screen.DRIVERS->driverScreen(body)
                Screen.DRIVER_MANAGE->driverManage(body)
                Screen.DRIVER_EDIT->driverForm(body)
                Screen.RECORD->recordForm(body)
                Screen.DETAIL->detail(body)
                Screen.SUMMARY->summary(body)
                Screen.VEHICLE->vehicleScreen(body)
                Screen.ACTION->actionForm(body)
            }
            body.addView(button("화면 테마: ${when(preference){"dark"->"어둡게";"light"->"밝게";else->"시스템 설정"}}","theme",enabled=canEdit()){
                check(getPreferences(MODE_PRIVATE).edit().putString("theme",when(preference){"dark"->"light";"light"->"system";else->"dark"}).commit())
                draw()
            })
            scroll.post{if(screen!=Screen.RECORD && screen!=Screen.ACTION)scroll.scrollTo(0,positions[screen]?:0)}
        } catch(e:Exception) {
            android.util.Log.e("Caritda","Read failed",e)
            body.addView(label("기록을 불러오지 못했습니다. 기존 데이터는 보존됩니다.",24,true))
            body.addView(button("다시 확인하기","retry"){draw()})
        }
    }
    private fun sectionName()=when(screen){Screen.HOME->"홈";Screen.ISSUES->"문제";Screen.VEHICLE->"차량";Screen.DRIVERS,Screen.DRIVER_EDIT->"운전자";else->"기록"}
    private fun home(body:LinearLayout) {
        val all=service.issues();val open=all.filter{it.status!=IssueStatus.RESOLVED}
        body.addView(label("함께 쓰는 차의 기록을 이어드립니다",28,true))
        if(!service.confirmed())body.addView(button("운전자를 선택하고 운전 시작","choose_driver",primary=true,enabled=canEdit()){openScreen(Screen.DRIVERS)})
        else {
            body.addView(button("문제 기록","new_issue",primary=true,enabled=canEdit()){beginRecord()})
            val unread=open.count{service.needsAcknowledgement(it)}
            body.addView(label("미해결 ${open.size}건 · 미확인 인계 ${unread}건",24,true).apply{tag="home_counts"})
            open.filter{service.needsAcknowledgement(it)}.take(1).forEach{issueCard(body,it)}
        }
        draftBanner(body)
        val vehicle=panel();vehicle.addView(label("최근 차량 상태",24,true))
        latestVehicleLines().forEach{vehicle.addView(label(it,22))};body.addView(vehicle)
        body.addView(button("차량 상태 자세히 보기","vehicle_data",enabled=canEdit()){openScreen(Screen.VEHICLE)})
        body.addView(button("전체 문제 ${all.size}건 보기","all_issues",enabled=canEdit()){openScreen(Screen.ISSUES)})
        body.addView(label("같은 차량 앱의 로컬 기록입니다. 차량 정상 판정이나 외부 전송을 의미하지 않습니다.",20))
    }
    private fun issues(body:LinearLayout){
        body.addView(label("차량 문제",28,true))
        body.addView(button("문제 기록","new_issue",primary=true,enabled=canWrite()){beginRecord()})
        draftBanner(body)
        val all=service.issues();val chosen=all.filter{(it.status==IssueStatus.RESOLVED)==includeResolved}
        body.addView(button(if(includeResolved)"✓ 해결 기록 ${chosen.size}건 · 미해결 보기" else "✓ 미해결 ${chosen.size}건 · 해결 기록 보기","toggle_resolved",enabled=canEdit()){includeResolved=!includeResolved;positions[Screen.ISSUES]=0;draw()})
        body.addView(label("미확인 인계 우선 · 그 안에서 최근 기록 순입니다.",20))
        if(chosen.isEmpty())body.addView(label("표시할 문제가 없습니다. 차량 정상 진단 결과는 아닙니다.",24))
        chosen.sortedBy{if(service.confirmed()&&service.needsAcknowledgement(it))0 else 1}.forEach{issueCard(body,it)}
        body.addView(button("정비 상담용 요약 · 전체 ${all.size}건","all_summary",enabled=canEdit()&&all.isNotEmpty()){selectedIssue=null;openScreen(Screen.SUMMARY)})
    }
    private fun issueCard(body:LinearLayout,issue:VehicleIssue){
        val card=panel();card.addView(label("사용자 직접 기록 · ${issue.status.label}",20))
        card.addView(label("${issue.type.label} · ${issue.situation.take(100)}",24,true))
        card.addView(label("발견 ${ConsultationSummary.time(issue.discoveredAt)} · 등록 ${issue.reporter.label}",20))
        if(service.confirmed()&&service.needsAcknowledgement(issue))card.addView(label("${service.driver().label}님의 읽음 확인이 필요합니다.",22,true))
        card.addView(button("문제와 기록 보기","issue_${issue.id}",enabled=canEdit()){selectedIssue=issue.id;openScreen(Screen.DETAIL)})
        body.addView(card)
    }
    private fun driverScreen(body:LinearLayout){
        body.addView(label("기록 운전자를 선택해 주세요",28,true))
        body.addView(label("이름이나 별명만 사용합니다. 차량 시동·OS 계정·시트 설정은 바뀌지 않습니다.",20))
        val drivers=service.drivers()
        if(drivers.isEmpty())body.addView(label("등록된 운전자가 없습니다. 먼저 이름을 등록해 주세요.",24))
        drivers.forEach{driver->
            val card=panel()
            card.addView(button((if(pendingDriver==driver.id)"✓ " else "")+driver.label+(if(pendingDriver==driver.id)" · 선택됨" else ""),"driver_${driver.id}",enabled=canEdit()){
                pendingDriver=driver.id;notice="선택 후보입니다. 운전 시작을 누르면 확정됩니다.";draw()
            }.apply{isSelected=pendingDriver==driver.id})
            body.addView(card)
        }
        body.addView(button("운전자 추가","add_driver",enabled=canEdit()){editingDriver=null;driverNameDraft=null;openScreen(Screen.DRIVER_EDIT)})
        body.addView(button("운전자 관리","manage_drivers",enabled=canEdit()){openScreen(Screen.DRIVER_MANAGE)})
        val candidate=drivers.find{it.id==pendingDriver}
        root.addView(button("${candidate?.label ?: "운전자 선택 후"} · 운전 시작","start_driving",primary=true,enabled=canEdit()&&candidate!=null){
            edit{service.selectDriver(candidate!!);notice="${service.driver().label}님을 기록 운전자로 설정했습니다.";history.clear();screen=Screen.HOME;draw()}
        })
        body.addView(button("선택 취소 · 홈으로","cancel_driver"){pendingDriver=repository.currentDriver()?.id;screen=Screen.HOME;draw()})
        draftBanner(body)
    }
    private fun driverManage(body:LinearLayout){
        body.addView(label("운전자 관리",28,true))
        body.addView(label("이름을 수정해도 과거 작성자 표시는 유지됩니다. 보관은 기록 삭제가 아닙니다.",22))
        service.drivers().forEach{driver->
            val card=panel();card.addView(label(driver.label,24,true))
            card.addView(button("${driver.label} 이름 수정","rename_${driver.id}",enabled=canEdit()){editingDriver=driver.id;driverNameDraft=null;openScreen(Screen.DRIVER_EDIT)})
            card.addView(button("${driver.label} 보관","archive_${driver.id}",enabled=canEdit()){
                confirm("운전자를 보관할까요?","과거 기록은 유지되고 선택 목록에서만 제외됩니다."){
                    service.archiveDriver(driver.id);if(pendingDriver==driver.id)pendingDriver=null;notice="운전자를 보관했습니다.";draw()
                }
            });body.addView(card)
        }
    }
    private fun driverForm(body:LinearLayout){
        body.addView(label(if(editingDriver==null)"운전자 등록" else "이름 수정",28,true))
        body.addView(label("이름 또는 별명을 1~12자로 입력해 주세요. 과거 작성자 이름은 바뀌지 않습니다.",22))
        val field=input("이름 또는 별명",48).apply{tag="driver_name";minLines=1;setText(driverNameDraft?:service.drivers().find{it.id==editingDriver}?.label.orEmpty())};driverNameInput=field;body.addView(field)
        body.addView(button("이름 저장","save_driver",primary=true,enabled=canEdit()){
            if(screen!=Screen.DRIVER_EDIT)return@button
            edit{if(editingDriver==null)pendingDriver=service.registerDriver(field.text.toString()).id else service.renameDriver(editingDriver!!,field.text.toString());driverNameInput=null;driverNameDraft=null;notice="이름을 저장했습니다. 운전 시작으로 기록 운전자를 확정해 주세요.";screen=Screen.DRIVERS;draw()}
        })
    }
    private fun draftBanner(body:LinearLayout){
        val saved=service.draft()?:return
        val card=panel();card.addView(label("${saved.ownerName}님이 작성 중인 기록이 있습니다.",24,true))
        card.addView(button("계속 작성","resume_draft",enabled=canWrite()&&service.driver().id==saved.ownerId){resumeDraft(saved)})
        card.addView(button("작성 취소","discard_draft",enabled=canEdit()){
            confirm("초안 작성을 취소할까요?","저장하지 않은 이 초안만 제거합니다. 기존 문제와 이력은 유지됩니다."){
                service.discardDraft(saved.id);journal=null;lastDraft=null;draft=Draft()
                if(screen==Screen.RECORD || screen==Screen.ACTION){screen=Screen.HOME;history.clear()}
                notice="초안 작성을 취소했습니다.";draw()
            }
        });body.addView(card)
    }
    private fun beginRecord(){
        if(!canWrite()){notice="운전자를 선택하고 운전 시작을 눌러 주세요.";screen=Screen.DRIVERS;draw();return}
        val old=service.draft()
        if(old!=null){notice="기존 초안을 계속 작성하거나 취소해 주세요.";draw();return}
        journal=JournalDraft(java.util.UUID.randomUUID().toString(),service.driver().id,service.driver().label,"RECORD")
        draft=Draft(foundAt=journal!!.foundAt);openScreen(Screen.RECORD)
    }
    private fun resumeDraft(saved:JournalDraft){
        journal=saved;lastDraft=saved;selectedIssue=saved.target
        draft=Draft(saved.type,saved.body,saved.foundAt,saved.memo)
        openScreen(if(saved.kind=="RECORD")Screen.RECORD else Screen.ACTION)
    }
    private fun actionForm(body:LinearLayout){
        val saved=journal?:return
        body.addView(label(if(saved.kind=="NOTE")"점검·조치 메모" else "재발 기록",28,true))
        body.addView(label("작성자: ${saved.ownerName} · 사용자 직접 기록",22))
        actionField=input("직접 확인한 내용을 적어 주세요.",2000).apply{tag="action_note";setText(saved.body)};body.addView(actionField)
        actionField!!.addTextChangedListener(watcher{captureDraft()})
        body.addView(button("이 차량 앱에 저장","save_action",primary=true,enabled=canWrite()){
            captureDraft();edit{
                val current=journal!!;if(current.kind=="NOTE")service.addNote(current.target!!,current.body,current.id) else service.recur(current.target!!,current.body,current.id)
                journal=null;lastDraft=null;actionField=null;notice="기록을 저장했습니다.";screen=Screen.DETAIL;draw()
            }
        })
        cancelDraftButton(body)
    }
    private fun cancelDraftButton(body:LinearLayout){
        body.addView(button("작성 취소","discard_draft",enabled=canEdit()){
            val active=journal?:return@button
            confirm("초안 작성을 취소할까요?","이 작성 중인 초안만 제거합니다. 기존 문제와 이력은 유지됩니다."){
                service.discardDraft(active.id);journal=null;lastDraft=null;draft=Draft();screen=Screen.HOME;history.clear()
                notice="초안 작성을 취소했습니다.";draw()
            }
        })
    }
    private fun latestVehicleLines():List<String> = listOf("EV_SOC" to "배터리 충전량","ODOMETER" to "누적 주행거리","FUEL_PERCENT" to "연료 잔량").map{(kind,name)->
        val item=observations.latest(100).firstOrNull{it.kind==kind}
        if(item?.status==ObservationStatus.RECEIVED) "$name ${item.raw} ${item.unit} · ${age(item)}" else "$name · ${if(item?.status==ObservationStatus.UNSUPPORTED)"미지원" else "미수신"}"
    }
    private fun age(item:VehicleObservation):String {
        val at=item.receivedAt?:return "미수신"
        return (if(System.currentTimeMillis()-at>600_000)"오래된 기록 · " else "마지막 수신 · ")+ConsultationSummary.time(at)
    }
    private fun vehicleScreen(body: LinearLayout) {
        body.addView(label("차량 상태", 28, true))
        body.addView(label("최근 저장값입니다. 10분이 지나면 오래된 기록으로 표시하며 정상 여부를 판정하지 않습니다.",20))
        body.addView(label(pleos.message, 20).apply { tag = "sdk_status" })
        body.addView(label("SOC・누적 주행거리・연료 잔량을 지원 확인 후 한 번 읽습니다. 에뮬레이터 SDK 응답은 실차 측정과 다릅니다.", 18))
        body.addView(button("차량 데이터 읽고 저장하기", "read_vehicle", enabled = canEdit() && !pleos.busy) {
            edit { pleos.readOnce() }
        })
        observations.latest().forEach { item ->
            val card = panel()
            val name=when(item.kind){"EV_SOC"->"배터리 충전량";"ODOMETER"->"누적 주행거리";"FUEL_PERCENT"->"연료 잔량";else->item.kind}
            val state=when(item.status){ObservationStatus.RECEIVED->age(item);ObservationStatus.UNSUPPORTED->"현재 차량 환경에서 미지원";ObservationStatus.PERMISSION_DENIED->"조회 권한 없음";ObservationStatus.TIMEOUT->"응답 시간 초과";ObservationStatus.CONNECTION_FAILED->"연결 실패";ObservationStatus.INVALID->"유효한 값 미수신";else->"SDK 미연결 기록"}
            card.addView(label("$name · $state", 24, true))
            card.addView(label("출처: ${if(item.source==ObservationSource.PLEOS_EMULATOR_SDK) "Pleos 에뮬레이터 SDK" else item.source} · ${item.status}",20))
            card.addView(label("원본: ${item.raw ?: "미수신"} · 단위: ${item.unit ?: "없음"}", 20))
            card.addView(label("수신: ${item.receivedAt?.let(ConsultationSummary::time) ?: "없음"}\n상태 기록: ${ConsultationSummary.time(item.recordedAt)}\n세션: ${item.session}", 18))
            card.addView(label(item.detail, 20))
            body.addView(card)
        }
    }

    private fun recordForm(body: LinearLayout) {
        body.addView(label("새 문제를 기록해 주세요.", 26, true))
        body.addView(label("작성자: ${journal?.ownerName ?: service.driver().label} · 사용자 직접 기록", 24))
        body.addView(label("증상 종류", 22, true))
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, SymptomType.entries.map { it.label }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).also { (it as TextView).textSize = 22f; it.setTextColor(Color.BLACK) }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).also {
                    (it as TextView).textSize = 24f; it.minimumHeight=dp(76);it.setTextColor(Color.BLACK); it.setBackgroundColor(Color.WHITE)
                    it.setPadding(dp(20), dp(16), dp(20), dp(16))
                }
        }
        val spinner = Spinner(this).apply {
            tag = "symptom_type"; setBackgroundColor(Color.WHITE); this.adapter = adapter
            setSelection(draft.type.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { draft.type = SymptomType.entries[position];captureDraft() }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        body.addView(spinner, LinearLayout.LayoutParams(-1, dp(76)))
        body.addView(label("어떤 상황에서 발생했나요?", 22, true))
        situationInput = input("예: 가속할 때 핸들이 진동합니다.", 1000).apply { tag = "situation"; setText(draft.situation) }
        body.addView(situationInput)
        body.addView(button("발견 시각: ${ConsultationSummary.time(draft.foundAt)}", "discovered_at") { pickTime() })
        body.addView(label("시각은 기기 설정(${ZoneId.systemDefault().id}) 기준입니다.", 18))
        body.addView(label("추가 메모 (선택)", 22, true))
        memoInput = input("추가로 전달할 내용을 적어 주세요.", 2000).apply { tag = "memo"; setText(draft.memo) }
        body.addView(memoInput)
        situationInput!!.addTextChangedListener(watcher{captureDraft()})
        memoInput!!.addTextChangedListener(watcher{captureDraft()})
        body.addView(button("이 차량 앱에 저장", "save_issue", primary = true, enabled = canWrite()) {
            val active=journal ?: return@button
            captureDraft()
            edit {
                val issue = service.record(draft.type, draft.situation, draft.foundAt, draft.memo,active.id)
                selectedIssue = issue.id; screen = Screen.DETAIL; draft = Draft(); journal=null;lastDraft=null;notice="문제를 이 차량 앱에 저장했습니다.";draw()
            }
        })
        cancelDraftButton(body)
    }

    private fun detail(body: LinearLayout) {
        val id = selectedIssue ?: run { screen = Screen.HOME; draw(); return }
        val issue = service.issue(id)
        body.addView(label("${issue.type.label} · ${issue.status.label}", 26, true))
        body.addView(label(issue.situation, 24))
        body.addView(label("발견 ${ConsultationSummary.time(issue.discoveredAt)}\n등록 ${issue.reporter.label} · ${ConsultationSummary.time(issue.recordedAt)}", 18))
        if (issue.memo.isNotBlank()) body.addView(label("추가 메모: ${issue.memo}", 20))
        body.addView(label("사용자 직접 기록 · 자동 감지나 AI 진단이 아닙니다.",20))
        if(!service.confirmed())body.addView(label("운전자를 선택하고 운전 시작을 누르면 읽음 확인과 조치를 기록할 수 있습니다.",22))
        else if (service.driver().id == issue.reporter.id) body.addView(label("이 문제를 직접 등록하셨습니다.", 20))
        else if (service.needsAcknowledgement(issue)) body.addView(button("내용을 읽었어요", "acknowledge", primary = true, enabled = canWrite()) {
            edit { service.acknowledge(id); draw(); toast("현재 운전자의 확인 이력을 저장했습니다.") }
        }) else body.addView(label("현재 운전자의 인계 확인이 저장되었습니다.", 20).apply { tag = "acknowledged" })
        body.addView(button("점검·조치 메모 남기기", "add_note", enabled = canWrite()) {
            beginAction("NOTE",issue)
        })
        body.addView(button("상태 변경하기", "change_status", enabled = canWrite()) { statusDialog(issue) })
        body.addView(button("같은 문제의 재발 기록하기", "recurrence", enabled = canWrite()) {
            beginAction("RECURRENCE",issue)
        })
        body.addView(button("이 문제의 정비 상담용 요약", "issue_summary", enabled = canEdit()) { openScreen(Screen.SUMMARY) })
        body.addView(label("확인·점검 이력", 24, true))
        draftBanner(body)
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
        body.addView(label("범위: ${if(selectedIssue==null) "전체 기록" else "선택한 문제"} ${issues.size}건 · 사용자 직접 기록",24,true))
        issues.forEach { issue ->
            val card = panel()
            card.addView(label(ConsultationSummary.build(issue, service.events(issue.id)), 21).apply { tag = "consultation_summary" })
            body.addView(card)
        }
    }

    private fun statusDialog(issue: VehicleIssue) {
        if(!canWrite())return
        val content=column().apply{setPadding(dp(24),dp(16),dp(24),dp(16))}
        val dialog=AlertDialog.Builder(this,dialogTheme()).setTitle("상태를 기록해 주세요").setView(ScrollView(this).apply{addView(content)}).create()
        IssueStatus.entries.forEach{status->content.addView(button((if(issue.status==status)"✓ " else "")+status.label,"status_${status.name}"){
            dialog.dismiss()
            if(status==IssueStatus.RESOLVED)confirm("해결 확인을 기록할까요?","직접 확인한 결과입니다. 앱이 안전이나 수리를 판정하지 않습니다."){service.changeStatus(issue.id,status);notice="상태를 저장했습니다.";draw()}
            else {service.changeStatus(issue.id,status);notice="상태를 저장했습니다.";draw()}
        })};activeDialog=dialog;dialog.show()
    }
    private fun beginAction(kind:String,issue:VehicleIssue){
        val old=service.draft()
        if(old!=null){notice="작성 중인 초안을 먼저 계속 작성하거나 취소해 주세요.";draw();return}
        journal=JournalDraft(java.util.UUID.randomUUID().toString(),service.driver().id,service.driver().label,kind,issue.id,issue.revision)
        openScreen(Screen.ACTION)
    }
    private fun dialogTheme()=if(dark)android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    private fun confirm(title:String,message:String,action:()->Unit){
        if(!canEdit())return
        val dialog=AlertDialog.Builder(this,dialogTheme()).setTitle(title).setMessage(message)
            .setPositiveButton("확인",null).setNegativeButton("취소",null).create()
        activeDialog=dialog;dialog.show()
        listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE).forEach{which->dialog.getButton(which).apply{minHeight=dp(76);minimumWidth=dp(76);textSize=24f}}
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{edit{if(!canEdit())throw EditingBlockedException();action();dialog.dismiss()}}
    }    private fun pickTime() {
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
        if(!canEdit()){toast("차량의 입력 허용 상태가 확인된 뒤 이용해 주세요.");return}
        captureDraft();positions[screen]=scrollView?.scrollY?:0
        if(screen!=target)history.addLast(screen)
        prepare();screen=target;draw()
    }
    private fun edit(action:()->Unit){
        try{action()}
        catch(e:IllegalArgumentException){toast(e.message?:"입력 내용을 확인해 주세요.")}
        catch(e:EditingBlockedException){toast(e.message?:"정차 후 이용해 주세요.")}
        catch(e:Exception){android.util.Log.e("Caritda","Local operation failed",e);toast("저장하지 못했습니다. 입력과 기존 기록을 보존했습니다. 다시 시도해 주세요.")}
    }
    private fun canEdit()=::safety.isInitialized && safety.state==EditingState.ALLOWED
    private fun canWrite()=canEdit()&&::service.isInitialized&&service.confirmed()
    private fun watcher(action:()->Unit)=object:TextWatcher{
        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){action()}
        override fun afterTextChanged(s:Editable?){}
    }
    private fun captureDraft(){
        if(!::service.isInitialized)return
        val current=journal?:return
        val next=when(screen){
            Screen.RECORD->{situationInput?.let{draft.situation=it.text.toString()};memoInput?.let{draft.memo=it.text.toString()};current.copy(type=draft.type,body=draft.situation,memo=draft.memo,foundAt=draft.foundAt)}
            Screen.ACTION->current.copy(body=actionField?.text?.toString()?:current.body)
            else->return
        }
        val dirty=lastDraft?.id==next.id || next.body.isNotEmpty() || next.memo.isNotEmpty() || next.type!=SymptomType.NOISE || next.foundAt!=current.foundAt
        journal=next
        if(dirty && next!=lastDraft){
            try{service.saveDraft(next);lastDraft=next}
            catch(e:Exception){notice="초안을 보존하지 못했습니다. 화면을 닫기 전에 다시 확인해 주세요.";feedback?.apply{text=notice;visibility=View.VISIBLE};android.util.Log.e("Caritda","Draft write failed",e)}
        }
    }
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(-1,-2)}
    private fun panel()=column().apply{
        setPadding(dp(24),dp(20),dp(24),dp(20));background=shape(cardColor)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,dp(12))}
    }
    private fun label(value:String,size:Int,bold:Boolean=false)=TextView(this).apply{
        text=value;textSize=size.toFloat();setTextColor(if(size<22)muted else ink)
        if(bold)setTypeface(typeface,Typeface.BOLD)
        setPadding(0,dp(8),0,dp(8));layoutParams=LinearLayout.LayoutParams(-1,-2)
    }
    private fun button(value:String,testTag:String,primary:Boolean=false,enabled:Boolean=true,action:()->Unit)=Button(this).apply{
        text=value;tag=testTag;textSize=24f;isAllCaps=false;minHeight=dp(76);minimumHeight=dp(76);minimumWidth=dp(76)
        setPadding(dp(20),dp(16),dp(20),dp(16));setTextColor(if(primary)actionColor else if(enabled)ink else muted)
        val normal=if(primary)accent else cardColor
        background=android.graphics.drawable.StateListDrawable().apply{
            addState(intArrayOf(android.R.attr.state_focused),shape(normal,if(primary)actionColor else accent))
            addState(intArrayOf(android.R.attr.state_selected),shape(normal,accent))
            addState(intArrayOf(),shape(normal))
        };isEnabled=enabled
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,dp(12))}
        setOnClickListener{edit{action()}}
    }
    private fun input(hintText:String,max:Int)=EditText(this).apply{
        hint=hintText;textSize=24f;minLines=3;maxLines=8;minHeight=dp(76)
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        filters=arrayOf(InputFilter.LengthFilter(max));setTextColor(ink);setHintTextColor(muted)
        setPadding(dp(20),dp(16),dp(20),dp(16));background=shape(cardColor,muted)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,dp(12))}
    }
    private fun shape(color:Int,stroke:Int?=null)=GradientDrawable().apply{setColor(color);cornerRadius=dp(16).toFloat();stroke?.let{setStroke(dp(3),it)}}
    private fun toast(message:String){notice=message;feedback?.apply{text=message;visibility=View.VISIBLE};Toast.makeText(this,message,Toast.LENGTH_LONG).show()}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
