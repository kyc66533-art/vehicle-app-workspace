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
import com.nextdrive.pleos.ui.*
import java.time.ZoneId
import java.util.Calendar

/** Manual journal and explicit one-shot vehicle reads. No diagnosis or remote transmission. */
class MainActivity : Activity() {
    private enum class Screen { HOME, ISSUES, DRIVERS, DRIVER_MANAGE, DRIVER_EDIT, RECORD, DETAIL, SUMMARY, VEHICLE, LOAD_DETAIL, ACTION, WELCOME, HELP, HELP_TOPIC }
    private lateinit var guide: GuideState
    private var guideReplay=false
    private data class RetainedSession(val driverId:String?)
    private var helpTopic=0
    private var pausedGuide: Screen?=null
    private var showUnavailable=false
    private val freshnessHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var freshnessKey=""
    private val freshnessTick=object:Runnable{
        override fun run(){
            if(screen==Screen.VEHICLE && !isFinishing && !isDestroyed){
                val key=listOf("EV_SOC","ODOMETER").map{kind->val item=observations.mostRecent(kind,true);val at=item?.receivedAt;"$kind:$at:${at?.let{System.currentTimeMillis()-it>=VehiclePresentation.STALE_AFTER_MS}}:${at?.let{it>System.currentTimeMillis()+60_000}}"}.joinToString()
                if(key!=freshnessKey){freshnessKey=key;positions[screen]=scrollView?.scrollY?:0;draw()}
            }
            freshnessHandler.postDelayed(this,1000)
        }
    }
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
        pleos = PleosReadClient(this, observations) { if (screen == Screen.VEHICLE || screen==Screen.LOAD_DETAIL) {positions[screen]=scrollView?.scrollY?:0;draw()} }
        safety = CarSafetyGate(this) { state ->
            if (state != displayedSafety) {
                captureDraft()
                displayedSafety = state
                if (state != EditingState.ALLOWED) {
                    activeDialog?.dismiss()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
                    if(screen in setOf(Screen.WELCOME,Screen.HELP,Screen.HELP_TOPIC)){positions[screen]=scrollView?.scrollY?:0;pausedGuide=screen}
                    screen = Screen.HOME
                }
                draw()
            }
        }
        service = CaritdaService(repository, { safety.state == EditingState.ALLOWED })
        service.restoreConfiguration((lastNonConfigurationInstance as? RetainedSession)?.driverId)
        journal = runCatching { service.draft() }.getOrElse { notice="초안을 복원하지 못했습니다. 기존 초안은 보존됩니다.";null }
        lastDraft = journal
        pendingDriver = journal?.ownerId ?: repository.currentDriver()?.id
        savedInstanceState?.let {
            pendingDriver=journal?.ownerId?:it.getString("pendingDriver",pendingDriver)
            editingDriver=it.getString("editingDriver")
            driverNameDraft=it.getString("driverNameDraft")
            notice=it.getString("notice",notice)
            guideReplay=it.getBoolean("guideReplay")
            helpTopic=it.getInt("helpTopic")
            pausedGuide=it.getString("pausedGuide")?.let{name->runCatching{Screen.valueOf(name)}.getOrNull()}
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
        guide=GuideState(getSharedPreferences("caritda_help",MODE_PRIVATE))
        @Suppress("DEPRECATION")
        val info=packageManager.getPackageInfo(packageName,0)
        val existing=info.firstInstallTime<info.lastUpdateTime || repository.drivers(true).isNotEmpty() || repository.issues().isNotEmpty() || journal!=null || observations.latest(1).isNotEmpty()
        if(guide.prepare(existing) && !guideReplay)screen=Screen.WELCOME
        else if(savedInstanceState!=null && lastNonConfigurationInstance!=null && savedInstanceState.getString("screen") in listOf("HELP","HELP_TOPIC","WELCOME"))screen=Screen.valueOf(savedInstanceState.getString("screen")!!)
        draw()
    }
    override fun onStart() {
        super.onStart()
        captureDraft()
        displayedSafety = EditingState.UNKNOWN
        draw()
        safety.start()
        freshnessHandler.removeCallbacks(freshnessTick);freshnessHandler.post(freshnessTick)
    }
    override fun onStop() { captureDraft(); activeDialog?.dismiss(); freshnessHandler.removeCallbacks(freshnessTick);pleos.stop(); safety.stop(); displayedSafety = EditingState.UNKNOWN; super.onStop() }
    override fun onDestroy() { repository.close(); observations.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft()
        outState.putString("screen", screen.name); outState.putString("issue", selectedIssue)
        outState.putBoolean("includeResolved", includeResolved); outState.putString("type", draft.type.name)
        outState.putString("situation", draft.situation); outState.putLong("foundAt", draft.foundAt); outState.putString("memo", draft.memo)
        outState.putString("pendingDriver",pendingDriver);outState.putString("editingDriver",editingDriver)
        outState.putBoolean("guideReplay",guideReplay);outState.putInt("helpTopic",helpTopic);outState.putString("pausedGuide",pausedGuide?.name)
        outState.putString("driverNameDraft",driverNameInput?.text?.toString()?:driverNameDraft);outState.putString("notice",notice)
        outState.putStringArrayList("history",ArrayList(history.map{it.name}))
        positions[screen]=scrollView?.scrollY?:0;positions.forEach{(target,position)->outState.putInt("position_${target.name}",position)}
        super.onSaveInstanceState(outState)
    }
    override fun onRetainNonConfigurationInstance(): Any = RetainedSession(if(service.confirmed())service.driver().id else null)
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
            val available=if(root.width>0)root.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat()
            val compact=available<600 || resources.configuration.fontScale>=1.5f
            val helpScreen=screen in setOf(Screen.HELP,Screen.HELP_TOPIC)
            if(screen!=Screen.WELCOME){
                val bar=LinearLayout(this).apply{orientation=if(available<480||resources.configuration.fontScale>=1.5f)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL}
                val title=label(if(helpScreen)"사용법" else "CARITDA",28,true)
                val help=button(if(helpScreen)"닫기" else "사용법",if(helpScreen)"close_help" else "help",enabled=canEdit()){
                    if(helpScreen)closeHelp() else openScreen(Screen.HELP)
                }
                if(bar.orientation==LinearLayout.VERTICAL){bar.addView(title);bar.addView(help)}else{bar.addView(title,LinearLayout.LayoutParams(0,-2,1f));bar.addView(help,LinearLayout.LayoutParams(-2,-2))}
                body.addView(bar)
            }
            if(displayedSafety!=EditingState.ALLOWED)body.addView(label(if(displayedSafety==EditingState.UNKNOWN)"화면 입력이 가능한지 확인 중이에요." else "지금은 조작할 수 없어요. 안전하게 주차한 뒤 이용해 주세요.",20).apply{tag="safety_status"})
            feedback=label(notice,22,true).apply{tag="feedback";visibility=if(notice.isBlank()||helpScreen||screen==Screen.WELCOME)View.GONE else View.VISIBLE};body.addView(feedback)
            if(screen!=Screen.WELCOME && !helpScreen){
                val recent=repository.currentDriver()
                body.addView(button(if(service.confirmed())"기록 이름: ${service.driver().label} · 변경" else "기록 이름 선택 · ${recent?.label ?: "아직 선택하지 않았어요"}","change_driver",enabled=canEdit()){
                    if(service.draft()!=null&&service.confirmed()){notice="작성 중인 내용을 먼저 저장하거나 취소해 주세요.";draw()}else{pendingDriver=service.draft()?.ownerId?:recent?.id;openScreen(Screen.DRIVERS)}
                })
                if(compact||screen==Screen.HOME)body.addView(button("메뉴","menu",enabled=canEdit()){menuOpen=!menuOpen;draw()})
                if((!compact&&screen!=Screen.HOME)||menuOpen){
                    val nav=LinearLayout(this).apply{orientation=if(compact||screen==Screen.HOME)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL}
                    listOf("홈" to Screen.HOME,"기록" to Screen.ISSUES,"차량 정보" to Screen.VEHICLE).forEach{(name,target)->
                        val item=button(if(screen==target)"✓ $name" else name,"nav_${target.name}",enabled=canEdit()){menuOpen=false;openScreen(target)}
                        if(nav.orientation==LinearLayout.VERTICAL)nav.addView(item)else nav.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(8),dp(8),dp(8),dp(8))})
                    };body.addView(nav)
                }
                if(screen !in setOf(Screen.HOME,Screen.ISSUES,Screen.VEHICLE,Screen.DRIVERS))body.addView(button("뒤로","home"){onBackPressed()})
            }
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
                Screen.LOAD_DETAIL->loadDetails(body)
                Screen.WELCOME->welcome(body)
                Screen.HELP->help(body)
                Screen.HELP_TOPIC->helpTopic(body)
                Screen.ACTION->actionForm(body)
            }
            scroll.post{if(screen!=Screen.RECORD && screen!=Screen.ACTION)scroll.scrollTo(0,positions[screen]?:0)}
        } catch(e:Exception) {
            android.util.Log.e("Caritda","Read failed",e)
            body.addView(label("기록을 불러오지 못했습니다. 기존 데이터는 보존됩니다.",24,true))
            body.addView(button("다시 확인하기","retry"){draw()})
        }
    }
    private fun themeSetting(body:LinearLayout){
        val preference=getPreferences(MODE_PRIVATE).getString("theme","system")
            body.addView(button("화면 테마: ${when(preference){"dark"->"어둡게";"light"->"밝게";else->"시스템 설정"}}","theme",enabled=canEdit()){
                check(getPreferences(MODE_PRIVATE).edit().putString("theme",when(preference){"dark"->"light";"light"->"system";else->"dark"}).commit())
                draw()
            })
    }
    private fun statusText(status:IssueStatus)=when(status){IssueStatus.UNRESOLVED->"관리 중인 기록";IssueStatus.RESOLVED->"사용자가 해결 확인";else->status.label}
    private fun sectionName()=when(screen){Screen.HOME->"홈";Screen.ISSUES->"문제";Screen.VEHICLE->"차량";Screen.DRIVERS,Screen.DRIVER_EDIT->"운전자";else->"기록"}
    private fun home(body:LinearLayout){
        val all=service.issues();val managed=all.filter{it.status!=IssueStatus.RESOLVED}
        val unread=if(service.confirmed())managed.count{service.needsAcknowledgement(it)} else null
        val scope=panel()
        scope.addView(label("지금 확인할 수 있는 정보",24,true))
        scope.addView(label("지원되는 충전량·주행거리와 직접 남긴 기록을 볼 수 있어요.",24))
        scope.addView(label("지금은 차량의 이상을 자동으로 찾아주지는 않아요.",22))
        body.addView(scope)
        body.addView(label(when{all.isEmpty()->"아직 남긴 기록이 없어요.";unread!=null&&unread>0->"아직 읽지 않은 전달 기록 ${unread}건";managed.isNotEmpty()->"관리 중인 기록 ${managed.size}건";else->"완료한 기록 ${all.size}건"},26,true).apply{tag="home_counts"})
        body.addView(button(if(all.isEmpty())"기록 보기" else "기록 보기 · ${all.size}건","all_issues",primary=all.isNotEmpty(),enabled=canEdit()){
            openScreen(Screen.ISSUES){includeResolved=managed.isEmpty()&&all.isNotEmpty();positions[Screen.ISSUES]=0}
        })
        body.addView(button("문제 남기기","new_issue",enabled=canEdit()){beginRecord()})
        body.addView(button("차량 정보","vehicle_data",primary=all.isEmpty(),enabled=canEdit()){openScreen(Screen.VEHICLE)})
        if(!service.confirmed())body.addView(label("기록을 남기려면 먼저 기록할 이름을 선택해 주세요.",20))
        draftBanner(body)
        pausedGuide?.let{paused->body.addView(button("안내 이어보기","resume_help",enabled=canEdit()){pausedGuide=null;screen=paused;draw()})}
    }
    private fun issues(body:LinearLayout){
        body.addView(label("저장된 기록",28,true))
        body.addView(button("문제 남기기","new_issue",primary=true,enabled=canWrite()){beginRecord()})
        draftBanner(body)
        val all=service.issues();val chosen=all.filter{(it.status==IssueStatus.RESOLVED)==includeResolved}
        body.addView(button(if(includeResolved)"✓ 완료한 기록 ${chosen.size}건 · 관리 중 보기" else "✓ 관리 중인 기록 ${chosen.size}건 · 완료한 기록 보기","toggle_resolved",enabled=canEdit()){includeResolved=!includeResolved;positions[Screen.ISSUES]=0;draw()})
        body.addView(label("아직 읽지 않은 전달 기록부터 보여드려요.",20))
        if(chosen.isEmpty())body.addView(label("아직 남긴 기록이 없어요. 차량이 정상이라는 뜻은 아니에요.",24))
        chosen.sortedBy{if(service.confirmed()&&service.needsAcknowledgement(it))0 else 1}.forEach{issueCard(body,it)}
        body.addView(button("정비 상담용 요약 · 전체 ${all.size}건","all_summary",enabled=canEdit()&&all.isNotEmpty()){selectedIssue=null;openScreen(Screen.SUMMARY)})
    }
    private fun issueCard(body:LinearLayout,issue:VehicleIssue){
        val card=panel();card.addView(label("사용자 직접 기록 · ${statusText(issue.status)}",20))
        card.addView(label("${issue.type.label} · ${issue.situation.take(100)}",24,true))
        card.addView(label("발견 ${ConsultationSummary.time(issue.discoveredAt)} · 등록 ${issue.reporter.label}",20))
        if(service.confirmed()&&service.needsAcknowledgement(issue))card.addView(label("${service.driver().label}님의 읽음 확인이 필요합니다.",22,true))
        card.addView(button("문제와 기록 보기","issue_${issue.id}",enabled=canEdit()){selectedIssue=issue.id;openScreen(Screen.DETAIL)})
        body.addView(card)
    }
    private fun driverScreen(body:LinearLayout){
        body.addView(label("기록할 사람 선택",28,true))
        body.addView(label("기록에 사용할 이름이나 별명을 골라 주세요.",20))
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
        root.addView(label("이 이름으로 기록을 남겨요",20))
        root.addView(button("${candidate?.label ?: "운전자 선택 후"} · 운전 시작","start_driving",primary=true,enabled=canEdit()&&candidate!=null){
            edit{service.selectDriver(candidate!!);notice="";history.clear();screen=Screen.HOME;draw()}
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
    private fun vehicleScreen(body:LinearLayout){
        body.addView(label("차량 정보",28,true))
        body.addView(label(if(pleos.busy)"차량값을 불러오는 중이에요." else "버튼을 누르면 지금 받을 수 있는 값을 불러와요.",22).apply{tag="load_status"})
        body.addView(button("차량값 불러오기","read_vehicle",primary=true,enabled=canEdit()&&!pleos.busy){edit{pleos.readOnce()}})
        listOf("EV_SOC" to "배터리 충전량","ODOMETER" to "누적 주행거리").forEach{(kind,name)->
            val view=VehiclePresentation.card(kind,observations.mostRecent(kind),observations.mostRecent(kind,true),System.currentTimeMillis())
            val card=panel();card.addView(label(name,24,true))
            card.addView(label(view.value,32,true).apply{tag="value_$kind"})
            card.addView(label(view.time,22).apply{tag="time_$kind"})
            card.addView(label(view.source,20))
            if(view.attempt.isNotEmpty())card.addView(label(view.attempt,22).apply{tag="attempt_$kind"})
            body.addView(card)
        }
        body.addView(button(if(showUnavailable)"제공되지 않는 항목 접기" else "제공되지 않는 항목","unavailable",enabled=canEdit()){showUnavailable=!showUnavailable;draw()})
        if(showUnavailable){
            val fuel=VehiclePresentation.card("FUEL_PERCENT",observations.mostRecent("FUEL_PERCENT"),observations.mostRecent("FUEL_PERCENT",true),System.currentTimeMillis())
            body.addView(label("연료 잔량 · ${fuel.attempt.ifEmpty{fuel.value}}",24).apply{tag="fuel_status"})
        }
        body.addView(button("불러오기 상세","load_details",enabled=canEdit()){openScreen(Screen.LOAD_DETAIL)})
        body.addView(label("이 값만으로 고장 여부를 판단하지 않아요. 자동으로 계속 확인하는 기능은 아니에요.",20))
    }
    private fun loadDetails(body:LinearLayout){
        body.addView(label("불러오기 상세",28,true))
        body.addView(label("Vehicle SDK 2.3.2 · ${pleos.message}",22))
        body.addView(label("아래는 저장된 불러오기 기록이에요. 기기 시간대: ${ZoneId.systemDefault().id}",20))
        observations.latest(100).forEach{item->
            val card=panel()
            card.addView(label("${item.kind} · ${item.status}",24,true))
            card.addView(label("원본: ${item.raw ?: "미수신"} · 단위: ${item.unit ?: "없음"}\n지원: ${item.supported} · 유효: ${item.valid}\n출처 코드: ${item.source}\n수신 시각: ${item.receivedAt ?: "없음"}\n상태 기록 시각: ${item.recordedAt}\n세션 ID: ${item.session}",22))
            card.addView(label(item.detail,22));body.addView(card)
        }
    }
    private fun welcome(body:LinearLayout){
        body.addView(label("내 차 기록을 한곳에서",32,true).apply{tag="welcome_title"})
        body.addView(label("차에서 불러온 값과 직접 남긴 문제를 함께 볼 수 있어요.",24))
        body.addView(label("지금은 차량의 이상을 자동으로 찾아주지는 않아요.",24))
        if(guideReplay){body.addView(button("닫기","close_guide",primary=true,enabled=canEdit()){onBackPressed()})}
        else {
            body.addView(button("운전자 선택","guide_choose",primary=true,enabled=canEdit()){finishGuide(false)})
            body.addView(button("안내 건너뛰기","guide_skip",enabled=canEdit()){finishGuide(true)})
        }
    }
    private fun finishGuide(skipped:Boolean){
        if(!canEdit())throw EditingBlockedException()
        guide.finish(skipped,canEdit());history.clear();screen=Screen.DRIVERS;guideReplay=false;draw()
    }
    private fun closeHelp(){
        if(screen==Screen.HELP_TOPIC) {screen=Screen.HELP;if(history.peekLast()==Screen.HELP)history.removeLast()}
        if(history.isNotEmpty())screen=history.removeLast() else screen=Screen.HOME
        guideReplay=false;draw()
    }
    private val helpTitles=listOf("기록할 사람 고르기","차량값 불러보기","느낀 문제 남기기","다른 사람의 기록 읽기","정비할 때 기록 보여주기","자동 확인 기능의 현재 범위")
    private fun help(body:LinearLayout){
        body.addView(button("처음 안내 다시 보기","replay_guide",enabled=canEdit()){guideReplay=true;openScreen(Screen.WELCOME)})
        helpTitles.forEachIndexed{index,title->body.addView(button(title,"help_topic_$index",enabled=canEdit()){helpTopic=index;openScreen(Screen.HELP_TOPIC)})}
        themeSetting(body)
    }
    private fun helpTopic(body:LinearLayout){
        val lines=listOf(
            listOf("내가 남긴 기록을 다른 사람이 알아볼 수 있게 이름을 정해요.","기록 이름 변경 → 이름 추가 또는 카드 선택 → 운전 시작을 눌러 주세요.","이 이름으로 기록을 남겨요. 차량 시동·계정·시트 설정은 바뀌지 않아요."),
            listOf("지원되는 충전량과 주행거리를 불러볼 수 있어요.","차량 정보 → 차량값 불러오기를 눌러 주세요. 받은 시각과 출처를 함께 확인해 주세요.","자동으로 계속 수집하거나 고장 여부를 판단하지는 않아요. 자세한 연결 기록은 불러오기 상세에 있어요."),
            listOf("차에서 느낀 소리나 떨림을 남겨 보세요. 고장 이름을 몰라도 괜찮아요.","문제 남기기 → 증상과 상황 입력 → 이 차량 앱에 저장을 눌러 주세요.","작성 중인 내용은 원래 이름으로 이어 쓸 수 있어요. 이름을 바꾸기 전에는 저장하거나 작성을 취소해 주세요."),
            listOf("같은 앱을 쓰는 다른 사람이 남긴 내용을 살펴봐요.","기록 보기 → 내용을 열고 읽었다면 내용을 읽었어요를 눌러 주세요.","읽음은 수리가 끝났다는 뜻이 아니에요. 이름을 바꾸거나 상세를 열기만 해서는 읽음으로 저장하지 않아요."),
            listOf("어떤 상황과 점검 기록이 있었는지 정비 상담 때 보여줄 수 있어요.","기록 → 내용을 열고 이 문제의 정비 상담용 요약을 눌러 주세요. 전체 요약은 기록 목록에 있어요.","이 화면은 고장 진단이 아니며 외부 정비소로 자동 전송되지 않아요."),
            listOf("지금은 차량의 이상을 자동으로 찾아주지는 않아요.","차량값은 버튼을 누를 때만 불러오고 문제는 직접 남겨요.","저장된 기록이 없다고 차량이 정상이라는 뜻은 아니에요.")
        )
        body.addView(label(helpTitles[helpTopic],28,true))
        lines[helpTopic].forEach{body.addView(label(it,24))}
        body.addView(button("사용법 목록","help_contents",enabled=canEdit()){onBackPressed()})
    }
    private fun recordForm(body: LinearLayout) {
        body.addView(label("어떤 문제를 느끼셨나요?", 28, true))
        body.addView(label("작성자: ${journal?.ownerName ?: service.driver().label} · 사용자 직접 기록", 24))
        body.addView(label("고장 이름을 몰라도 괜찮아요.", 22))
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
        body.addView(label("${issue.type.label} · ${statusText(issue.status)}", 26, true))
        body.addView(label(issue.situation, 24))
        body.addView(label("발견 ${ConsultationSummary.time(issue.discoveredAt)}\n등록 ${issue.reporter.label} · ${ConsultationSummary.time(issue.recordedAt)}", 18))
        if (issue.memo.isNotBlank()) body.addView(label("추가 메모: ${issue.memo}", 20))
        body.addView(label("직접 남긴 기록이에요. 읽음은 수리 완료와 달라요.",20))
        if(!service.confirmed())body.addView(label("운전자를 선택하고 운전 시작을 누르면 읽음 확인과 조치를 기록할 수 있습니다.",22))
        else if (service.driver().id == issue.reporter.id) body.addView(label("이 문제를 직접 등록하셨습니다.", 20))
        else if (service.needsAcknowledgement(issue)) body.addView(button("내용을 읽었어요", "acknowledge", primary = true, enabled = canWrite()) {
            edit { service.acknowledge(id); draw(); toast("현재 운전자의 확인 이력을 저장했습니다.") }
        }) else body.addView(label("현재 운전자의 인계 확인이 저장되었습니다.", 20).apply { tag = "acknowledged" })
        body.addView(button("점검하거나 한 일 남기기", "add_note", enabled = canWrite()) {
            beginAction("NOTE",issue)
        })
        body.addView(button("상태 변경하기", "change_status", enabled = canWrite()) { statusDialog(issue) })
        body.addView(button("다시 발생했어요", "recurrence", enabled = canWrite()) {
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
            card.addView(label(ConsultationSummary.build(issue, service.events(issue.id),::statusText), 24).apply { tag = "consultation_summary" })
            body.addView(card)
        }
    }

    private fun statusDialog(issue: VehicleIssue) {
        if(!canWrite())return
        val content=column().apply{setPadding(dp(24),dp(16),dp(24),dp(16))}
        val dialog=AlertDialog.Builder(this,dialogTheme()).setTitle("상태를 기록해 주세요").setView(ScrollView(this).apply{addView(content)}).create()
        IssueStatus.entries.forEach{status->content.addView(button((if(issue.status==status)"✓ " else "")+statusText(status),"status_${status.name}"){
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
        catch(e:Exception){android.util.Log.e("Caritda","Local operation failed",e);toast("저장하지 못했어요. 화면을 닫기 전에 작성 내용을 확인해 주세요.")}
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
