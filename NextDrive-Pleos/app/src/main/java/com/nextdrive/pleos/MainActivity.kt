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
import com.nextdrive.pleos.analytics.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.integration.*
import java.time.ZoneId
import java.util.Calendar

/** Manual journal and explicit one-shot vehicle reads. No diagnosis or remote transmission. */
class MainActivity : Activity() {
    private enum class Screen { HOME, ISSUES, DRIVERS, DRIVER_MANAGE, DRIVER_EDIT, RECORD, DETAIL, SUMMARY, VEHICLE, LOAD_DETAIL, ACTION, WELCOME, HELP, HELP_TOPIC, SNAPSHOTS, SNAPSHOT_DETAIL, HISTORY, MENU, VEHICLE_SETTINGS, SOC_NOTICES, ANALYTICS, AI_PREVIEW, HANDOVER }
    private lateinit var guide: GuideState
    private var guideReplay=false
    private data class RetainedSession(val driverId:String?,val entry:EntryReadState)
    private var entry=EntryReadState()
    private var visible=false
    private var aiHomeRequested=false
    private lateinit var snapshots:SnapshotStore
    private lateinit var vehicleDisplay:VehicleDisplaySettings
    private lateinit var lowSoc:LowSocStore
    private lateinit var socObserver:PleosSocObserver
    private lateinit var attentionEvents:AttentionEventStore
    private lateinit var attentionObserver:PleosAttentionObserver
    private var deferredAttention:ObservationRef?=null
    private var removeVoiceAuth:(()->Unit)?=null
    private var homeVoice:com.nextdrive.pleos.voice.HomeVoiceRuntime?=null
    private var homeVoiceLastIssue:String?=null
    private fun paintHomeVoice(){
        val voice=homeVoice?:return
        root.findViewWithTag<TextView>("home_voice_status")?.text=voice.title
        root.findViewWithTag<TextView>("home_voice_detail")?.text=voice.detail
        root.findViewWithTag<Button>("home_voice_toggle")?.apply{
            visibility=if(voice.autoEnabled)View.VISIBLE else View.GONE
            text=if(voice.active)"음성 대기 끄기"else"다시 대기"
        }
        val saved=voice.controller?.issueId
        if(saved!=null&&saved!=homeVoiceLastIssue){homeVoiceLastIssue=saved;notice="음성으로 말씀하신 문제를 기록했어요.";root.post{if(visible&&screen==Screen.HOME)draw()}}
    }
    private fun receiveAttention(ref:ObservationRef){
        val event=attentionEvents.accept(ref,lowSoc)?:return
        if(event.phase!="ACTIVE")return
        if(com.nextdrive.pleos.ai.auth.FirebaseSessionProvider.configState=="READY"&&!com.nextdrive.pleos.ai.auth.FirebaseSessionProvider.initialized){deferredAttention=ref;return}
        val account=com.nextdrive.pleos.ai.auth.FirebaseSessionProvider.subject().orEmpty()
        val owner=if(service.confirmed())service.driver().id else ""
        val permit=com.nextdrive.pleos.voice.VoiceSettings(this,fixturePrefix?.let{"$it-settings"}?:"voice-automation-v1").read(owner,account)
        val fresh=DriveCheck.assess(event.kind,observations.mostRecent(event.kind),observations.mostRecent(event.kind,true),lowSoc.policy()).attention
        val eligible=fresh&&visible&&canWrite()&&screen==Screen.HOME&&service.draft()==null&&!speechInput.busy&&!speechOutput.busy&&permit.alerts&&permit.speak&&permit.history
        if(homeVoice?.autoEnabled==true){
            val ready=eligible&&homeVoice?.controller?.effectsAllowed()!=true
            if(!attentionEvents.claimSpeech(event.id,ready))return
            val text=if(event.kind=="EV_SOC")"배터리가 설정한 기준보다 낮아졌어요. 고장 판정은 아니에요."else"차량에서 ${DriveCheck.assess(event.kind,observations.mostRecent(event.kind),observations.mostRecent(event.kind,true),lowSoc.policy()).title} 안내를 받았어요. 차량 표시와 함께 확인해 주세요."
            if(homeVoice?.announce(event,event.kind,text)!=true)attentionEvents.finishSpeech(event.id,"SKIPPED")
            return
        }
        if(!attentionEvents.claimSpeech(event.id,eligible))return
        startActivity(android.content.Intent(this,VoiceConversationActivity::class.java).putExtra("driver_id",owner)
            .putExtra("validation_fixture",fixturePrefix).putExtra("attention_id",event.id).putExtra("attention_kind",event.kind).putExtra("auto_attention",true))
    }
    private var socThresholdInput:EditText?=null
    private var socThresholdDraft:String?=null
    private var socTechnical=false
    private var socHistory=false
    private var socHistoryLimit=20
    private var analysisKind="EV_SOC"
    private var analysisDays=1
    private var analysisDetail=false
    private var analysisPage=0
    private var analysisBusy=false
    private var analysisDirty=false
    private var analysisError=""
    private val analysisWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var analysisResults=emptyMap<String,VehicleAnalysis>()
    private var aiConsent:AnalysisConsent?=null
    private var aiPreview=""
    private var palette=CaritdaPalette.of(false)
    private val ui get()=CaritdaComponents(this,palette)
    private var selectedSnapshot:String?=null
    private var selectedCard:String?=null
    private var historyPage=0
    private var technicalExpanded=false
    private var recordExtras=false
    private var hiddenSnapshots=false
    private var snapshotError=""
    private var helpTopic=0
    private var pausedGuide: Screen?=null
    private var showUnavailable=false
    private var showHealth=false
    private val expandedSections=mutableSetOf<String>()
    private var speechFormVisible=false
    private val speechOutput by lazy{SpeechOutput(this){visible&&canEdit()}}
    private val speechInput by lazy{SpeechInput(this){visible&&canWrite()}}
    private val locationReader by lazy{OneShotLocation(this){visible&&canWrite()}}
    private val navigation by lazy{InspectionNavigation(this){visible&&canEdit()}}
    private val repairSearch by lazy{InspectionSearch(this){visible&&canWrite()}}
    private var repairSearchStatus=""
    private var repairSearchScope=""
    private var locationHistory=false
    private var locationHistoryLimit=3
    private var navigationFields:List<EditText>?=null
    private var navigationDraft=listOf("","","")
    private var navigationEvents=emptyList<InspectionNavigation.Event>()
    private var healthFreshness=emptyList<Long>()
    private var permissionAction:(()->Unit)?=null
    // Instrumentation can use an isolated database family, never either app's existing records.
    private val fixturePrefix by lazy { intent.getStringExtra("validation_fixture")?.takeIf {
        packageName=="com.nextdrive.pleos.validation" && Regex("gleo-ui-[a-f0-9-]{36}").matches(it)
    } }
    private fun repairSearchContext()=if(canWrite())service.driver().id+":"+selectedIssue.orEmpty() else ""
    private fun stopAssistance(){repairSearch.stop();homeVoice?.pause();speechOutput.stop();speechInput.stop();locationReader.stop()
        if(navigation.busy)navigationEvents=navigation.events+InspectionNavigation.Event("NAVI_MONITOR_STOPPED",System.currentTimeMillis())
        navigation.stop();permissionAction=null}
    private fun locationStore()=IssueLocationStore(this,fixturePrefix?.let{it+"-locations.db"}?:"caritda-issue-location.db")
    private fun permission(name:String,action:()->Unit){
        if(!canEdit())return
        if(checkSelfPermission(name)==android.content.pm.PackageManager.PERMISSION_GRANTED)action()
        else{permissionAction=action;requestPermissions(arrayOf(name),701)}
    }
    override fun onRequestPermissionsResult(code:Int,names:Array<out String>,results:IntArray){
        super.onRequestPermissionsResult(code,names,results)
        if(code==701){val action=permissionAction;permissionAction=null;if(results.isNotEmpty()&&results.all{it==android.content.pm.PackageManager.PERMISSION_GRANTED}&&canEdit())action?.invoke()else toast("권한을 허용하지 않았어요. 기존 기록은 그대로예요.")}
    }
    private fun readAloud(text:String){if(!canEdit())return;speechOutput.speak(text.take(600)){message->notice=message;feedback?.apply{setText(message);visibility=View.VISIBLE}}}
    private fun voiceDraft(){
        if(!canWrite()||!captureDraft())return
        speechOutput.stop()
        permission(android.Manifest.permission.RECORD_AUDIO){
            val editing=journal?:return@permission
            val field=situationInput?:return@permission
            val delivery=SpeechDraftDelivery(editing.id,editing.ownerId,field.text.toString())
            notice="기기에서 음성 입력을 준비하고 있어요. 자동으로 저장하지 않아요.";feedback?.apply{text=notice;visibility=View.VISIBLE}
            speechInput.listen({message->notice=message;feedback?.apply{text=message;visibility=View.VISIBLE}},{text->
                val current=situationInput
                val merged=delivery.accept(text,journal?.id,journal?.ownerId,current?.text?.toString().orEmpty(),
                    visible&&canWrite()&&screen==Screen.RECORD&&current===field)
                if(merged!=null&&current!=null){
                    // SpeechInput delivers only after both SDK final and ended. No modal confirmation
                    // and no issue save: the driver can edit this draft and use the existing Save action.
                    current.setText(merged);current.setSelection(current.length())
                    if(captureDraft())notice="음성을 입력란에 반영했어요. 내용을 확인한 뒤 저장해 주세요."
                }else if(visible&&screen==Screen.RECORD){
                    notice="작성 내용이 바뀌었거나 길이가 길어 음성을 덧붙이지 않았어요. 기존 입력과 인식한 문장을 확인해 주세요."
                }
                feedback?.apply{setText(notice);visibility=View.VISIBLE}
            })
        }
    }
    private fun attachLocation(id:String){
        if(!canWrite()||!service.canManage(service.issue(id)))return
        confirm("현재 위치를 확인할까요?","기기에서 마지막으로 확인한 위치를 가져와요. 증상 발생 당시 위치와 다를 수 있어요. 이 앱에만 저장하며 AI에는 보내지 않아요."){
            permission("pleos.car.permission.FUSED_LOCATION"){
                if(screen!=Screen.DETAIL||selectedIssue!=id||!service.canManage(service.issue(id)))return@permission
                notice="위치를 확인하고 있어요.";feedback?.apply{text=notice;visibility=View.VISIBLE}
                locationReader.read{value,code->
                    if(screen!=Screen.DETAIL||selectedIssue!=id)return@read
                    if(value==null){notice=LocationPresentation.failure(code)+" 확인 코드: "+code;draw()}
                    else if(canWrite()&&service.canManage(service.issue(id))){
                        draw()
                        confirm("이 위치를 함께 저장할까요?",LocationPresentation.text(value,vehicleDisplay::time)){
                            check(screen==Screen.DETAIL&&selectedIssue==id&&service.canManage(service.issue(id)))
                            val added=locationStore().use{val created=it.save(id,value);check(it.contains(id,value));created}
                            notice=if(added)"위치가 기록에 저장됐어요."else"이미 연결한 같은 위치예요. 중복 저장하지 않았어요.";draw()
                        }
                    }
                }
                draw()
            }
        }
    }
    private fun linkedLocations(body:LinearLayout,id:String){
        locationStore().use{store->
            val latest=store.latest(id)?:return
            body.addView(label("위치가 기록에 저장됐어요.",24,true).apply{tag="linked_location"})
            body.addView(label("저장 ${vehicleDisplay.time(latest.receivedAt)}\n이 기기가 마지막으로 확인한 위치예요. 문제가 생긴 곳과 다를 수 있어요.",20))
            fold(body,"위치 자세히 보기","location_details"){details->
                details.addView(label(LocationPresentation.text(latest,vehicleDisplay::time),20))
                details.addView(ui.expandable("위치 연결 이력 · ${store.count(id)}건","location_history",locationHistory,canEdit()){edit{locationHistory=!locationHistory;draw()}})
                if(locationHistory){
                    store.history(id,locationHistoryLimit).forEach{item->details.addView(ui.state("사용자가 연결한 위치",LocationPresentation.text(item,vehicleDisplay::time)).apply{tag="location_history_item"})}
                    if(store.count(id)>locationHistoryLimit&&locationHistoryLimit<200)details.addView(button("위치 이력 더 보기","location_history_more",enabled=canEdit()){locationHistoryLimit=(locationHistoryLimit+20).coerceAtMost(200);draw()})
                }
            }
        }
    }

    private fun findRepairInNavigation(){
        if(!visible||screen!=Screen.SUMMARY||!canWrite()||navigation.busy||repairSearch.busy)return
        repairSearchScope=repairSearchContext()
        val scope=repairSearchScope
        repairSearch.request{code->
            if(visible&&screen==Screen.SUMMARY&&scope==repairSearchContext()){
                repairSearchStatus=InspectionSearch.message(code)
                positions[screen]=scrollView?.scrollY?:0;draw()
            }
        }
    }

    private fun repairSearchPanel(body:LinearLayout){
        val section=panel()
        section.addView(ui.sectionTitle("가까운 정비소 찾기",HmiIcon.VEHICLE))
        section.addView(label("내비에서 ‘자동차 정비소’를 검색해요. 장소를 골라 길 안내를 시작해 주세요.",22))
        section.addView(ui.action(if(repairSearch.busy)"내비게이션 연결 중…"else"내비에서 정비소 찾기",
            "repair_nearby_search",ActionStyle.PRIMARY,canWrite()&&!repairSearch.busy&&!navigation.busy,HmiIcon.VEHICLE){findRepairInNavigation()})
        if(repairSearchStatus.isNotBlank())section.addView(label(repairSearchStatus,20).apply{tag="repair_search_status"})
        section.addView(label("문제 기록은 전송하지 않아요. 장소 선택과 길 안내는 내비게이션에서 진행해요.",20))
        body.addView(section)
    }

    private fun destination(){
        if(!canEdit()||navigation.busy||repairSearch.busy)return
        val fields=column();val name=input("정비 장소 이름",80);val lat=input("위도 (-90~90)",24);val lon=input("경도 (-180~180)",24)
        listOf(name,lat,lon).forEachIndexed{i,field->field.setText(navigationDraft[i]);field.tag=listOf("navigation_name","navigation_latitude","navigation_longitude")[i]}
        navigationFields=listOf(name,lat,lon)
        fields.addView(label("정비소 위치(위도·경도)를 직접 넣어 주세요. 예약은 되지 않아요.",22));listOf(name,lat,lon).forEach{fields.addView(it)}
        val dialog=AlertDialog.Builder(this,dialogTheme()).setTitle("정비 장소 길찾기").setView(ScrollView(this).apply{addView(fields)}).setNegativeButton("취소",null).setPositiveButton("경로 요청",null).create()
        activeDialog=dialog;dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val latitude=lat.text.toString().toDoubleOrNull();val longitude=lon.text.toString().toDoubleOrNull();val title=name.text.toString().trim()
            if(latitude==null||longitude==null||!latitude.isFinite()||!longitude.isFinite()||latitude !in -90.0..90.0||longitude !in -180.0..180.0||title.isBlank()){lat.error="장소 이름과 유효한 좌표를 확인해 주세요.";return@setOnClickListener}
            if(!canEdit())return@setOnClickListener
            navigationDraft=listOf(title,lat.text.toString(),lon.text.toString());navigationFields=null
            dialog.dismiss();navigation.request(latitude,longitude,title){code->
                notice=InspectionNavigation.message(code);navigationEvents=navigation.events
                feedback?.apply{text=notice;visibility=View.VISIBLE}
                if(screen==Screen.SUMMARY){positions[screen]=scrollView?.scrollY?:0;draw()}
            }
        }
        dialog.setOnDismissListener{navigationFields?.takeIf{it.firstOrNull()===name}?.let{navigationDraft=it.map{field->field.text.toString()};navigationFields=null}}
    }
    private val freshnessHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var freshnessKey=""
    private val freshnessTick=object:Runnable{
        override fun run(){
            if(screen in setOf(Screen.HOME,Screen.VEHICLE,Screen.SNAPSHOTS,Screen.SNAPSHOT_DETAIL,Screen.HISTORY,Screen.DETAIL,Screen.SUMMARY,Screen.SOC_NOTICES,Screen.ANALYTICS) && !isFinishing && !isDestroyed){
                val key=freshnessMarker()
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
    private lateinit var page:CaritdaPage
    private var screen = Screen.HOME
    private var selectedIssue: String? = null
    private var recordFilter = RecordFilter.ALL
    private var recordQuery = ""
    private var searchText = ""
    private var searchInput: EditText? = null
    private data class RecordBrowse(val filter:RecordFilter,val query:String,val text:String,val position:Int)
    private var previousRecordBrowse:RecordBrowse?=null
    private var pendingRecordFilter: RecordFilter? = null
    private var recordSaving = false
    private var draft = Draft()
    private var journal: JournalDraft? = null
    private var pendingDriver: String? = null
    private var editingDriver: String? = null
    private var driverNameDraft: String? = null
    private var driverNameInput: EditText? = null
    private var actionField: EditText? = null
    private var notice = ""
    private var feedback: TextView? = null
    private var menuReturn:Screen?=null
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
        com.nextdrive.pleos.ai.auth.FirebaseSessionProvider.prepare(this)
        aiHomeRequested=intent.getBooleanExtra("caritda_home",false)
        root = column().apply { setBackgroundColor(Color.rgb(14, 23, 34)); setPadding(dp(24), dp(16), dp(24), dp(16)) }
        page=CaritdaPage(this,root);setContentView(page, ViewGroup.LayoutParams(-1,-1))
        root.addOnLayoutChangeListener { _,l,t,r,b,oldL,oldT,oldR,oldB ->
            if(oldR>oldL&&(r-l!=oldR-oldL||(b-t!=oldB-oldT&&currentFocus !is EditText)))root.post{draw()}
        }
        repository = if(fixturePrefix==null)SqliteIssueRepository(this)else SqliteIssueRepository(this,fixturePrefix+".db")
        observations = if(fixturePrefix==null)ObservationStore(this)else ObservationStore(this,fixturePrefix+"-observations.db")
        snapshots=if(fixturePrefix==null)SnapshotStore(this)else SnapshotStore(this,fixturePrefix+"-snapshots.db")
        vehicleDisplay=VehicleDisplaySettings(getSharedPreferences(fixturePrefix?.let{it+"-vehicle-display"}?:"caritda_vehicle_display",MODE_PRIVATE))
        lowSoc=if(fixturePrefix==null)LowSocStore(this)else LowSocStore(this,fixturePrefix+"-soc.db")
        attentionEvents=AttentionEventStore(this,fixturePrefix?.let{it+"-attention.db"}?:"caritda-attention.db");attentionEvents.recover()
        attentionObserver=PleosAttentionObserver(this,observations,{visible&&canEdit()&&screen!=Screen.WELCOME},{ref->
            runCatching{receiveAttention(ref)}.onFailure{notice="받은 차량 정보는 남아 있어요. 안내 이력을 다시 확인해 주세요."}
            if(screen in setOf(Screen.HOME,Screen.VEHICLE)){positions[screen]=scrollView?.scrollY?:0;draw()}
        },{notice=attentionObserver.message;if(visible)draw()})
        socObserver=PleosSocObserver(this,observations,{visible&&canEdit()&&lowSoc.policy().enabled},{
            runCatching{lowSoc.reconcile(observations)}.onFailure{notice="차량 정보는 저장됐지만 안내 갱신에 실패했어요. 자동 안내에서 다시 시도해 주세요."}
            runCatching{receiveAttention(it)}
            requestAnalytics()
        },{if(screen in setOf(Screen.HOME,Screen.VEHICLE,Screen.SOC_NOTICES)){positions[screen]=scrollView?.scrollY?:0;draw()}})
        entry=(lastNonConfigurationInstance as? RetainedSession)?.entry ?: EntryReadState()
        pleos = PleosReadClient(this, observations) {
            if(!pleos.busy){reconcileSnapshots();maybeSocWatch()}
            if(screen in setOf(Screen.HOME,Screen.VEHICLE,Screen.LOAD_DETAIL,Screen.SNAPSHOTS)){positions[screen]=scrollView?.scrollY?:0;draw()}
        }
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
            if(state!=EditingState.ALLOWED){stopAssistance();pleos.stop();stopSocWatch("안전 상태 변경으로 자동 확인을 멈췄어요.")} else {if(visible)homeVoice?.resume();consumeAiHome();maybeAutomaticRead();maybeSocWatch()}
        }
        service = CaritdaService(repository, { safety.state == EditingState.ALLOWED })
        service.restoreConfiguration((lastNonConfigurationInstance as? RetainedSession)?.driverId)
        homeVoice=com.nextdrive.pleos.voice.HomeVoiceRuntime(this,fixturePrefix,service,repository,observations,lowSoc,attentionEvents,
            {visible&&canWrite()&&screen==Screen.HOME&&!speechInput.busy&&!speechOutput.busy},{paintHomeVoice()})
        journal = runCatching { service.draft() }.getOrElse { notice="초안을 복원하지 못했습니다. 기존 초안은 보존됩니다.";null }
        lastDraft = journal
        pendingDriver = journal?.ownerId ?: repository.currentDriver()?.id
        savedInstanceState?.let {
            aiHomeRequested=it.getBoolean("aiHomeRequested")
            expandedSections.addAll(it.getStringArrayList("expandedSections").orEmpty())
            aiPreview=it.getString("aiPreview","");if(aiPreview.isNotBlank())aiConsent=AnalysisConsent(aiPreview)
            showHealth=it.getBoolean("showHealth");showUnavailable=it.getBoolean("showUnavailable");hiddenSnapshots=it.getBoolean("hiddenSnapshots")
            socTechnical=it.getBoolean("socTechnical");socHistoryLimit=it.getInt("socHistoryLimit",20)
            locationHistory=it.getBoolean("locationHistory");locationHistoryLimit=it.getInt("locationHistoryLimit",3)
            navigationDraft=it.getStringArrayList("navigationDraft")?.takeIf{list->list.size==3}?:navigationDraft
            val codes=it.getStringArrayList("navigationCodes").orEmpty();val times=it.getLongArray("navigationTimes")?:longArrayOf()
            navigationEvents=codes.mapIndexedNotNull{i,code->times.getOrNull(i)?.let{at->InspectionNavigation.Event(code,at)}}
            if(it.getBoolean("navigationWasBusy"))navigationEvents=navigationEvents+InspectionNavigation.Event("NAVI_MONITOR_STOPPED",System.currentTimeMillis())
            analysisKind=it.getString("analysisKind","EV_SOC");analysisDays=it.getInt("analysisDays",1);socHistory=it.getBoolean("socHistory");analysisDetail=it.getBoolean("analysisDetail");analysisPage=it.getInt("analysisPage")
            socThresholdDraft=it.getString("socThresholdDraft")
            pendingDriver=journal?.ownerId?:it.getString("pendingDriver",pendingDriver)
            editingDriver=it.getString("editingDriver")
            driverNameDraft=it.getString("driverNameDraft")
            notice=it.getString("notice",notice)
            guideReplay=it.getBoolean("guideReplay")
            helpTopic=it.getInt("helpTopic")
            pausedGuide=it.getString("pausedGuide")?.let{name->runCatching{Screen.valueOf(name)}.getOrNull()}
            menuReturn=it.getString("menuReturn")?.let{name->runCatching{Screen.valueOf(name)}.getOrNull()}
            if(lastNonConfigurationInstance!=null)it.getStringArrayList("history")?.forEach { name->runCatching{history.addLast(Screen.valueOf(name))} }
            Screen.entries.forEach{target->positions[target]=it.getInt("position_${target.name}")}
            screen = runCatching { Screen.valueOf(it.getString("screen") ?: "HOME") }.getOrDefault(Screen.HOME)
            selectedIssue = it.getString("issue")
            selectedSnapshot=it.getString("snapshot");selectedCard=it.getString("card");historyPage=it.getInt("historyPage");technicalExpanded=it.getBoolean("technicalExpanded");recordExtras=it.getBoolean("recordExtras")
            recordFilter = runCatching{RecordFilter.valueOf(it.getString("recordFilter")?:if(it.getBoolean("includeResolved"))"COMPLETED" else "ALL")}.getOrDefault(RecordFilter.ALL)
            recordQuery=it.getString("recordQuery","");searchText=it.getString("searchText",recordQuery)
            previousRecordBrowse=it.getString("previousRecordFilter")?.let{name->runCatching{
                RecordBrowse(RecordFilter.valueOf(name),it.getString("previousRecordQuery", ""),it.getString("previousRecordText", ""),it.getInt("previousRecordPosition"))
            }.getOrNull()}
            pendingRecordFilter=it.getString("pendingRecordFilter")?.let{name->runCatching{RecordFilter.valueOf(name)}.getOrNull()}
            draft = Draft(runCatching { SymptomType.valueOf(it.getString("type") ?: "NOISE") }.getOrDefault(SymptomType.NOISE),
                it.getString("situation") ?: "", it.getLong("foundAt", System.currentTimeMillis()), it.getString("memo") ?: "")
        }
        // Restored detail/input screens wait for a new platform restriction response.
        if (!service.confirmed()) screen = Screen.DRIVERS
        guide=GuideState(getSharedPreferences(fixturePrefix?.let{it+"-help"}?:"caritda_help",MODE_PRIVATE))
        @Suppress("DEPRECATION")
        val info=packageManager.getPackageInfo(packageName,0)
        val existing=info.firstInstallTime<info.lastUpdateTime || repository.drivers(true).isNotEmpty() || repository.issues().isNotEmpty() || journal!=null || observations.latest(1).isNotEmpty()
        if(guide.prepare(existing) && !guideReplay)screen=Screen.WELCOME
        else if(savedInstanceState!=null && lastNonConfigurationInstance!=null && savedInstanceState.getString("screen") in listOf("HELP","HELP_TOPIC","WELCOME","MENU","VEHICLE_SETTINGS"))screen=Screen.valueOf(savedInstanceState.getString("screen")!!)
        draw()
    }
    override fun onStart() {
        super.onStart()
        visible=true
        removeVoiceAuth=com.nextdrive.pleos.ai.auth.FirebaseSessionProvider.observe{
            if(visible&&com.nextdrive.pleos.ai.auth.FirebaseSessionProvider.initialized){val deferred=deferredAttention;deferredAttention=null;if(deferred!=null)runCatching{receiveAttention(deferred)}}
            homeVoice?.evaluate()
        }
        captureDraft()
        displayedSafety = EditingState.UNKNOWN
        draw()
        safety.start()
        reconcileSnapshots()
        freshnessHandler.removeCallbacks(freshnessTick);freshnessHandler.post(freshnessTick)
        homeVoice?.resume()
    }
    override fun onStop() { visible=false;removeVoiceAuth?.invoke();removeVoiceAuth=null;deferredAttention=null;stopAssistance();if(!isChangingConfigurations)entry=EntryReadState();captureDraft(); activeDialog?.dismiss(); freshnessHandler.removeCallbacks(freshnessTick);pleos.stop();stopSocWatch("앱이 보이지 않아 자동 확인을 멈췄어요. 관측 공백이 있어요."); safety.stop(); displayedSafety = EditingState.UNKNOWN; super.onStop() }
    override fun onDestroy() { homeVoice?.close();aiConsent?.revoke();analysisWorker.shutdown();repository.close(); observations.close();snapshots.close();lowSoc.close();attentionEvents.close();super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft()
        outState.putStringArrayList("expandedSections",ArrayList(expandedSections));outState.putBoolean("showHealth",showHealth);outState.putBoolean("showUnavailable",showUnavailable);outState.putBoolean("hiddenSnapshots",hiddenSnapshots);outState.putBoolean("socTechnical",socTechnical);outState.putInt("socHistoryLimit",socHistoryLimit)
        outState.putString("aiPreview",aiPreview)
        outState.putBoolean("aiHomeRequested",aiHomeRequested)
        navigationFields?.let{navigationDraft=it.map{field->field.text.toString()}}
        outState.putStringArrayList("navigationDraft",ArrayList(navigationDraft));outState.putStringArrayList("navigationCodes",ArrayList(navigationEvents.map{it.code}));outState.putLongArray("navigationTimes",navigationEvents.map{it.at}.toLongArray());outState.putBoolean("navigationWasBusy",navigation.busy)
        outState.putBoolean("locationHistory",locationHistory);outState.putInt("locationHistoryLimit",locationHistoryLimit)
        outState.putString("analysisKind",analysisKind);outState.putInt("analysisDays",analysisDays);outState.putBoolean("socHistory",socHistory);outState.putBoolean("analysisDetail",analysisDetail);outState.putInt("analysisPage",analysisPage)
        outState.putString("snapshot",selectedSnapshot);outState.putString("card",selectedCard)
        outState.putInt("historyPage",historyPage);outState.putBoolean("technicalExpanded",technicalExpanded);outState.putBoolean("recordExtras",recordExtras)
        outState.putString("screen", screen.name); outState.putString("issue", selectedIssue)
        outState.putString("menuReturn",menuReturn?.name)
        outState.putString("socThresholdDraft",socThresholdInput?.text?.toString()?:socThresholdDraft)
        outState.putString("recordFilter", recordFilter.name); outState.putString("type", draft.type.name)
        searchText=searchInput?.text?.toString()?:searchText
        outState.putString("recordQuery",recordQuery);outState.putString("searchText",searchText);outState.putString("pendingRecordFilter",pendingRecordFilter?.name)
        previousRecordBrowse?.let{outState.putString("previousRecordFilter",it.filter.name);outState.putString("previousRecordQuery",it.query);outState.putString("previousRecordText",it.text);outState.putInt("previousRecordPosition",it.position)}
        outState.putString("situation", draft.situation); outState.putLong("foundAt", draft.foundAt); outState.putString("memo", draft.memo)
        outState.putString("pendingDriver",pendingDriver);outState.putString("editingDriver",editingDriver)
        outState.putBoolean("guideReplay",guideReplay);outState.putInt("helpTopic",helpTopic);outState.putString("pausedGuide",pausedGuide?.name)
        outState.putString("driverNameDraft",driverNameInput?.text?.toString()?:driverNameDraft);outState.putString("notice",notice)
        outState.putStringArrayList("history",ArrayList(history.map{it.name}))
        positions[screen]=scrollView?.scrollY?:0;positions.forEach{(target,position)->outState.putInt("position_${target.name}",position)}
        super.onSaveInstanceState(outState)
    }
    override fun onRetainNonConfigurationInstance(): Any = RetainedSession(if(service.confirmed())service.driver().id else null,entry)

    private fun reconcileSnapshots(){
        runCatching{snapshots.reconcile(observations)}.onSuccess{snapshotError=""}.onFailure{snapshotError="차량 원본은 저장됐지만 자동 기록을 정리하지 못했어요. 다시 열면 재시도해요."}
        runCatching{lowSoc.reconcile(observations)}.onFailure{snapshotError="차량 원본은 남아 있지만 자동 안내 갱신을 다시 시도해야 해요."}
        requestAnalytics()
    }
    /** One coalesced worker for bounded SQL/analysis. UI redraw never reads the full observation DB. */
    private fun requestAnalytics(){
        if(isDestroyed||analysisWorker.isShutdown)return
        if(analysisBusy){analysisDirty=true;return}
        analysisBusy=true;analysisDirty=false
        val days=analysisDays
        analysisWorker.execute{
            val result=runCatching{
                val now=System.currentTimeMillis();val since=if(days==0)0L else now-days*86400000L
                ObservationStore(applicationContext,fixturePrefix?.let{it+"-observations.db"}?:"caritda-observations.db").use{raw->LowSocStore(applicationContext,fixturePrefix?.let{it+"-soc.db"}?:"caritda-soc-notices.db").use{soc->AnalysisStore(applicationContext,fixturePrefix?.let{it+"-analytics.db"}?:"caritda-analytics.db").use{derived->
                    val changes=soc.changes();val gaps=soc.gaps()
                    listOf("EV_SOC","ODOMETER").associateWith{kind->
                        val refs=raw.analysisRefs(kind,since);VehicleAnalytics.analyze(kind,refs.takeLast(2000),since,now,changes,gaps,refs.size>2000).also{derived.remember(it)}
                    }
                }}}
            }
            runOnUiThread{
                if(!isDestroyed){analysisBusy=false
                    if(days==analysisDays)result.onSuccess{analysisResults=it;analysisError=""}.onFailure{analysisError="분석을 갱신하지 못했어요. 원본은 보존돼요. 다시 열어 주세요."}
                    if(visible&&screen==Screen.ANALYTICS){positions[screen]=scrollView?.scrollY?:0;draw()}
                    if(analysisDirty||days!=analysisDays)requestAnalytics()
                }
            }
        }
    }
    private fun maybeSocWatch(){if(::socObserver.isInitialized&&visible&&canEdit()&&screen!=Screen.WELCOME&&!pleos.busy){if(lowSoc.policy().enabled)socObserver.start();attentionObserver.start()}}
    private fun stopSocWatch(reason:String){if(::attentionObserver.isInitialized)attentionObserver.stop();if(::socObserver.isInitialized){if(socObserver.running)runCatching{lowSoc.gap(reason)};socObserver.stop(reason)}}
    private fun maybeAutomaticRead(){
        if(fixturePrefix!=null)return // synthetic UI fixture; actual SDK checks run separately
        if(entry.claim(visible,canEdit(),screen!=Screen.WELCOME,pleos.busy)){
            android.util.Log.i("Caritda","entry_read_once")
            pleos.readOnce()
        }
    }
    @Deprecated("Framework back navigation")
    override fun onBackPressed() {
        if(screen==Screen.AI_PREVIEW){aiConsent?.revoke();aiConsent=null;aiPreview=""}
        if(!canEdit()){toast("화면 입력이 가능한 뒤에 이동해 주세요.");return}
        if(!captureDraft())return
        rememberLocation()
        if(history.isNotEmpty())screen=history.removeLast() else if(screen!=Screen.HOME)screen=Screen.HOME else {super.onBackPressed();return}
        draw()
    }

    private fun rememberLocation(){
        positions[screen]=scrollView?.scrollY?:0
        searchText=searchInput?.text?.toString()?:searchText
    }
    private fun goHome(){
        if(!canEdit()){toast("화면 입력이 가능한 뒤에 이동해 주세요.");return}
        if(screen==Screen.HOME)return
        if(!captureDraft())return
        rememberLocation()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken,0)
        history.clear();menuReturn=null;guideReplay=false;pausedGuide=null;pendingRecordFilter=null;notice="";screen=Screen.HOME;draw()
    }
    private fun openRecords(filter:RecordFilter){
        if(filter==RecordFilter.MINE&&!service.confirmed()){
            openScreen(Screen.DRIVERS){pendingRecordFilter=filter;notice="내가 작성한 기록을 보려면 기록할 이름을 선택하고 운전 시작을 눌러 주세요."};return
        }
        openScreen(Screen.ISSUES){recordFilter=filter;recordQuery="";searchText="";positions[Screen.ISSUES]=0}
    }
    private fun openAllRecords(){
        val typed=searchInput?.text?.toString()?:searchText
        if(recordFilter!=RecordFilter.ALL||recordQuery.isNotBlank()||typed.isNotBlank()){
            previousRecordBrowse=RecordBrowse(recordFilter,recordQuery,typed,positions[Screen.ISSUES]?:0)
        }
        openRecords(RecordFilter.ALL)
    }
    private fun navigationBar(available:Float):LinearLayout {
        val actions=mutableListOf<Button>()
        fun add(title:String,key:String,icon:HmiIcon,action:()->Unit){
            actions.add(ui.action(title,key,ActionStyle.QUIET,canEdit(),if(available<600||resources.configuration.fontScale>=1.5f)null else icon){edit{action()}})
        }
        if(screen==Screen.HOME){
            add("설정·도움말","settings_help",HmiIcon.MENU){openScreen(Screen.MENU)}
        }else{
            add("이전","back",HmiIcon.BACK){onBackPressed()}
            add("홈","nav_HOME",HmiIcon.HOME){goHome()}
        }
        return ui.screenHeader(pageTitle(),screen==Screen.HOME,actions,available)
    }
    private fun pageTitle()=when(screen){
        Screen.HOME->"CARITDA";Screen.ISSUES->"기록·인계";Screen.DRIVERS->"기록 운전자 선택"
        Screen.DRIVER_MANAGE->"이름 관리";Screen.DRIVER_EDIT->if(editingDriver==null)"이름 등록"else"이름 수정"
        Screen.RECORD->if(journal?.kind=="EDIT")"남긴 내용 수정"else"문제 남기기"
        Screen.DETAIL->"기록 상세";Screen.SUMMARY->"정비 상담용 기록";Screen.VEHICLE->"내 차 상태"
        Screen.LOAD_DETAIL->"앱 정보";Screen.SNAPSHOTS->"차량 조회 기록";Screen.SNAPSHOT_DETAIL->"조회한 차량 정보"
        Screen.HISTORY->"전체 조회 이력";Screen.HELP->"사용법";Screen.HELP_TOPIC->helpTitles[helpTopic]
        Screen.ACTION->if(journal?.kind=="NOTE")"점검·조치 메모"else"다시 발생한 증상";Screen.WELCOME->""
        Screen.MENU->"설정·도움말";Screen.VEHICLE_SETTINGS->"차량 표시·시각"
        Screen.SOC_NOTICES->"배터리 알림"
        Screen.ANALYTICS->"배터리·주행거리 그래프"
        Screen.AI_PREVIEW->"AI 전송 내용 확인"
        Screen.HANDOVER->"다음 운전자에게 넘기기"
    }
    private fun chooseDriver(){
        if(service.draft()!=null&&service.confirmed()){
            if(screen==Screen.MENU){screen=menuReturn?:Screen.HOME;menuReturn=null}
            notice="작성 중인 내용을 먼저 저장하거나 취소해 주세요.";draw()
        }
        else{pendingDriver=service.draft()?.ownerId?:repository.currentDriver()?.id;openScreen(Screen.DRIVERS)}
    }
    private fun moreMenu(body:LinearLayout){
        // One list of whole-row buttons (same tags and actions as before). Long notes moved to the bottom, folded.
        body.addView(themeChoice())
        val list=column()
        list.addView(column().apply{tag="driver_settings";addView(ui.listRow("운전자","등록하고 이름 바꾸기","manage_drivers",canEdit(),HmiIcon.PERSON){edit{openScreen(Screen.DRIVER_MANAGE)}})})
        list.addView(ui.listRow("화면 보기","차량 표시·시각 설정","vehicle_settings",canEdit(),HmiIcon.VEHICLE){edit{openScreen(Screen.VEHICLE_SETTINGS)}})
        list.addView(ui.listRow("음성 대화","설정과 지난 대화","voice_settings_history",canWrite(),HmiIcon.MIC){
            if(canWrite()&&captureDraft()){stopAssistance();startActivity(android.content.Intent(this,VoiceConversationActivity::class.java).putExtra("driver_id",service.driver().id).putExtra("validation_fixture",fixturePrefix))}
        })
        list.addView(ui.listRow("사용법","사용 방법 보기","help",canEdit(),HmiIcon.HELP){edit{openScreen(Screen.HELP)}})
        list.addView(ui.listRow("처음 안내","처음 안내 다시 보기","replay_guide",canEdit(),HmiIcon.HELP){edit{guideReplay=true;openScreen(Screen.WELCOME)}})
        list.addView(ui.listRow("AI와 개인정보","AI 계정 확인","ai_account_check",canEdit(),HmiIcon.SHIELD){edit{
            stopAssistance();startActivity(android.content.Intent(this,PortableAiActivity::class.java).putExtra("account_reauth",true).putExtra("validation_fixture",fixturePrefix))
        }})
        list.addView(ui.listRow("앱 정보","앱 이름과 개발자용 정보","app_technical",canEdit(),HmiIcon.INFO){edit{openScreen(Screen.LOAD_DETAIL)}})
        body.addView(list)
        fold(body,"개인정보와 앱 설명 자세히","settings_info"){details->
            details.addView(label("AI는 저장한 문제에서 선택해요. 전송 전에 내용과 동의를 확인하며, 동의 철회도 같은 화면에서 할 수 있어요.",22))
            details.addView(label("기록은 이 기기의 운전자끼리 확인해요. 휴대폰이나 다른 차량으로 자동 전송하지 않아요.",20))
            details.addView(label("CARITDA · 카잇다\n차량 기록을 다음 운전자에게 이어요.\n상표 검증 전 잠정 명칭이에요.",20))
        }
    }
    /** Screen colour as three big icon tiles (sun / moon / auto). Same preference key and tags as the old text buttons. */
    private fun themeChoice():LinearLayout{
        val preference=CaritdaTheme.preferences(this,fixturePrefix).getString("theme","light")
        val box=column()
        box.addView(label("화면 색",24,true))
        val choices=listOf(Triple("light","밝게",HmiIcon.SUN),Triple("dark","어둡게",HmiIcon.MOON),Triple("system","자동",HmiIcon.AUTO))
        box.addView(ui.grid(choices.map{(value,title,icon)->ui.choiceTile(title,"theme_$value",value==preference,canEdit(),icon){
            edit{check(CaritdaTheme.preferences(this,fixturePrefix).edit().putString("theme",value).commit());notice="화면 색을 저장했어요.";draw()}
        }},if(columns(600)==2)3 else 1))
        if(preference=="system")box.addView(label("이 기기의 화면 설정에 맞춰요.",20))
        return box
    }
    private fun draw() {
        if(screen!=Screen.SUMMARY||repairSearchScope.isNotBlank()&&repairSearchScope!=repairSearchContext()){
            repairSearch.stop();repairSearchScope="";repairSearchStatus=""
        }
        if(::service.isInitialized)ActionDriverSession.id=if(service.confirmed())service.driver().id else null
        if(screen!=Screen.RECORD&&speechFormVisible){speechInput.stop();speechFormVisible=false}
        if (!::service.isInitialized || isDestroyed || isFinishing) return
        socThresholdDraft=socThresholdInput?.text?.toString()?:socThresholdDraft;socThresholdInput=null
        captureDraft()
        searchText=searchInput?.text?.toString()?:searchText;searchInput=null
        driverNameDraft=driverNameInput?.text?.toString()?:driverNameDraft;driverNameInput=null
        situationInput=null;memoInput=null;actionField=null
        dark=CaritdaTheme.dark(this,fixturePrefix)
        palette=CaritdaPalette.of(dark)
        ink=palette.text;muted=palette.secondary;accent=palette.accent;backdrop=palette.background;cardColor=palette.surface;actionColor=palette.onAccent
        val width=if(root.width>0)root.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat()
        val pageInset=when{width<600->18;width<840->24;else->32}
        root.setBackgroundColor(backdrop);page.setBackgroundColor(backdrop);root.setPadding(dp(pageInset),dp(8),dp(pageInset),dp(8));root.removeAllViews()
        if(packageName.endsWith(".validation"))root.addView(label("검증 전용 · TEST",20,true).apply{tag="test_banner";contentDescription="검증 전용 TEST 앱"})
        val scroll=ScrollView(this).apply{isFillViewport=true;clipToPadding=false;tag="screen_scroll"};scrollView=scroll
        val body=column();scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        try {
            val available=if(root.width>0)root.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat()
            val helpScreen=screen in setOf(Screen.HELP,Screen.HELP_TOPIC)
            val header=navigationBar(available)
            val actualHeight=if(root.height>0)root.height/resources.displayMetrics.density else resources.configuration.screenHeightDp.toFloat()
            val shortWindow=actualHeight<480||resources.configuration.fontScale>=1.5f||screen in setOf(Screen.RECORD,Screen.ACTION,Screen.DRIVER_EDIT,Screen.ISSUES,Screen.SOC_NOTICES)
            if(shortWindow)body.addView(header)else root.addView(header,0)
            if(displayedSafety!=EditingState.ALLOWED)body.addView(label(if(displayedSafety==EditingState.UNKNOWN)"화면 입력이 가능한지 확인 중이에요." else "지금은 조작할 수 없어요. 안전하게 주차한 뒤 이용해 주세요.",20).apply{tag="safety_status"})
            feedback=label(notice,22,true).apply{
                tag="feedback";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
                visibility=if(notice.isBlank()||helpScreen||screen==Screen.WELCOME)View.GONE else View.VISIBLE
            }
            val height=if(root.height>0)root.height/resources.displayMetrics.density else resources.configuration.screenHeightDp.toFloat()
            if(height>=600 && resources.configuration.fontScale<1.5f)root.addView(feedback,1)else body.addView(feedback,0)
            when(screen){
                Screen.MENU->moreMenu(body)
                Screen.VEHICLE_SETTINGS->vehicleSettings(body)
                Screen.SOC_NOTICES->socNotices(body)
                Screen.ANALYTICS->analyticsScreen(body)
                Screen.AI_PREVIEW->aiPreviewScreen(body)
                Screen.HOME->home(body)
                Screen.HANDOVER->handover(body)
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
                Screen.SNAPSHOTS->snapshotScreen(body)
                Screen.SNAPSHOT_DETAIL->snapshotDetail(body)
                Screen.HISTORY->historyScreen(body)
            }
            scroll.post{if(screen!=Screen.RECORD && screen!=Screen.ACTION)scroll.scrollTo(0,positions[screen]?:0)}
        } catch(e:Exception) {
            android.util.Log.e("Caritda","Read failed",e)
            body.addView(label("기록을 불러오지 못했습니다. 기존 데이터는 보존됩니다.",24,true))
            body.addView(button("다시 확인하기","retry"){draw()})
        }
        homeVoice?.evaluate();paintHomeVoice()
    }
    private fun themeSetting(body:LinearLayout){
        val preference=CaritdaTheme.preferences(this,fixturePrefix).getString("theme","light")
        body.addView(label("화면 테마",22,true))
        val choices=listOf("light" to "밝게","dark" to "어둡게","system" to "자동")
        body.addView(ui.grid(choices.map{(value,title)->ui.action((if(value==preference)"✓ "else"")+title,"theme_$value",ActionStyle.SECONDARY,canEdit()){
            edit{check(CaritdaTheme.preferences(this,fixturePrefix).edit().putString("theme",value).commit());notice="화면 테마를 저장했어요.";draw()}
        }.apply{isSelected=value==preference}},if(columns()==2)3 else 1))
        if(preference=="system")body.addView(label("이 기기의 화면 설정에 맞춰요.",20))
    }
    private fun vehicleSettings(body:LinearLayout){
        body.addView(label("내 차 종류",26,true))
        body.addView(label("어떤 정보를 먼저 볼지 정해요. 차가 알려 준 종류는 아니에요.",22))
        body.addView(label("현재: ${vehicleDisplay.layoutDescription}",22).apply{tag="layout_current"})
        PowertrainLayout.entries.forEach{layout->
            body.addView(ui.action((if(vehicleDisplay.layout==layout)"✓ "else"")+layout.label,"layout_${layout.name}",ActionStyle.SECONDARY,canEdit()){
                edit{vehicleDisplay.setLayout(layout);notice="화면 배치를 저장했어요. 차량 정보와 이전 기록은 바꾸지 않아요.";draw()}
            }.apply{isSelected=vehicleDisplay.layout==layout})
        }
        body.addView(label("차 종류를 몰라도 쓸 수 있어요. 받지 못한 값은 지어내지 않아요. 이전에 받은 기록은 그대로 남아요.",20))
        body.addView(label("시간 보는 방법",26,true))
        body.addView(label("${vehicleDisplay.timeDescription}\n기기 시간대: ${ZoneId.systemDefault().id}\n화면에 보이는 시각만 바뀌고 원래 기록은 그대로예요.",22).apply{tag="time_zone_setting"})
        for(device in listOf(false,true))body.addView(ui.action((if(vehicleDisplay.deviceTime==device)"✓ "else"")+(if(device)"기기 시간 사용"else"한국 시간 사용"),if(device)"time_device"else"time_seoul",ActionStyle.SECONDARY,canEdit()){
            edit{vehicleDisplay.setDeviceTime(device);notice="시각 표시 설정을 저장했어요.";draw()}
        }.apply{isSelected=vehicleDisplay.deviceTime==device})
    }
    private fun statusText(status:IssueStatus)=status.label
    private fun sectionName()=when(screen){Screen.HOME->"홈";Screen.ISSUES->"문제";Screen.VEHICLE->"차량";Screen.DRIVERS,Screen.DRIVER_EDIT->"운전자";else->"기록"}
    private fun columns(minWidth:Int=600)=if((if(root.width>0)root.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat())>=minWidth && resources.configuration.fontScale<1.5f)2 else 1
    private fun home(body:LinearLayout){
        val all=service.issues();val unread=if(service.confirmed())all.filter{service.needsAcknowledgement(it)}else emptyList()
        val driver=column();driver.addView(label("지금 운전하는 사람",20))
        driver.addView(label(if(service.confirmed())service.driver().label else"기록할 사람을 확인해 주세요",26,true))
        val change=ui.action(if(service.confirmed())"운전자 변경"else"운전하는 사람 고르기","change_driver",ActionStyle.QUIET,canEdit(),HmiIcon.PERSON){edit{chooseDriver()}}
        body.addView(ui.grid(listOf(driver,change),columns()))
        val primary=column();val secondary=column()
        if(unread.isNotEmpty()){
            val card=ui.attention(AttentionContent("넘겨받은 기록이 있어요",unread.first().situation.take(100),"미확인 ${unread.size}건 · 읽음과 점검은 별개예요."))
            card.addView(ui.action("넘겨받은 기록 보기 ·${unread.size}건","handover",ActionStyle.PRIMARY,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.HANDOVER)}});primary.addView(card)
        }
        val checks=vehicleChecks();healthFreshness=VehicleHealth.labels.keys.mapNotNull{observations.mostRecent(it,true)?.receivedAt}
        if(checks.isNotEmpty()){
            val first=checks.first();val card=ui.checkCard(first.title,first.reason,first.origin).apply{tag="home_vehicle_attention"}
            card.addView(label(vehicleDisplay.time(first.reading.receivedAt!!)+(if(checks.size>1)" · 그 밖에 ${checks.size-1}개 항목"else""),20))
            card.addView(ui.action("차 안내 보기","open_vehicle_check",ActionStyle.SECONDARY,canEdit(),HmiIcon.VEHICLE){edit{openScreen(Screen.VEHICLE){positions[Screen.VEHICLE]=0}}});primary.addView(card)
        }
        if(service.confirmed()){
            val voice=homeVoice
            val voiceCard=panel();voiceCard.addView(label(voice?.title?:"음성 대기 꺼짐",24,true).apply{tag="home_voice_status";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE})
            voiceCard.addView(label(voice?.detail?:"음성 설정에서 홈 자동 대기를 선택할 수 있어요.",20).apply{tag="home_voice_detail"})
            if(voice?.autoEnabled==true)voiceCard.addView(ui.action(if(voice.active)"음성 대기 끄기"else"다시 대기","home_voice_toggle",ActionStyle.QUIET,canEdit()){
                if(voice.active)voice.disable()else voice.retry();draw()
            })
            primary.addView(voiceCard)
        }
        if(service.draft()!=null)draftBanner(primary)else{
            val create=panel();create.addView(ui.sectionTitle("어떤 문제가 있었나요?",HmiIcon.PLUS))
            create.addView(label("말하거나 적어서 남겨 주세요.",22))
            create.addView(ui.action("문제 남기기","new_issue",if(unread.isEmpty()&&checks.isEmpty())ActionStyle.PRIMARY else ActionStyle.SECONDARY,canEdit(),HmiIcon.PLUS){edit{beginRecord()}})
            create.addView(ui.action("말로 물어보기 · 설정","voice_conversation",ActionStyle.SECONDARY,canWrite(),HmiIcon.RECORD){
                if(canWrite()&&service.draft()==null){stopAssistance();startActivity(android.content.Intent(this,VoiceConversationActivity::class.java).putExtra("driver_id",service.driver().id).putExtra("validation_fixture",fixturePrefix))}
            });primary.addView(create)
        }
        val health=panel();health.addView(ui.sectionTitle("내 차 상태",HmiIcon.VEHICLE))
        health.addView(label(if(pleos.busy)"차량 정보를 확인하고 있어요."else"차가 알려 준 정보를 보여 드려요.",22).apply{tag="auto_read_status"})
        health.addView(ui.action("내 차 상태 보기","vehicle_health",ActionStyle.SECONDARY,canEdit(),HmiIcon.VEHICLE){edit{openScreen(Screen.VEHICLE)}});secondary.addView(health)
        val records=panel();records.addView(ui.sectionTitle("기록 확인·넘기기",HmiIcon.RECORD));records.addView(label("저장한 기록 ${all.size}건",22).apply{tag="home_counts"})
        records.addView(ui.action("기록 모두 보기","all_issues",ActionStyle.SECONDARY,canEdit(),HmiIcon.RECORD){edit{openAllRecords()}});secondary.addView(records)
        body.addView(ui.twoPane(primary,secondary,columns(840)==2))
        pausedGuide?.let{paused->body.addView(ui.action("보던 안내로 돌아가기","resume_help",ActionStyle.QUIET,canEdit()){pausedGuide=null;screen=paused;draw()})}
    }
    private fun vehicleChecks()=VehicleAttention.list({observations.mostRecent(it)},{observations.mostRecent(it,true)},lowSoc.policy())
    private fun handover(body:LinearLayout){
        if(!service.confirmed()){
            body.addView(ui.state("누가 운전하세요?","고른 사람에게 넘어온 기록을 보여 드려요."))
            body.addView(ui.action("운전하는 사람 고르기","change_driver",ActionStyle.PRIMARY,canEdit(),HmiIcon.PERSON){edit{chooseDriver()}});return
        }
        val pending=service.issues().filter{service.needsAcknowledgement(it)}
        body.addView(label("확인하지 않은 기록 ${pending.size}건",24,true).apply{tag="handover_count"})
        if(pending.isEmpty())body.addView(ui.state("확인하지 않은 기록이 없어요.","읽음은 차가 괜찮다는 뜻이 아니에요."))
        pending.forEach{issueCard(body,it)}
        fold(body,"이 화면이 뭔가요? 자세히","handover_info"){d->d.addView(label("${service.driver().label}님이 아직 확인하지 않은, 다른 운전자가 남긴 기록이에요. 읽음 표시는 차가 괜찮다거나 점검을 마쳤다는 뜻이 아니에요.",22))}
    }

    private fun issues(body:LinearLayout){
        val actions=listOf(ui.action("문제 남기기","new_issue",ActionStyle.PRIMARY,canWrite(),HmiIcon.PLUS){edit{beginRecord()}},
            ui.action("다음 운전자에게 넘기기","handover",ActionStyle.SECONDARY,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.HANDOVER)}})
        body.addView(ui.grid(actions,columns()))
        draftBanner(body)
        val all=service.issues()
        if(recordFilter==RecordFilter.MINE&&!service.confirmed())recordFilter=RecordFilter.ALL
        val chosen=service.issues(recordFilter,recordQuery)
        searchInput=input("단어나 상황으로 찾기",100).apply{tag="record_search";minLines=1;maxLines=2;setText(searchText)}
        fieldLabel(body,"기록 찾기",searchInput!!);body.addView(searchInput)
        body.addView(ui.action("검색","apply_search",ActionStyle.QUIET,canEdit()){
            edit{recordQuery=searchInput?.text?.toString()?.trim().orEmpty();searchText=recordQuery
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken,0);positions[Screen.ISSUES]=0;draw()}
        })
        val filters=RecordFilter.entries.map{filter->ui.action((if(recordFilter==filter)"✓ "else"")+filter.label,"filter_${filter.name}",ActionStyle.SECONDARY,canEdit()&&(filter!=RecordFilter.MINE||service.confirmed())){
            edit{recordFilter=filter;positions[Screen.ISSUES]=0;draw()}
        }.apply{isSelected=recordFilter==filter}}
        body.addView(ui.grid(filters,if(columns()==2)4 else if(resources.configuration.fontScale<1.5f&&root.width/resources.displayMetrics.density>=480)2 else 1))
        body.addView(label("${chosen.size}건"+(if(recordQuery.isNotBlank())" · 검색: $recordQuery"else""),22).apply{tag="record_filter_status"})
        if(recordFilter==RecordFilter.ALL&&recordQuery.isBlank())previousRecordBrowse?.let{previous->
            body.addView(ui.action("이전 검색으로 돌아가기","restore_record_search",ActionStyle.QUIET,canEdit(),HmiIcon.BACK){edit{
                recordFilter=previous.filter;recordQuery=previous.query;searchText=previous.text
                searchInput?.setText(previous.text);positions[Screen.ISSUES]=previous.position;previousRecordBrowse=null;draw()
            }})
        }
        if(recordFilter!=RecordFilter.ALL||recordQuery.isNotBlank())body.addView(ui.action("검색·필터 초기화","reset_conditions",ActionStyle.QUIET,canEdit()){
            edit{recordFilter=RecordFilter.ALL;recordQuery="";searchText="";searchInput?.setText("");positions[Screen.ISSUES]=0;draw()}
        })
        if(chosen.isEmpty())body.addView(ui.state(if(all.isEmpty())"아직 남긴 기록이 없어요."else"조건에 맞는 기록이 없어요.",if(all.isEmpty())"기록이 없는 것이 차량 정상을 뜻하지는 않아요."else"검색이나 필터를 바꿔 주세요."))
        chosen.sortedBy{if(service.confirmed()&&service.needsAcknowledgement(it))0 else 1}.forEach{issueCard(body,it)}
        if(all.isNotEmpty())fold(body,"기록으로 상담 준비","records_summary"){details->details.addView(ui.action("전체 기록으로 정비 상담 준비","all_summary",ActionStyle.QUIET,canEdit(),HmiIcon.RECORD){edit{selectedIssue=null;openScreen(Screen.SUMMARY)}})}
    }

    private fun issueCard(body:LinearLayout,issue:VehicleIssue){
        body.addView(ui.issueRecord(issue.type.label,statusText(issue.status),issue.situation.take(100),
            "${ConsultationSummary.time(issue.discoveredAt)} · ${issue.reporter.label}",
            if(service.confirmed()&&service.needsAcknowledgement(issue))"아직 읽지 않은 전달 기록"else"직접 남긴 증상",
            "issue_${issue.id}",canEdit()){edit{selectedIssue=issue.id;openScreen(Screen.DETAIL)}})
    }
    private fun driverScreen(body:LinearLayout){
        body.addView(label("이 이름으로 기록을 남겨요. 차량 시동이나 계정은 바뀌지 않아요.",22))
        val drivers=service.drivers();val choices=column();val confirm=panel()
        drivers.forEach{driver->choices.addView(ui.action((if(pendingDriver==driver.id)"✓ "else"")+driver.label,"driver_${driver.id}",ActionStyle.SECONDARY,canEdit(),HmiIcon.PERSON){
            edit{rememberLocation();pendingDriver=driver.id;notice="";draw()}
        }.apply{isSelected=pendingDriver==driver.id})}
        if(drivers.isEmpty()){
            choices.addView(label("먼저 기록할 이름을 등록해 주세요.",24))
            choices.addView(ui.action("이름 등록","add_driver",ActionStyle.PRIMARY,canEdit(),HmiIcon.PLUS){edit{editingDriver=null;driverNameDraft=null;openScreen(Screen.DRIVER_EDIT)}})
        }
        val candidate=drivers.find{it.id==pendingDriver}
        confirm.addView(ui.sectionTitle(candidate?.label?:"이름을 골라 주세요.",HmiIcon.PERSON))
        confirm.addView(label("선택한 뒤 아래 버튼으로 확정해 주세요.",22))
        confirm.addView(ui.action("이 이름으로 기록 시작","start_driving",ActionStyle.PRIMARY,canEdit()&&candidate!=null,HmiIcon.CHECK){
            edit{service.selectDriver(candidate!!);notice="";history.clear();screen=Screen.HOME
                pendingRecordFilter?.let{recordFilter=it;recordQuery="";searchText="";screen=Screen.ISSUES};pendingRecordFilter=null;draw()}
        })
        body.addView(ui.twoPane(choices,confirm,columns(840)==2));draftBanner(body)
    }

    private fun driverManage(body:LinearLayout){
        body.addView(ui.action("운전자 추가","add_driver",ActionStyle.PRIMARY,canEdit(),HmiIcon.PLUS){edit{editingDriver=null;driverNameDraft=null;openScreen(Screen.DRIVER_EDIT)}})
        body.addView(label("이름 변경이나 보관으로 과거 작성자와 기록이 바뀌지 않아요.",22))
        val cards=service.drivers().map{driver->
            val card=panel();card.addView(ui.sectionTitle(driver.label,HmiIcon.PERSON))
            card.addView(ui.action("이름 수정","rename_${driver.id}",ActionStyle.SECONDARY,canEdit()){edit{editingDriver=driver.id;driverNameDraft=null;openScreen(Screen.DRIVER_EDIT)}})
            card.addView(ui.action("선택 목록에서 보관","archive_${driver.id}",ActionStyle.QUIET,canEdit()){
                confirm("운전자를 보관할까요?","과거 기록은 남고 운전자 선택 목록에서만 제외돼요."){
                    service.archiveDriver(driver.id);if(pendingDriver==driver.id)pendingDriver=null;notice="운전자를 보관했어요.";draw()
                }
            });card
        };body.addView(ui.grid(cards,columns(840)))
    }

    private fun driverForm(body:LinearLayout){
        val field=input("이름 또는 별명",12).apply{tag="driver_name";minLines=1;setText(driverNameDraft?:service.drivers().find{it.id==editingDriver}?.label.orEmpty())}
        driverNameInput=field;fieldLabel(body,"이름 또는 별명 · 1~12자",field);body.addView(field)
        body.addView(label("과거 기록의 작성 당시 이름은 유지해요.",20))
        body.addView(ui.action("이름 저장","save_driver",ActionStyle.PRIMARY,canEdit(),HmiIcon.CHECK){
            if(screen==Screen.DRIVER_EDIT)edit{
                if(editingDriver==null)pendingDriver=service.registerDriver(field.text.toString()).id else service.renameDriver(editingDriver!!,field.text.toString())
                driverNameInput=null;driverNameDraft=null;notice="이름을 저장했어요. 기록 운전자는 따로 확정해 주세요."
                if(history.peekLast()==Screen.DRIVER_MANAGE){history.removeLast();screen=Screen.DRIVER_MANAGE}else screen=Screen.DRIVERS
                draw()
            }
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
        notice="";recordExtras=false;draft=Draft(foundAt=journal!!.foundAt);openScreen(Screen.RECORD)
    }
    private fun resumeDraft(saved:JournalDraft){
        journal=saved;lastDraft=saved;selectedIssue=saved.target
        draft=Draft(saved.type,saved.body,saved.foundAt,saved.memo);recordExtras=saved.memo.isNotBlank()
        openScreen(if(saved.kind in setOf("RECORD","EDIT"))Screen.RECORD else Screen.ACTION)
    }
    private fun beginEdit(issue:VehicleIssue){
        if(!canWrite()||!service.canManage(issue)){toast("원래 작성자로 운전 시작한 뒤 수정해 주세요.");return}
        if(service.draft()!=null){notice="작성 중인 초안을 먼저 저장하거나 취소해 주세요.";draw();return}
        journal=JournalDraft(java.util.UUID.randomUUID().toString(),service.driver().id,service.driver().label,"EDIT",issue.id,issue.revision,issue.type,issue.situation,issue.memo,issue.discoveredAt)
        notice="";recordExtras=true;draft=Draft(issue.type,issue.situation,issue.discoveredAt,issue.memo);lastDraft=null
        openScreen(Screen.RECORD)
    }
    private fun actionForm(body:LinearLayout){
        val saved=journal?:return

        body.addView(label("작성자: ${saved.ownerName} · 사용자 직접 기록",22))
        actionField=input("직접 확인한 내용을 적어 주세요.",2000).apply{tag="action_note";setText(saved.body)};body.addView(actionField)
        actionField!!.addTextChangedListener(watcher{captureDraft()})
        body.addView(button("이 차량 앱에 저장","save_action",primary=true,enabled=canWrite()){
            if(!captureDraft())return@button
            edit{
                val current=journal!!;if(current.kind=="NOTE")service.addNote(current.target!!,current.body,current.id) else service.recur(current.target!!,current.body,current.id)
                journal=null;lastDraft=null;actionField=null;notice="기록을 저장했습니다.";screen=Screen.DETAIL;draw()
            }
        })
        cancelDraftButton(body)
    }
    private fun cancelDraftButton(body:LinearLayout){
        body.addView(button(if(journal?.kind=="EDIT")"수정 취소" else "작성 취소","discard_draft",enabled=canEdit()){
            val active=journal?:return@button
            confirm("초안 작성을 취소할까요?","이 작성 중인 초안만 제거합니다. 기존 문제와 이력은 유지됩니다."){
                service.discardDraft(active.id);journal=null;lastDraft=null;draft=Draft();history.clear()
                if(active.kind=="EDIT"){selectedIssue=active.target;screen=Screen.DETAIL;history.addLast(Screen.ISSUES);positions[Screen.DETAIL]=0}else screen=Screen.HOME
                notice="초안 작성을 취소했습니다.";draw()
            }
        })
    }
    private fun freshnessMarker():String {
        val times=mutableListOf<Long>()
        listOf("EV_SOC","FUEL_PERCENT","ODOMETER").forEach{kind->observations.mostRecent(kind,true)?.receivedAt?.let{times.add(it)}}
        val shown=mutableListOf<Snapshot>()
        selectedSnapshot?.let{snapshots.get(it)?.let(shown::add)}
        if(screen in setOf(Screen.DETAIL,Screen.SUMMARY))selectedIssue?.let{snapshots.linked(it)?.let(shown::add)}
        if(screen==Screen.SNAPSHOTS)snapshots.cards(hiddenSnapshots).forEach{shown.addAll(snapshots.timeline(it.cardId))}
        if(screen==Screen.HISTORY)selectedCard?.let{shown.addAll(snapshots.timeline(it,20,historyPage*20))}
        times.addAll(shown.flatMap{it.evidence}.mapNotNull{it.value.receivedAt})
        val now=System.currentTimeMillis()
        if(screen in setOf(Screen.HOME,Screen.VEHICLE))times.addAll(healthFreshness)
        return times.distinct().joinToString{"$it:${now-it>=VehiclePresentation.STALE_AFTER_MS}:${it>now}:${it>now+60_000}"}
    }
    private fun metricGroup(body:LinearLayout,snapshot:Snapshot?=null,fromSnapshot:Boolean=false){
        fun pair(kind:String)=if(fromSnapshot)VehicleUi.values(snapshot,kind)else observations.mostRecent(kind) to observations.mostRecent(kind,true)
        val layout=if(fromSnapshot)PowertrainLayout.UNKNOWN else vehicleDisplay.layout
        val kinds=VehicleInfoPolicy.kinds(layout,{pair(it).first},{pair(it).second})
        val cards=kinds.map{kind->
            val pair=pair(kind)
            val view=VehiclePresentation.card(kind,pair.first,pair.second,System.currentTimeMillis(),vehicleDisplay::time)
            val value=if(pair.second==null)when(pair.first?.status){ObservationStatus.UNSUPPORTED->"지원되지 않음";null->"확인 전";else->"확인할 수 없음"}else view.value
            ui.metric(VehicleInfoPolicy.name(kind,layout),value,view.time,view.attempt,kind,when(kind){"ODOMETER"->HmiIcon.DISTANCE;"FUEL_PERCENT"->HmiIcon.FUEL;else->HmiIcon.BATTERY})
        }
        body.addView(ui.grid(cards,columns()))
        val source=if(fromSnapshot)VehicleUi.source(snapshot)else{
            val item=observations.mostRecent("EV_SOC")?:observations.mostRecent("ODOMETER")
            when(item?.source){ObservationSource.PLEOS_EMULATOR_SDK->"에뮬레이터에서 받은 값이에요.";ObservationSource.PLEOS_VEHICLE_SDK->"차량 SDK에서 받은 값이에요.";else->"아직 차량 정보를 받지 못했어요."}
        }
        body.addView(label(source,20).apply{tag="vehicle_source"})
    }
    private fun healthSummary(body:LinearLayout){
        val readings=(listOf("EV_SOC","ODOMETER","FUEL_PERCENT")+VehicleHealth.labels.keys).map{kind->DriveCheck.assess(kind,observations.mostRecent(kind),observations.mostRecent(kind,true),lowSoc.policy())}
        healthFreshness=readings.mapNotNull{it.receivedAt}
        body.addView(ui.expandable("문·등화 등 차량 정보 자세히","health_expand",showHealth,canEdit()){edit{rememberLocation();showHealth=!showHealth;draw()}})
        if(!showHealth)return
        val groups=VehicleHealthPresentation.groups.map{(name,kinds)->
            val group=panel().apply{tag="health_group_${kinds.first()}"}
            val mine=readings.filter{it.kind in kinds}
            val openKey="health_open_${kinds.first()}"
            val isOpen=openKey in expandedSections
            // Tap the big heading to open its details right below it; opening one closes the others.
            group.addView(ui.accordion(name,VehicleHealthPresentation.summary(mine),"health_toggle_${kinds.first()}",isOpen,canEdit()){edit{
                rememberLocation();expandedSections.removeAll{it.startsWith("health_open_")};if(!isOpen)expandedSections.add(openKey);draw()
            }})
            if(isOpen){
                mine.forEach{reading->group.addView(label(VehicleHealthPresentation.compact(reading),22).apply{tag="drivecheck_item_${reading.kind}"})}
                if(name=="문")group.addView(label("문이 열렸는지는 차에 보이는 표시로 봐 주세요.",20))
                if(name=="타이어 안내 기준")group.addView(label("타이어 공기압 숫자는 알려 주지 않아요.",20))
            }
            group
        }
        body.addView(ui.grid(groups,columns(840)))
        fold(body,"출처·수신 상태 자세히","health_technical"){details->
            details.addView(label(vehicleDisplay.layoutDescription+"\n"+vehicleDisplay.timeDescription,20).apply{tag="powertrain_context"})
            readings.forEach{reading->details.addView(ui.state(reading.title,reading.text(vehicleDisplay::time)))}
            details.addView(ui.action("개발자용 원문 보기","load_details",ActionStyle.QUIET,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.LOAD_DETAIL)}})
            if(packageName=="com.nextdrive.pleos.validation"){
                details.addView(ui.action("TEST · Gemini 합성 시험","gemini_poc",ActionStyle.QUIET,canEdit()){edit{startActivity(android.content.Intent().setClassName(this,"com.nextdrive.pleos.GeminiPocActivity"))}})
                details.addView(ui.action("TEST · 로그인 호환성","firebase_auth_probe",ActionStyle.QUIET,canEdit()){edit{startActivity(android.content.Intent().setClassName(this,"com.nextdrive.pleos.AuthCompatibilityActivity"))}})
            }
        }
    }

    private fun beginFromCheck(kind:String){
        if(!canWrite()||!captureDraft())return
        val current=DriveCheck.assess(kind,observations.mostRecent(kind),observations.mostRecent(kind,true),lowSoc.policy())
        if(!current.attention){notice="새로 받은 차량 상태를 다시 확인해 주세요. 이전 안내로 새 문제를 확정하지 않아요.";draw();return}
        if(service.draft()!=null){notice="작성 중인 초안을 먼저 계속 작성하거나 취소해 주세요. 기존 내용을 덮어쓰지 않았어요.";draw();return}
        beginRecord();draft.type=DriveCheck.draftType(kind)
        situationInput?.setText(DriveCheck.draftText(kind));captureDraft()
        notice="차량 안내에서 시작한 사용자 초안이에요. 직접 확인한 증상으로 고쳐 주세요. 차량 수치·시각·위치는 본문이나 AI에 자동 추가하지 않아요.";draw()
    }
    private fun vehicleScreen(body:LinearLayout){
        val checks=vehicleChecks()
        if(checks.isNotEmpty()){
            body.addView(label("확인이 필요한 항목",24,true).apply{tag="drivecheck_attention"})
            checks.forEach{check->
                val reason=listOf(check.reading.value,check.reason).distinct().joinToString(" · ")
                val card=ui.checkCard(check.title,reason,check.origin)
                card.addView(label(vehicleDisplay.time(check.reading.receivedAt!!),20))
                card.addView(ui.action("직접 확인한 내용 남기기","drivecheck_draft_${check.kind}",ActionStyle.SECONDARY,canWrite(),HmiIcon.RECORD){beginFromCheck(check.kind)})
                attentionEvents.events().firstOrNull{it.kind==check.kind&&it.phase=="ACTIVE"}?.let{event->
                    if(service.confirmed()&&attentionEvents.acknowledged(event.id,service.driver().id))card.addView(label("안내를 확인하셨어요. 차량 상태는 계속 표시해요.",20))
                    else card.addView(ui.action("안내 확인했어요","attention_ack_${check.kind}",ActionStyle.QUIET,canWrite()){edit{
                        require(canWrite());attentionEvents.acknowledge(event.id,service.driver().id);draw()
                    }})
                }
                if(!canWrite())card.addView(label("기록 운전자를 확인한 뒤 남길 수 있어요.",20))
                body.addView(card)
            }
        }
        if(service.draft()!=null)draftBanner(body)
        val primary=column();val secondary=column()
        val metrics=panel();metrics.addView(ui.sectionTitle("차가 알려 준 정보",HmiIcon.VEHICLE))
        if(pleos.busy)metrics.addView(ui.loading("차량 정보를 확인하고 있어요.").apply{tag="load_status"})
        metricGroup(metrics)
        metrics.addView(ui.action("다시 불러오기","read_vehicle",ActionStyle.SECONDARY,canEdit()&&!pleos.busy,HmiIcon.VEHICLE){edit{entry.markRequested();pleos.readOnce()}})
        primary.addView(metrics)
        val summary=panel();summary.addView(ui.sectionTitle("알려 준 정보 요약"))
        summary.addView(label(if(checks.isEmpty())"최근에 받은 값으로는 알려 드릴 안내가 없어요."else"위 항목의 이유를 확인하고 직접 살펴봐 주세요.",24))
        summary.addView(label("차가 알려 준 것만 보여요. 차 전체가 괜찮다는 뜻은 아니에요.",20));primary.addView(summary)
        val soc=panel();soc.addView(ui.sectionTitle("배터리 알림",HmiIcon.BATTERY));soc.addView(label("내가 정한 % 아래로 내려가면 알려 드려요.",22))
        soc.addView(ui.action("기준 설정·안내 기록","soc_notices",ActionStyle.SECONDARY,canEdit(),HmiIcon.BATTERY){edit{openScreen(Screen.SOC_NOTICES)}});secondary.addView(soc)
        val analysis=panel();analysis.addView(ui.sectionTitle("배터리·주행거리 그래프",HmiIcon.DISTANCE));analysis.addView(label("이전에 받은 값과 비교해 보여 드려요.",22))
        analysis.addView(ui.action("그래프·분석 보기","vehicle_analytics",ActionStyle.SECONDARY,canEdit(),HmiIcon.DISTANCE){edit{requestAnalytics();openScreen(Screen.ANALYTICS)}});secondary.addView(analysis)
        body.addView(ui.twoPane(primary,secondary,columns(840)==2))
        healthSummary(body)
        val history=panel();history.addView(ui.sectionTitle("저장해 둔 차 정보",HmiIcon.CLOCK))
        history.addView(ui.action("언제 받은 정보인지 보기","auto_snapshots",ActionStyle.QUIET,canEdit(),HmiIcon.CLOCK){edit{openScreen(Screen.SNAPSHOTS)}});body.addView(history)
    }

    private fun openSnapshot(s:Snapshot){openScreen(Screen.SNAPSHOT_DETAIL){selectedSnapshot=s.id;technicalExpanded=false}}
    private fun socNotices(body:LinearLayout){
        val p=lowSoc.policy();val notices=lowSoc.notices();val latest=observations.mostRecent("EV_SOC")
        val current=panel().apply{tag="soc_current"}
        val view=VehiclePresentation.card("EV_SOC",latest,observations.mostRecent("EV_SOC",true),System.currentTimeMillis(),vehicleDisplay::time)
        current.addView(ui.sectionTitle("최근 충전량",HmiIcon.BATTERY));current.addView(label(view.value,40,true).apply{tag="soc_current_value"})
        current.addView(label(view.time,22));if(view.attempt.isNotBlank())current.addView(label(view.attempt,22));current.addView(label(view.source,20));body.addView(current)
        body.addView(label("${if(p.enabled)"안내 켜짐"else"안내 꺼짐"} · 사용자 기준 ${p.threshold}% 미만",24,true).apply{tag="soc_policy"})
        body.addView(ui.state("현재 안내",SocGuide.text(p,notices,latest)).apply{tag="soc_core"})
        body.addView(label(if(!p.enabled)"자동 안내를 꺼 두셨어요."else if(socObserver.receivedCount>0&&socObserver.running)"충전량 수신을 확인했어요."else"새 수신 상태를 확인해 주세요.",20).apply{tag="soc_watch_status"})
        if(DriveCheck.assess("EV_SOC",latest,observations.mostRecent("EV_SOC",true),p).attention)body.addView(ui.action("직접 확인한 내용 남기기","drivecheck_draft_EV_SOC",ActionStyle.SECONDARY,canWrite(),HmiIcon.RECORD){beginFromCheck("EV_SOC")})
        if(service.draft()!=null)draftBanner(body)
        val settings=panel();settings.addView(ui.sectionTitle("나의 충전량 기준"))
        socThresholdInput=input("1~95",2).apply{tag="soc_threshold";inputType=InputType.TYPE_CLASS_NUMBER;setText(socThresholdDraft?:p.threshold.toString());minLines=1}
        fieldLabel(settings,"이 충전량 미만이면 안내해요 · %",socThresholdInput!!);settings.addView(socThresholdInput)
        settings.addView(button(if(p.enabled)"기준 저장"else"이 기준으로 안내 켜기","enable_soc",primary=true,enabled=canEdit()){
            val threshold=socThresholdInput?.text?.toString()?.toIntOrNull()?:throw IllegalArgumentException("안내 기준을 숫자로 입력해 주세요.")
            lowSoc.configure(true,threshold,observations);socThresholdDraft=null;socThresholdInput=null;notice="기준을 저장했어요. 새로 받은 값부터 안내해요.";stopSocWatch("설정을 반영하고 있어요.");maybeSocWatch();draw()
        })
        if(p.enabled)settings.addView(ui.action("안내 끄기","disable_soc",ActionStyle.QUIET,canEdit()){edit{lowSoc.configure(false,p.threshold,observations);stopSocWatch("자동 안내를 껐어요. 이전 기록은 남아요.");draw()}})
        settings.addView(label("내가 정한 기준이에요. 설정 기준보다 2%p 높은 값으로 돌아오면 알림을 끝내요. 고장이나 수리가 끝났다는 뜻은 아니에요.",20));body.addView(settings)
        body.addView(ui.expandable("어떻게 알려 드리나요?","soc_evidence",socTechnical,canEdit()){edit{socTechnical=!socTechnical;draw()}})
        if(socTechnical){
            body.addView(label("앱이 보이고 입력이 허용될 때만 확인해요. 종료·안전 제한·연결 끊김에는 관측 공백이 있어요.\n"+socObserver.message,20))
            body.addView(label("기기 내 규칙 안내예요. 차량 수치를 증상 본문이나 AI 요청에 자동 넣지 않아요.",20).apply{tag="ai_connection_status"})
            body.addView(ui.action("저장된 안내 다시 정리","reconcile_soc",ActionStyle.QUIET,canEdit()){edit{lowSoc.reconcile(observations);notice="저장된 관측으로 안내를 다시 확인했어요.";draw()}})
        }
        body.addView(ui.expandable("이전 안내 기록 · ${notices.size}건","soc_history",socHistory,canEdit()){edit{socHistory=!socHistory;draw()}})
        if(socHistory){
            if(notices.isEmpty())body.addView(label("이전 안내 기록이 없어요.",22))
            notices.take(socHistoryLimit).forEach{n->
                val card=panel().apply{tag="soc_notice_${n.id}"};card.addView(ui.sectionTitle(if(n.phase=="LOW")"안내 유지 기록"else"기준 복귀 기록"))
                card.addView(label("당시 마지막 수신 ${n.value}${n.unit}\n${vehicleDisplay.time(n.last)}",22))
                lowSoc.changes().filter{it.episode==n.id}.forEach{e->card.addView(label("${if(e.kind=="USER_LOW_SOC")"안내 진입"else"기준 복귀"} · ${e.raw}% · 당시 기준 ${e.threshold}% 미만\n${vehicleDisplay.time(e.at)}",22))}
                card.addView(label("현재 충전량과 구분해서 확인해 주세요.",20))
                if(socTechnical)card.addView(label("규칙 ${SocRule.VERSION}:${n.version} · 원본 ${n.store}:${n.row}",20));body.addView(card)
            }
            if(notices.size>socHistoryLimit)body.addView(button("이전 안내 더 보기","soc_history_more",enabled=canEdit()){socHistoryLimit+=20;draw()})
        }
    }

    private fun analyticsScreen(body:LinearLayout){
        body.addView(label("새로 받은 값으로 그래프를 그려요. 이 화면을 열어도 차에 다시 묻지는 않아요.",20))
        body.addView(ui.grid(listOf("EV_SOC" to "충전량","ODOMETER" to "누적거리").map{(kind,name)->ui.action(name,"analysis_$kind",if(kind==analysisKind)ActionStyle.PRIMARY else ActionStyle.SECONDARY,canEdit(),if(kind=="EV_SOC")HmiIcon.BATTERY else HmiIcon.DISTANCE){edit{analysisKind=kind;analysisPage=0;draw()}}},columns()))
        body.addView(ui.grid(listOf(1 to "최근 하루",7 to "최근 일주일",0 to "최근 2000개까지").map{(days,name)->ui.action(name,"analysis_days_$days",if(days==analysisDays)ActionStyle.PRIMARY else ActionStyle.QUIET,canEdit(),HmiIcon.CLOCK){edit{analysisDays=days;analysisPage=0;analysisResults=emptyMap();requestAnalytics();draw()}}},columns()))
        if(analysisBusy)body.addView(ui.loading("저장된 수신값을 분석하고 있어요."))
        if(analysisError.isNotBlank())body.addView(ui.state("분석을 갱신하지 못했어요.",analysisError,true))
        val a=analysisResults[analysisKind]?:run{body.addView(ui.state("분석을 준비하고 있어요.","수신 기록이 없으면 그래프를 만들지 않아요."));return}
        body.addView(label("보는 기간 · ${if(a.since==0L)"저장된 최근 2000개까지"else vehicleDisplay.time(a.since)}\n${vehicleDisplay.time(a.until)}까지",20).apply{tag="analysis_period"})
        if(a.points.isEmpty()){body.addView(ui.state("쓸 수 있는 값이 아직 없어요.","못 받은 값을 0으로 보여 드리지 않아요. 차 정보에서 연결 상태를 확인해 주세요."))}else{
            val latest=a.latest!!
            if(a.lastAttempt?.value?.let{!SnapshotStore.accepted(it)}==true)body.addView(ui.state("최근에 받지 못했어요.","아래 값은 예전에 받은 값이에요. 지금 상태가 괜찮다는 뜻은 아니에요.",true).apply{tag="analysis_latest_failure"})
            body.addView(ui.metric(if(a.kind=="EV_SOC")"최근 수신 충전량"else"최근 수신 누적거리","${VehicleAnalysis.fmt(latest.number)} ${a.unit}",vehicleDisplay.time(latest.at),"${if(System.currentTimeMillis()-latest.at>=600000)"예전에 받은 값이에요."else"앱이 받은 값이에요."}\n${if(latest.ref.value.source==ObservationSource.PLEOS_EMULATOR_SDK)"Pleos 에뮬레이터"else"차량 SDK"}","analysis_latest",if(a.kind=="EV_SOC")HmiIcon.BATTERY else HmiIcon.DISTANCE))
            val p=lowSoc.policy()
            body.addView(VehicleChart(this,a,palette,p.threshold.takeIf{p.enabled},vehicleDisplay::time))
            body.addView(label(a.textSummary,22).apply{tag="analysis_statistics"})
            body.addView(label(a.delta?.let{"바로 전보다 ${VehicleAnalysis.fmt(it)}${if(a.kind=="EV_SOC")"%p"else"km"}"}?:"바로 전 값과 비교하기 어려워요. 값이 끊겼거나 너무 적을 수 있어요.",24,true).apply{tag="analysis_delta"})
            if(p.enabled&&a.kind=="EV_SOC")body.addView(label("점선: 내가 정한 기준 ${p.threshold}% 아래 · 동그라미: 알림이 시작되거나 끝난 때\n기준을 바꾼 뒤라면, 그때의 기준은 자세히 보기에서 확인해 주세요.",20))
        }
        body.addView(label("그래프에서 찾은 내용",26,true))
        if(a.insights.isEmpty())body.addView(ui.state("더 설명하기엔 정보가 부족해요.","값이 같거나 적다고 해서 고장이나 연비를 말할 수는 없어요. 차에 보이는 표시를 함께 봐 주세요."))
        a.insights.take(8).forEach{i->val card=panel().apply{tag="insight_${i.kind}"};card.addView(label(i.title,24,true));card.addView(label(i.meaning,22));card.addView(label(i.action,22,true));card.addView(label(vehicleDisplay.time(i.last),20));body.addView(card)}
        body.addView(label("이 설명은 정해진 규칙으로 만든 거예요. AI가 쓴 글이 아니에요. 평균은 시간 평균이나 배터리 효율이 아니에요. 변화 시각은 차에서 바뀐 때가 아니라 앱이 값을 받은 때예요.",20))
        if(a.kind=="EV_SOC")body.addView(ui.action("최근 하루 충전량 AI 설명","portable_ai",ActionStyle.SECONDARY,canEdit()&&a.points.isNotEmpty(),HmiIcon.RECORD){edit{val request=android.content.Intent(this,PortableAiActivity::class.java);fixturePrefix?.let{request.putExtra("validation_fixture",it)};startActivityForResult(request,810)}})
        body.addView(ui.expandable("계산에 쓴 값 자세히 보기","analysis_detail",analysisDetail,canEdit()){edit{analysisDetail=!analysisDetail;draw()}})
        if(analysisDetail){
            body.addView(label("계산에 쓴 값: 고른 기간에 같은 곳에서 받은 값 ${a.points.size}건이에요(같은 값을 여러 번 받은 것도 포함). 선은 한 번 연결된 동안에만 이어져요. 10분 이상 비면 나눠서 보는데, 이건 이 앱의 기준이고 차의 규격은 아니에요. 한 번 연결된 동안이 한 번의 운전을 뜻하지는 않아요.",22))
            a.quality.forEach{(k,n)->body.addView(label("$k · ${n}건",22))}
            a.insights.take(8).forEach{i->body.addView(label("${i.kind} · ${i.rule}\n${vehicleDisplay.time(i.first)} → ${vehicleDisplay.time(i.last)}\n근거 ${i.evidence.joinToString{it.storeId+":"+it.rowId}}",20))}
            body.addView(label("그래프에 쓴 값 목록 · 최신 순",24,true))
            a.points.asReversed().drop(analysisPage*20).take(20).forEach{p->body.addView(label("${vehicleDisplay.time(p.at)} · ${VehicleAnalysis.fmt(p.number)}${a.unit}\n원본 ${p.ref.storeId}:${p.ref.rowId} · ${if(p.ref.value.detail.contains("CALLBACK"))"CALLBACK"else"GET/기존 읽기"} · 세션 ${p.ref.value.session}",20))}
            if(a.points.size>(analysisPage+1)*20)body.addView(button("다음 수신값 20개","analysis_more",enabled=canEdit()){analysisPage++;draw()})
            if(analysisPage>0)body.addView(button("가장 최근 수신값","analysis_reset_page",enabled=canEdit()){analysisPage=0;draw()})
        }
    }
    private fun aiPreviewScreen(body:LinearLayout){
        body.addView(ui.state("이 기기 안에서만 보는 요약이에요.","여기서는 밖으로 보내지 않아요. AI 설명은 로그인과 기기 등록이 필요하고, 분석 화면의 별도 버튼에서 쓸 수 있어요."))
        body.addView(label("AI에 보낼 수 있는 것: 고른 값의 숫자, 시각, 변화량, 통계, 받은 상태, 분석의 한계예요. 이름, 차량 식별 번호, 위치, 전체 데이터, 증상 원문은 보내지 않아요.",22))
        body.addView(label(JSONObjectPretty(aiPreview),20).apply{tag="ai_payload_preview"})
        body.addView(button("내용 확인했어요","ai_consent",enabled=canEdit()){aiConsent?.accept();notice="동의 범위만 확인했어요. 외부로 전송하지 않았어요.";draw()})
        body.addView(button("동의 취소 · 이전으로","ai_cancel",enabled=canEdit()){aiConsent?.revoke();aiConsent=null;aiPreview="";onBackPressed()})
    }
    private fun JSONObjectPretty(value:String)=runCatching{org.json.JSONObject(value).toString(2)}.getOrDefault(value)
    private fun historyRows(body:LinearLayout,card:String,limit:Int,offset:Int=0){
        val first=snapshots.first(card)?.id
        snapshots.timeline(card,limit,offset).forEach{s->
            val meaning=when{first==s.id->"처음 저장한 조회";s.changed->"이전 조회와 달라진 정보";else->"같은 값 다시 확인"}
            body.addView(ui.history(vehicleDisplay.time(s.at),"$meaning\n${VehicleUi.compact(s)}","snapshot_${s.id}",canEdit()){edit{openSnapshot(s)}})
        }
    }
    private fun historyPreview(body:LinearLayout,card:String){
        body.addView(label("최근 조회 기록",24,true))
        historyRows(body,card,3)
        body.addView(ui.action("전체 이력 · ${snapshots.snapshotCount(card)}건","all_vehicle_history",ActionStyle.QUIET,canEdit(),HmiIcon.CHEVRON){edit{openScreen(Screen.HISTORY){selectedCard=card;historyPage=0}}})
    }
    private fun snapshotScreen(body:LinearLayout){
        if(snapshotError.isNotBlank())body.addView(ui.state("기록을 정리하지 못했어요.","자세한 수신 기록을 확인한 뒤 다시 조회해 주세요.",true))
        body.addView(ui.action(if(hiddenSnapshots)"표시된 정보 보기"else"숨긴 정보 보기","toggle_hidden",ActionStyle.QUIET,canEdit(),HmiIcon.RECORD){edit{hiddenSnapshots=!hiddenSnapshots;draw()}})
        val cards=snapshots.cards(hiddenSnapshots)
        if(cards.isEmpty())body.addView(ui.state(if(hiddenSnapshots)"숨긴 정보가 없어요."else"아직 조회 기록이 없어요.","차량 정보에서 값을 불러와 주세요."))
        cards.forEach{s->
            val card=panel().apply{tag="snapshot_card"}
            card.addView(label(VehicleUi.source(s),20))
            card.addView(label(VehicleUi.compact(s),26,true))
            card.addView(label("조회 ${vehicleDisplay.time(s.at)}",22))
            card.addView(ui.action("조회 내용 보기","open_snapshot",ActionStyle.SECONDARY,canEdit(),HmiIcon.CHEVRON){edit{openSnapshot(s)}})
            card.addView(ui.action(if(hiddenSnapshots)"다시 표시"else"이 정보 숨기기","hide_snapshot",ActionStyle.QUIET,canEdit()){edit{snapshots.hide(s.cardId,!hiddenSnapshots);draw()}})
            body.addView(card);historyPreview(body,s.cardId)
        }
        body.addView(label("조회 시점의 정보예요. 실제 변화가 일어난 시각이나 고장 원인을 뜻하지 않아요.",20))
    }
    private fun snapshotDetail(body:LinearLayout){
        val s=selectedSnapshot?.let{snapshots.get(it)}?:run{body.addView(ui.state("기록을 찾지 못했어요.","이전 화면에서 다시 선택해 주세요.",true));return}
        metricGroup(body,s,true)
        body.addView(label("조회 시점의 정보예요. 증상 발생 당시의 값이나 원인이라는 뜻은 아니에요.",22))
        body.addView(ui.expandable("자세한 수신 기록","snapshot_technical",technicalExpanded,canEdit()){edit{technicalExpanded=!technicalExpanded;draw()}})
        if(technicalExpanded){
            val detail=panel();detail.addView(label(VehicleUi.technical(s),20).apply{tag="snapshot_raw"});body.addView(detail)
            body.addView(label("기기 시간대: ${ZoneId.systemDefault().id}",20))
        }
    }
    private fun historyScreen(body:LinearLayout){
        val card=selectedCard?:return
        val total=snapshots.snapshotCount(card);val offset=historyPage*20
        body.addView(label("${offset+1}–${(offset+20).coerceAtMost(total)} / ${total}건 · 최근 조회부터",22))
        historyRows(body,card,20,offset)
        if(historyPage>0)body.addView(button("더 최근 기록","history_newer",enabled=canEdit()){historyPage--;draw()})
        if(offset+20<total)body.addView(button("이전 기록 더보기","history_older",enabled=canEdit()){historyPage++;draw()})
        body.addView(label("수신 시각과 출처가 다른 조회예요. 하나의 물리 차량 변화라고 단정하지 않아요.",20))
    }
    private fun linkedVehicle(body:LinearLayout,s:Snapshot,testTag:String="linked_snapshot"){
        val section=column().apply{tag=testTag}
        section.addView(label("차량 정보가 기록에 연결됐어요.",24,true))
        section.addView(label("조회 ${vehicleDisplay.time(s.at)}\n증상 발생 당시 값이나 원인으로 확정하지 않아요.",20))
        section.addView(ui.action("함께 저장한 차량 정보 보기","linked_snapshot_detail",ActionStyle.QUIET,canEdit(),HmiIcon.VEHICLE){edit{openSnapshot(s)}});body.addView(section)
    }

    private fun loadDetails(body:LinearLayout){
        body.addView(ui.state("개발자용 정보", "일반 사용에는 필요 없어요. 차가 알려 준 원래 값과 연결 상태를 확인하는 자료예요. 차가 고장인지 아닌지와는 상관없어요."))
        fold(body,"개발자용 정보 펼치기","developer_info"){dev->
        fold(dev,"SDK·관측 원문 100개","load_raw"){details->
            details.addView(label("Vehicle SDK 2.3.2 · ${pleos.message}\n차량 측 측정시각은 제공되지 않아요.",20))
            observations.refs().takeLast(100).asReversed().forEach{ref->val v=ref.value
                details.addView(ui.state("${v.kind} · ${v.status}","값 ${v.raw?:"미수신"} · 단위 ${v.unit?:"없음"}\n지원 ${v.supported} · 유효 ${v.valid}\n앱 수신 ${v.receivedAt?.let(vehicleDisplay::time)?:"없음"}\n기록 시각 ${v.recordedAt}\n출처 ${v.source} · 세션 ${v.session}\n원본 ${ref.storeId}:${ref.rowId}\n${v.detail}"))
            }
        }
        fold(dev,"전송 없는 구조 요약 도구","analysis_preview_tool"){details->
            val a=analysisResults["EV_SOC"]
            details.addView(label("AI 요청·동의와 별개인 로컬 JSON 확인 도구예요.",20))
            details.addView(ui.action("구조 요약 보기 · 전송 없음","analysis_ai",ActionStyle.QUIET,canEdit()&&a?.points?.isNotEmpty()==true){edit{aiPreview=AnalysisExplanation.payload(a!!);aiConsent=AnalysisConsent(aiPreview);openScreen(Screen.AI_PREVIEW)}})
        }
        }
    }

    private fun welcome(body:LinearLayout){
        body.addView(label("내 차 기록을 다음 운전자에게 이어 줘요",32,true).apply{tag="welcome_title"})
        body.addView(ui.state("내 차 상태", "차가 알려 준 정보를 볼 수 있어요."))
        body.addView(ui.state("문제 남기기", "직접 적거나 말해서 남겨요. 남기기 전에 내용을 확인해요. AI 설명은 원할 때만 써요."))
        body.addView(ui.state("다음 운전자에게 넘기기", "남겨진 기록을 읽고, 확인한 내용을 이어서 적어요."))
        body.addView(label("차를 안전하게 세운 뒤 사용해 주세요. 차 전체가 고장인지는 알려 주지 않아요.",20))
        if(!guideReplay){
            body.addView(ui.action("시작하기","guide_choose",ActionStyle.PRIMARY,canEdit()){edit{finishGuide(false)}})
            body.addView(ui.action("나중에","guide_skip",ActionStyle.QUIET,canEdit()){edit{finishGuide(true)}})
        }
    }

    private fun finishGuide(skipped:Boolean){
        if(!canEdit())throw EditingBlockedException()
        guide.finish(skipped,canEdit());history.clear();screen=Screen.DRIVERS;guideReplay=false;draw()
    }
    private fun closeHelp(){
        if(!canEdit()||!captureDraft())return
        rememberLocation()
        if(screen==Screen.HELP_TOPIC) {screen=Screen.HELP;if(history.peekLast()==Screen.HELP)history.removeLast()}
        if(history.isNotEmpty())screen=history.removeLast() else screen=Screen.HOME
        guideReplay=false;draw()
    }
    private val helpTitles=listOf("기록할 사람 고르기","차량값 불러보기","느낀 문제 남기기","다른 사람의 기록 읽기","정비할 때 기록 보여주기","자동 확인 기능의 현재 범위")
    private fun help(body:LinearLayout){
        body.addView(label("세 가지만 기억해 주세요.",32,true))
        val cards=listOf("1. 먼저 넘어온 기록을 확인하세요." to "홈에서 다른 운전자가 남긴 기록과 내 차 안내를 봐요.",
            "2. 문제가 있으면 남겨 주세요." to "종류와 상황을 적어서 남겨요. AI 설명은 원할 때만 받아요.",
            "3. 다음 운전자에게 기록을 넘겨요." to "다음 사람은 자기 이름을 고른 뒤 넘어온 기록을 읽어요.")
        cards.forEach{(title,text)->body.addView(ui.state(title,text))}
        fold(body,"상황별 사용법","help_topics"){topics->helpTitles.forEachIndexed{i,title->topics.addView(ui.action(title,"help_topic_$i",ActionStyle.QUIET,canEdit()){edit{helpTopic=i;openScreen(Screen.HELP_TOPIC)}})}}
    }

    private fun helpTopic(body:LinearLayout){
        val lines=listOf(
            listOf("홈에서 ‘운전자 변경’을 눌러 이름을 고른 뒤, ‘이 이름으로 기록 시작’을 눌러 주세요.","이름을 새로 만들거나 고치는 곳은 설정·도움말의 ‘운전자’예요. 예전에 쓴 사람 이름은 그대로 남아요. 이름을 고르는 것은 본인 확인이나 차 시동과는 상관없어요."),
            listOf("홈 → ‘내 차 상태 보기’에서 차가 알려 준 배터리와 주행거리를 볼 수 있어요. 언제 받은 값인지, 예전 값인지도 함께 봐 주세요.","앱이 화면에 떠 있고 조작할 수 있을 때만 값을 불러와요. 내가 켠 배터리 알림도 같은 때에만 확인해요. 앱을 닫았거나 조작이 막힌 동안에는 값을 받지 못한 시간이 생겨요."),
            listOf("홈 → ‘문제 남기기’에서 종류와 상황을 적고 저장해요. 말로 입력하면 끝난 뒤에 글을 고칠 수 있어요.","‘저장하고 AI 설명 받기’는 먼저 기록을 남겨요. 다음 화면에서 AI에게 보낼 내용을 확인한 뒤 설명을 받을지 고를 수 있어요. 쓰던 글이 있으면 처음 쓴 사람 이름으로 이어서 써 주세요."),
            listOf("홈 → ‘넘겨받은 기록 보기’에서 다른 운전자가 남긴 기록을 열고, 읽은 뒤 ‘넘어온 기록을 읽었어요’를 눌러 주세요.","읽었다고 표시한다고 해서 점검이나 해결이 끝난 것은 아니에요. 이름을 고르거나 기록을 열어 보기만 해서는 읽음으로 표시되지 않아요."),
            listOf("기록 상세 → ‘점검·상태·상담’ → ‘정비소에 말할 내용 정리’에서 기록과 물어볼 내용을 보여 줄 수 있어요.","‘내비에서 정비소 찾기’를 누르면 설치된 내비게이션의 정비소 목록이 열려요. 장소를 골라 길 안내를 시작할 수 있어요. 이름이나 위도·경도를 입력하지 않아도 돼요. 기존 경로가 있으면 먼저 내비에서 확인해 주세요."),
            listOf("차 안내는 차가 알려 준 일부 값과 내가 정한 기준으로 보여 줘요. 차 전체를 진단하는 것은 아니에요.","문 위치 값, 등이 켜진 것, 타이어 공기압이 낮다는 기준을 고장이라고 말하지 않아요. 값이 오래됐거나 못 받았거나 실패했다고 해서 차가 괜찮다는 뜻은 아니에요.")
        );lines[helpTopic].forEach{body.addView(label(it,24))}
    }

    private fun recordForm(body:LinearLayout){
        speechFormVisible=true
        val active=journal?:run{
            body.addView(ui.state("작성 중인 내용이 없어요.","홈에서 문제를 남기거나 저장된 기록을 확인해 주세요."));return
        }
        val editing=active.kind=="EDIT"
        body.addView(label("기록 운전자 · ${active.ownerName}",20))
        body.addView(label("어떤 문제가 있었나요?",32,true))
        val fields=column();val controls=panel()
        fields.addView(label("어떤 종류인가요?",24,true))
        // Four big icon tiles (sound / shake / warning light / other). Same tags and actions as the old text buttons.
        val typeIcons=mapOf(SymptomType.NOISE to HmiIcon.SOUND,SymptomType.VIBRATION to HmiIcon.SHAKE,SymptomType.WARNING to HmiIcon.WARN,SymptomType.OTHER to HmiIcon.MORE)
        val types=SymptomType.entries.map{type->ui.choiceTile(type.label,"type_${type.name}",draft.type==type,canWrite(),typeIcons.getValue(type)){
            edit{if(captureDraft()){draft.type=type;captureDraft();draw()}}
        }}
        fields.addView(ui.grid(types,2).apply{tag="symptom_type"})
        situationInput=input("어떤 때, 무엇이 달랐는지 적어 주세요.",1000).apply{tag="situation";setText(draft.situation);minimumHeight=dp(142);minLines=3}
        fieldLabel(fields,"상황",situationInput!!);fields.addView(situationInput)
        situationInput!!.addTextChangedListener(watcher{captureDraft()})
        fields.addView(SpeechControls(this,ui,speechInput,{canWrite()},{voiceDraft()}))
        val speechObserver=speechInput.observer
        speechInput.observer={state->speechObserver(state);listOf("save_issue","save_issue_ai").forEach{tag->root.findViewWithTag<Button>(tag)?.isEnabled=canWrite()&&!recordSaving&&!state.busy}}
        fields.addView(ui.expandable("더 적고 싶으면 (선택)","record_extras",recordExtras,canEdit()){
            edit{if(captureDraft()){rememberLocation();recordExtras=!recordExtras;draw()}}
        })
        if(recordExtras){
            val extras=column()
            if(!editing){
                snapshots.latest()?.let{s->
                    val selected=snapshots.selected(active.id)==s.id
                    extras.addView(ui.action((if(selected)"✓ "else"")+"차량 정보 함께 저장","select_snapshot",ActionStyle.SECONDARY,canWrite(),HmiIcon.VEHICLE){edit{snapshots.select(active.id,if(selected)null else s.id);draw()}})
                    extras.addView(label("조회 ${VehicleUi.shortTime(s.at)} · ${VehicleUi.compact(s)}\n증상 당시 값이나 원인은 아니에요.",20))
                }
                extras.addView(button("발견 시각 · ${ConsultationSummary.time(draft.foundAt)}","discovered_at",enabled=canEdit()){pickTime()})
            }else extras.addView(label("처음 느낀 때 ·${ConsultationSummary.time(draft.foundAt)}",20))
            memoInput=input("추가로 남길 내용을 적어 주세요.",2000).apply{tag="memo";setText(draft.memo)}
            fieldLabel(extras,"추가 메모",memoInput!!);extras.addView(memoInput)
            memoInput!!.addTextChangedListener(watcher{captureDraft()});fields.addView(extras)
        }
        intent.getStringExtra("voice_draft")?.takeIf{it.length<=800&&it.isNotBlank()}?.let{candidate->
            fields.addView(button("음성 명령 내용 확인","adopt_voice_action",enabled=canWrite()){
                if(draft.situation.isNotBlank())toast("작성 중인 내용을 덮어쓰지 않아요. 먼저 확인해 주세요.")
                else if(canWrite()){situationInput?.setText(CaritdaActions.draft(candidate));captureDraft();intent.removeExtra("voice_draft");notice="내용을 확인한 뒤 저장해 주세요.";draw()}
            })
        }
        controls.addView(ui.sectionTitle("먼저 기록을 남겨요.",HmiIcon.RECORD))
        controls.addView(label("AI 설명이 필요하면 남긴 뒤에 고를 수 있어요. 보내기 전에 내용을 먼저 보여 드려요.",24))
        controls.addView(button(if(editing)"수정 내용 저장"else"저장","save_issue",primary=true,enabled=canWrite()&&!recordSaving&&!speechInput.busy){saveRecord()})
        controls.addView(ui.action(if(editing)"수정 저장하고 AI 설명 받기"else"저장하고 AI 설명 받기","save_issue_ai",ActionStyle.SECONDARY,canWrite()&&!recordSaving&&!speechInput.busy,HmiIcon.RECORD){if(canWrite()&&!speechInput.busy)saveRecord(openAi=true)})
        controls.addView(label("이 화면에서는 말한 내용을 입력란에만 넣어요. 내용을 확인한 뒤 저장해 주세요.",20))
        cancelDraftButton(controls)
        body.addView(ui.twoPane(fields,controls,columns(840)==2))
    }

    @Deprecated("Framework result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==810&&resultCode==RESULT_OK&&data?.getBooleanExtra("caritda_home",false)==true){aiHomeRequested=true;consumeAiHome()}
    }
    override fun onNewIntent(next:android.content.Intent){
        super.onNewIntent(next)
        if(next.getBooleanExtra("caritda_home",false)){aiHomeRequested=true;consumeAiHome()}
    }
    private fun consumeAiHome(){if(aiHomeRequested&&canEdit()){goHome();if(screen==Screen.HOME)aiHomeRequested=false}}
    private fun openIssueAi(id:String,afterSave:Boolean=false){
        if(!canEdit())return
        val request=android.content.Intent(this,PortableAiActivity::class.java).putExtra("issue_id",id).putExtra("issue_after_save",afterSave)
        // Same unique validation family across both Activities. Ordinary extras are ignored.
        fixturePrefix?.let{request.putExtra("validation_fixture",it)}
        startActivityForResult(request,810)
    }
    private fun saveRecord(openAi:Boolean=false){
        if(recordSaving||speechInput.busy)return
        if(!canWrite()){toast("정차 상태와 기록할 이름을 확인한 뒤 다시 저장해 주세요.");return}
        val active=journal ?: run{
            if(screen==Screen.DETAIL&&selectedIssue!=null){notice="저장되었습니다. 기록 목록에서도 확인하실 수 있어요.";feedback?.apply{text=notice;visibility=View.VISIBLE};return}
            notice="저장된 기록을 확인해 주세요.";recordFilter=RecordFilter.ALL;screen=Screen.ISSUES;history.clear();draw();return
        }
        if(!captureDraft())return
        recordSaving=true
        val save=root.findViewWithTag<Button>("save_issue");save?.isEnabled=false
        try {
            val selection=runCatching{if(active.kind=="RECORD")snapshots.selected(active.id)else null}
            val snapshot=selection.getOrNull()
            var linkFailed=selection.isFailure
            if(snapshot!=null)runCatching{snapshots.prepareLink(active.id,snapshot)}.onFailure{linkFailed=true}
            val issue=if(active.kind=="EDIT")service.revise(active.target!!,draft.type,draft.situation,draft.memo,active.id,active.revision)
                else service.record(draft.type,draft.situation,draft.foundAt,draft.memo,active.id)
            if(snapshot!=null&&!linkFailed)runCatching{snapshots.completeLink(issue.id)}.onFailure{linkFailed=true}
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken,0)
            android.util.Log.i("Caritda","record_commit id=${issue.id} revision=${issue.revision}")
            selectedIssue=issue.id;journal=null;lastDraft=null;draft=Draft()
            // Back must never resurrect the form whose draft was consumed by commit.
            history.clear();history.addLast(Screen.ISSUES);positions[Screen.DETAIL]=0
            if(active.kind!="EDIT"){recordFilter=RecordFilter.ALL;recordQuery="";searchText="";positions[Screen.ISSUES]=0}
            screen=Screen.DETAIL
            notice=if(active.kind=="EDIT"){
                if(issue.revision==active.revision)"변경된 내용이 없어 원래 기록을 유지했어요." else "수정되었습니다."
            }else "저장되었습니다. 기록 목록에서도 확인하실 수 있어요."
            if(linkFailed)notice+=" 증상은 저장됐지만 차량 정보 연결에 실패했어요. 이 기록에서 다시 연결해 주세요."
            if(active.kind=="EDIT"&&service.issues(recordFilter,recordQuery).none{it.id==issue.id})notice+=" 현재 검색·필터 조건과 달라 목록에서 보이지 않을 수 있어요. 전체 기록에서 확인해 주세요."
            draw()
            // Only a verified SQLite commit reaches this point. Opening AI never dispatches a model.
            if(openAi)runCatching{openIssueAi(issue.id,afterSave=true)}.onFailure{
                notice="기록은 저장됐어요. AI 화면을 열지 못했어요. 아래 AI 설명에서 다시 열어 주세요."
                feedback?.apply{text=notice;visibility=View.VISIBLE}
            }
        }catch(e:Exception){
            android.util.Log.e("Caritda","record_commit_failed draft=${active.id}",e)
            val reason=when(e){is IllegalArgumentException->e.message?:"입력 내용을 확인해 주세요.";is EditingBlockedException->"정차 상태와 기록할 이름을 확인해 주세요.";else->"저장 중 문제가 생겼어요. 입력 내용은 이 화면에 남아 있어요."}
            notice="저장하지 못했어요. $reason 내용을 확인한 뒤 다시 저장해 주세요."
            feedback?.apply{text=notice;visibility=View.VISIBLE}
            if(draft.situation.isBlank())situationInput?.apply{error="발생 상황을 입력해 주세요.";requestFocus()}
            save?.text="다시 저장"
        }finally{recordSaving=false;save?.isEnabled=canWrite()}
    }

    private fun detail(body:LinearLayout){
        val id=selectedIssue?:run{goHome();return};val issue=service.issue(id)
        val content=panel();content.addView(label(issue.situation,28,true).apply{tag="issue_body"})
        content.addView(ui.status("${issue.type.label} · ${statusText(issue.status)}"))
        content.addView(label("${issue.reporter.label} · 발견 ${ConsultationSummary.time(issue.discoveredAt)}",20))
        if(issue.memo.isNotBlank())fold(content,"추가 메모 보기","issue_memo"){it.addView(label(issue.memo,24))}
        body.addView(content)
        val unread=service.confirmed()&&service.driver().id!=issue.reporter.id&&service.needsAcknowledgement(issue)
        if(unread)body.addView(ui.action("넘어온 기록을 읽었어요","acknowledge",ActionStyle.PRIMARY,canWrite(),HmiIcon.CHECK){edit{service.acknowledge(id);notice="읽음 확인을 저장했어요. 점검 상태는 그대로예요.";draw()}})
        else{
            if(service.confirmed()&&service.driver().id!=issue.reporter.id)body.addView(label("읽었다고 표시했어요.",20).apply{tag="acknowledged"})
            body.addView(ui.action("확인한 내용 적기","add_note",ActionStyle.PRIMARY,canWrite(),HmiIcon.PLUS){edit{beginAction("NOTE",issue)}})
        }
        val primary=column();val secondary=column()
        val ai=panel();ai.addView(ui.sectionTitle("AI 문제 설명",HmiIcon.RECORD));ai.addView(label("가능한 원인과 점검할 내용을 참고해요. 고장 확정은 아니에요.",22))
        ai.addView(ui.action("AI 설명 보기","issue_ai_explain",ActionStyle.SECONDARY,canEdit(),HmiIcon.RECORD){openIssueAi(id)});primary.addView(ai)
        val linked=panel();linked.addView(ui.sectionTitle("위치·차량 정보",HmiIcon.VEHICLE));linkedLocations(linked,id)
        if(service.canManage(issue))linked.addView(ui.action("현재 위치 함께 저장","attach_location",ActionStyle.QUIET,canWrite()&&!locationReader.busy){attachLocation(id)})
        if(locationReader.busy)linked.addView(ui.action("위치 확인 취소","cancel_location",ActionStyle.QUIET,canWrite()){locationReader.cancel();draw()})
        snapshots.linked(id)?.let{linkedVehicle(linked,it)}
        if(service.canManage(issue)&&snapshots.linked(id)==null){
            linked.addView(ui.action(if(snapshots.pending(id))"차량 정보 연결 다시 시도"else"차량 정보 함께 저장","link_snapshot",ActionStyle.QUIET,canWrite(),HmiIcon.VEHICLE){
                confirm("차량 정보를 함께 저장할까요?","최근 조회값이며 증상 발생 당시 값은 아니에요. 원문과 상태는 바뀌지 않아요."){
                    if(!service.canManage(service.issue(id)))throw EditingBlockedException()
                    if(!snapshots.pending(id))snapshots.prepareLink(id,snapshots.latest()?.id?:error("먼저 차량 정보를 확인해 주세요."))
                    snapshots.completeLink(id);notice="차량 정보를 기록에 연결했어요.";draw()
                }
            })
        };secondary.addView(linked)
        body.addView(ui.twoPane(primary,secondary,columns(840)==2))
        draftBanner(body)
        fold(body,"점검·상태·상담","issue_actions"){actions->
            if(unread)actions.addView(ui.action("확인한 내용 적기","add_note",ActionStyle.SECONDARY,canWrite(),HmiIcon.PLUS){edit{beginAction("NOTE",issue)}})
            actions.addView(ui.action("상태 변경","change_status",ActionStyle.SECONDARY,canWrite(),HmiIcon.CHECK){edit{statusDialog(issue)}})
            actions.addView(ui.action("또 그랬어요","recurrence",ActionStyle.QUIET,canWrite(),HmiIcon.RECORD){edit{beginAction("RECURRENCE",issue)}})
            actions.addView(ui.action("정비소에 말할 내용 정리","issue_summary",ActionStyle.QUIET,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.SUMMARY)}})
            actions.addView(ui.action("기록 듣기 · 600자까지","speak_issue",ActionStyle.QUIET,canEdit()){readAloud(issue.type.label+". "+issue.situation+". 현재 상태는 "+statusText(issue.status)+"입니다.")})
            actions.addView(label("상태는 직접 확인한 대로 적어요. 읽었다고 해서 해결된 건 아니에요.",20))
        }
        if(service.canManage(issue))fold(body,"내 기록 관리","issue_manage"){manage->
            manage.addView(ui.action("내용 수정","edit_issue",ActionStyle.SECONDARY,canWrite(),HmiIcon.RECORD){edit{beginEdit(issue)}})
            manage.addView(ui.action("기록 지우기","delete_issue",ActionStyle.DANGER,canWrite()){
                confirm("이 기록을 삭제할까요?","목록과 정비 상담에서 제외해요. 원본과 이력은 기기에 남아요. 복구·영구 삭제 기능은 제공하지 않아요.","삭제"){
                    service.deleteOwned(id);selectedIssue=null;history.clear();recordFilter=RecordFilter.ALL;screen=Screen.ISSUES;positions[screen]=0;notice="기록을 삭제했어요. 다른 기록은 그대로예요.";draw()
                }
            })
        }
        fold(body,"확인·점검·수정 이력","issue_history"){historyBody->
            val events=service.events(id);historyBody.addView(label("다시 발생 ${events.count{it.kind==EventKind.RECURRENCE}}회",20))
            events.asReversed().forEach{event->val row=panel();row.addView(label("${event.kind.label} · ${event.actor.label}",22,true));row.addView(label("${ConsultationSummary.time(event.at)}\n${event.text}",20));historyBody.addView(row)}
        }
        if(service.issues(recordFilter,recordQuery).none{it.id==id})body.addView(ui.action("전체 기록에서 보기","show_in_all",ActionStyle.QUIET,canEdit()){edit{openRecords(RecordFilter.ALL)}})
    }

    private fun summary(body:LinearLayout){
        val issues=selectedIssue?.let{listOf(service.issue(it))}?:service.issues()
        body.addView(label(if(selectedIssue==null)"전체 기록 ${issues.size}건으로 상담을 준비해요."else"이 기록으로 상담을 준비해요.",24))
        body.addView(label("정비소에 저절로 보내거나 예약하지 않아요.",20))
        if(issues.isNotEmpty()){
            repairSearchPanel(body)
            // Legacy coordinate action stays available as a quiet fallback and for existing regression checks.
            body.addView(ui.action("고급 · 좌표로 직접 길찾기", "inspection_navigation",ActionStyle.QUIET,
                canEdit()&&!navigation.busy&&!repairSearch.busy,HmiIcon.VEHICLE){destination()})
        }
        navigationEvents.lastOrNull()?.let{event->
            body.addView(ui.state("길찾기 확인",InspectionNavigation.message(event.code)).apply{tag="navigation_progress"})
            fold(body,"길찾기 수신 이력","navigation_details"){it.addView(label(navigationEvents.joinToString("\n\n"){e->vehicleDisplay.time(e.at)+"\n"+InspectionNavigation.message(e.code)},20))}
        }
        if(navigation.busy)body.addView(ui.action("요청 결과 확인 멈추기","cancel_navigation_monitor",ActionStyle.QUIET,canEdit()){navigation.cancel();navigationEvents=navigation.events;draw()})
        issues.forEach{issue->
            val card=panel();card.addView(label(ConsultationSummary.build(issue,service.events(issue.id),::statusText),24).apply{tag="consultation_summary"})
            snapshots.linked(issue.id)?.let{linkedVehicle(card,it)}
            card.addView(ui.action("상담 내용 듣기 · 600자까지","speak_consultation_${issue.id}",ActionStyle.QUIET,canEdit()){readAloud(ConsultationSummary.build(issue,service.events(issue.id),::statusText))})
            val question="‘${issue.situation.take(300)}’ 증상에 어떤 점검이 필요할까요?"
            card.addView(ui.state("정비사에게 물어볼 내용",question).apply{tag="consultation_question"})
            card.addView(ui.action("질문 복사","copy_question_${issue.id}",ActionStyle.SECONDARY,canEdit(),HmiIcon.RECORD){edit{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("정비 상담 질문",question));notice="질문을 복사했어요.";draw()}});body.addView(card)
        }
    }

    private fun statusDialog(issue: VehicleIssue) {
        if(!canWrite())return
        val content=column().apply{setPadding(dp(24),dp(16),dp(24),dp(16))}
        content.addView(label("\"점검 완료\"는 살펴봤다는 뜻이에요. \"해결 확인\"은 직접 보고 나아진 것을 확인했다는 뜻이에요.",22))
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
    private fun confirm(title:String,message:String,positiveLabel:String="확인",action:()->Unit){
        if(!canEdit())return
        val dialog=AlertDialog.Builder(this,dialogTheme()).setTitle(title).setMessage(message)
            .setPositiveButton(positiveLabel,null).setNegativeButton("취소",null).create()
        activeDialog=dialog;dialog.show()
        // Large print for older drivers: title and message were left at the system default size.
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.textSize=26f
        dialog.findViewById<android.widget.TextView>(resources.getIdentifier("alertTitle","id","android"))?.apply{textSize=30f;maxLines=Int.MAX_VALUE;ellipsize=null}
        listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE).forEach{which->dialog.getButton(which).apply{minHeight=dp(76);minimumWidth=dp(76);textSize=30f;isSingleLine=false;maxLines=Int.MAX_VALUE;ellipsize=null}}
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
        if(!captureDraft())return
        rememberLocation()
        searchInput=null
        val origin=screen
        if(origin!=target)history.addLast(origin)
        menuReturn=null
        prepare();screen=target;draw()
    }
    private fun edit(action:()->Unit){
        try{if(!canEdit())throw EditingBlockedException();action()}
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
    private fun captureDraft():Boolean{
        if(!::service.isInitialized)return true
        val current=journal?:return true
        val next=when(screen){
            Screen.RECORD->{situationInput?.let{draft.situation=it.text.toString()};memoInput?.let{draft.memo=it.text.toString()};current.copy(type=draft.type,body=draft.situation,memo=draft.memo,foundAt=draft.foundAt)}
            Screen.ACTION->current.copy(body=actionField?.text?.toString()?:current.body)
            else->return true
        }
        val dirty=lastDraft?.id==next.id || next.body.isNotEmpty() || next.memo.isNotEmpty() || next.type!=SymptomType.NOISE || next.foundAt!=current.foundAt
        journal=next
        if(dirty && next!=lastDraft){
            try{service.saveDraft(next);lastDraft=next}
            catch(e:Exception){notice="초안을 기기에 보존하지 못했어요. 이 화면에서 내용을 유지하고 다시 시도해 주세요.";feedback?.apply{text=notice;visibility=View.VISIBLE};android.util.Log.e("Caritda","Draft write failed",e);return false}
        }
        return true
    }
    private fun column()=ui.column()
    private fun panel()=ui.card()
    private fun fold(body:LinearLayout,title:String,key:String,content:(LinearLayout)->Unit){
        val open=key in expandedSections
        body.addView(ui.expandable(title,key,open,canEdit()){edit{if(captureDraft()){rememberLocation();if(!expandedSections.add(key))expandedSections.remove(key);draw()}}})
        if(open)body.addView(column().also(content))
    }
    private fun fieldLabel(body:LinearLayout,title:String,field:EditText){
        if(field.id==View.NO_ID)field.id=View.generateViewId()
        body.addView(label(title,24,true).apply{labelFor=field.id})
    }
    private fun label(value:String,size:Int,bold:Boolean=false)=ui.text(value,size.coerceAtLeast(CaritdaTokens.SUPPORT),bold,size<22)
    private fun button(value:String,testTag:String,primary:Boolean=false,enabled:Boolean=true,action:()->Unit)=
        ui.action(value,testTag,if(primary)ActionStyle.PRIMARY else if(testTag=="delete_issue")ActionStyle.DANGER else if(testTag=="discard_draft")ActionStyle.QUIET else ActionStyle.SECONDARY,enabled){edit{action()}}
    private fun input(hintText:String,max:Int)=EditText(this).apply{
        hint=hintText;textSize=30f;minLines=3;maxLines=8;minHeight=dp(76)
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        filters=arrayOf(InputFilter.LengthFilter(max));setTextColor(ink);setHintTextColor(muted)
        setPadding(dp(20),dp(16),dp(20),dp(16));background=shape(cardColor,muted)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,dp(12))}
    }
    private fun shape(color:Int,stroke:Int?=null)=ui.shape(color,CaritdaTokens.BUTTON_RADIUS,stroke)
    private fun toast(message:String){notice=message;feedback?.apply{text=message;visibility=View.VISIBLE};Toast.makeText(this,message,Toast.LENGTH_LONG).show()}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
