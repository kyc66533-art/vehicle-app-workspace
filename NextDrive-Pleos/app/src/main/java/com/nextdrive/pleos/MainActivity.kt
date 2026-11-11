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
    private lateinit var snapshots:SnapshotStore
    private lateinit var vehicleDisplay:VehicleDisplaySettings
    private lateinit var lowSoc:LowSocStore
    private lateinit var socObserver:PleosSocObserver
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
    private var speechFormVisible=false
    private val speechOutput by lazy{SpeechOutput(this){visible&&canEdit()}}
    private val speechInput by lazy{SpeechInput(this){visible&&canWrite()}}
    private val locationReader by lazy{OneShotLocation(this){visible&&canWrite()}}
    private val navigation by lazy{InspectionNavigation(this){visible&&canEdit()}}
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
    private fun stopAssistance(){speechOutput.stop();speechInput.stop();locationReader.stop()
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
                            notice=if(added)"위치 연결을 저장했어요. 이전 연결도 그대로 남아 있어요."else"이미 연결한 같은 위치예요. 중복 저장하지 않았어요.";draw()
                        }
                    }
                }
                draw()
            }
        }
    }
    private fun linkedLocations(body:LinearLayout,id:String){
        locationStore().use{store->
            val count=store.count(id);val latest=store.latest(id)
            if(latest!=null){
                body.addView(ui.state("함께 저장한 마지막 위치",LocationPresentation.text(latest,vehicleDisplay::time)).apply{tag="linked_location"})
                body.addView(ui.expandable("위치 연결 이력 · ${count}건","location_history",locationHistory,canEdit()){locationHistory=!locationHistory;draw()})
                if(locationHistory){
                    store.history(id,locationHistoryLimit).forEach{item->body.addView(ui.state("사용자가 연결한 위치",LocationPresentation.text(item,vehicleDisplay::time)).apply{tag="location_history_item"})}
                    if(count>locationHistoryLimit&&locationHistoryLimit<200)body.addView(button("위치 이력 더 보기","location_history_more",enabled=canEdit()){locationHistoryLimit=(locationHistoryLimit+20).coerceAtMost(200);draw()})
                }
            }
        }
    }
    private fun destination(){
        if(!canEdit()||navigation.busy)return
        val fields=column();val name=input("정비 장소 이름",80);val lat=input("위도 (-90~90)",24);val lon=input("경도 (-180~180)",24)
        listOf(name,lat,lon).forEachIndexed{i,field->field.setText(navigationDraft[i]);field.tag=listOf("navigation_name","navigation_latitude","navigation_longitude")[i]}
        navigationFields=listOf(name,lat,lon)
        fields.addView(label("직접 확인한 정비 장소의 좌표를 입력해 주세요. 예약 기능은 아니에요.",22));listOf(name,lat,lon).forEach{fields.addView(it)}
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
    private var screen = Screen.HOME
    private var selectedIssue: String? = null
    private var recordFilter = RecordFilter.ALL
    private var recordQuery = ""
    private var searchText = ""
    private var searchInput: EditText? = null
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
        root = column().apply { setBackgroundColor(Color.rgb(14, 23, 34)); setPadding(dp(24), dp(16), dp(24), dp(16)) }
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
        root.addOnLayoutChangeListener { _,l,_,r,_,oldL,_,oldR,_ -> if(oldR>oldL && r-l!=oldR-oldL)root.post{draw()} }
        repository = if(fixturePrefix==null)SqliteIssueRepository(this)else SqliteIssueRepository(this,fixturePrefix+".db")
        observations = if(fixturePrefix==null)ObservationStore(this)else ObservationStore(this,fixturePrefix+"-observations.db")
        snapshots=if(fixturePrefix==null)SnapshotStore(this)else SnapshotStore(this,fixturePrefix+"-snapshots.db")
        vehicleDisplay=VehicleDisplaySettings(getSharedPreferences(fixturePrefix?.let{it+"-vehicle-display"}?:"caritda_vehicle_display",MODE_PRIVATE))
        lowSoc=if(fixturePrefix==null)LowSocStore(this)else LowSocStore(this,fixturePrefix+"-soc.db")
        socObserver=PleosSocObserver(this,observations,{visible&&canEdit()&&lowSoc.policy().enabled},{
            runCatching{lowSoc.reconcile(observations)}.onFailure{notice="차량 정보는 저장됐지만 안내 갱신에 실패했어요. 자동 안내에서 다시 시도해 주세요."}
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
            if(state!=EditingState.ALLOWED){stopAssistance();pleos.stop();stopSocWatch("안전 상태 변경으로 자동 확인을 멈췄어요.")} else {maybeAutomaticRead();maybeSocWatch()}
        }
        service = CaritdaService(repository, { safety.state == EditingState.ALLOWED })
        service.restoreConfiguration((lastNonConfigurationInstance as? RetainedSession)?.driverId)
        journal = runCatching { service.draft() }.getOrElse { notice="초안을 복원하지 못했습니다. 기존 초안은 보존됩니다.";null }
        lastDraft = journal
        pendingDriver = journal?.ownerId ?: repository.currentDriver()?.id
        savedInstanceState?.let {
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
        captureDraft()
        displayedSafety = EditingState.UNKNOWN
        draw()
        safety.start()
        reconcileSnapshots()
        freshnessHandler.removeCallbacks(freshnessTick);freshnessHandler.post(freshnessTick)
    }
    override fun onStop() { visible=false;stopAssistance();if(!isChangingConfigurations)entry=EntryReadState();captureDraft(); activeDialog?.dismiss(); freshnessHandler.removeCallbacks(freshnessTick);pleos.stop();stopSocWatch("앱이 보이지 않아 자동 확인을 멈췄어요. 관측 공백이 있어요."); safety.stop(); displayedSafety = EditingState.UNKNOWN; super.onStop() }
    override fun onDestroy() { aiConsent?.revoke();analysisWorker.shutdown();repository.close(); observations.close();snapshots.close();lowSoc.close();super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft()
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
    private fun maybeSocWatch(){if(::socObserver.isInitialized&&visible&&canEdit()&&screen!=Screen.WELCOME&&!pleos.busy&&lowSoc.policy().enabled)socObserver.start()}
    private fun stopSocWatch(reason:String){if(::socObserver.isInitialized){if(socObserver.running)runCatching{lowSoc.gap(reason)};socObserver.stop(reason)}}
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
        Screen.DETAIL->"기록 상세";Screen.SUMMARY->"정비 상담용 기록";Screen.VEHICLE->"차량 건강 상태"
        Screen.LOAD_DETAIL->"자세한 수신 기록";Screen.SNAPSHOTS->"차량 조회 기록";Screen.SNAPSHOT_DETAIL->"조회한 차량 정보"
        Screen.HISTORY->"전체 조회 이력";Screen.HELP->"사용법";Screen.HELP_TOPIC->helpTitles[helpTopic]
        Screen.ACTION->if(journal?.kind=="NOTE")"점검·조치 메모"else"다시 발생한 증상";Screen.WELCOME->""
        Screen.MENU->"설정·도움말";Screen.VEHICLE_SETTINGS->"차량 표시·시각"
        Screen.SOC_NOTICES->"충전량 자동 안내"
        Screen.ANALYTICS->"차량 데이터 분석"
        Screen.AI_PREVIEW->"AI 전송 내용 확인"
        Screen.HANDOVER->"다음 운전자 인계"
    }
    private fun chooseDriver(){
        if(service.draft()!=null&&service.confirmed()){
            if(screen==Screen.MENU){screen=menuReturn?:Screen.HOME;menuReturn=null}
            notice="작성 중인 내용을 먼저 저장하거나 취소해 주세요.";draw()
        }
        else{pendingDriver=service.draft()?.ownerId?:repository.currentDriver()?.id;openScreen(Screen.DRIVERS)}
    }
    private fun moreMenu(body:LinearLayout){
        val left=column();val right=column()
        val drivers=panel().apply{tag="driver_settings"};drivers.addView(ui.sectionTitle("기록 운전자",HmiIcon.PERSON))
        drivers.addView(ui.action("운전자 등록·이름 관리","manage_drivers",ActionStyle.SECONDARY,canEdit(),HmiIcon.PERSON){edit{openScreen(Screen.DRIVER_MANAGE)}});left.addView(drivers)
        val display=panel();display.addView(ui.sectionTitle("보기 편한 화면"));themeSetting(display)
        display.addView(ui.action("차량 표시·시각 설정","vehicle_settings",ActionStyle.QUIET,canEdit(),HmiIcon.VEHICLE){edit{openScreen(Screen.VEHICLE_SETTINGS)}});left.addView(display)
        val help=panel();help.addView(ui.sectionTitle("도움이 필요하신가요?",HmiIcon.RECORD))
        help.addView(ui.action("사용법 보기","help",ActionStyle.PRIMARY,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.HELP)}})
        help.addView(ui.action("처음 안내 다시 보기","replay_guide",ActionStyle.QUIET,canEdit()){edit{guideReplay=true;openScreen(Screen.WELCOME)}});right.addView(help)
        val privacy=panel();privacy.addView(ui.sectionTitle("AI·개인정보"));privacy.addView(label("AI는 저장한 문제에서 선택해요. 전송 전에 내용과 동의를 확인하며, 동의 철회도 같은 화면에서 할 수 있어요.",22))
        privacy.addView(label("기록은 이 기기의 운전자끼리 확인해요. 휴대폰이나 다른 차량으로 자동 전송하지 않아요.",20));right.addView(privacy)
        val about=panel();about.addView(ui.sectionTitle("앱 정보"));about.addView(label("CARITDA · 카잇다\n차량 기록을 다음 운전자에게 이어요.\n상표 검증 전 잠정 명칭이에요.",20));right.addView(about)
        body.addView(ui.grid(listOf(left,right),columns(840)))
    }
    private fun draw() {
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
        root.setBackgroundColor(backdrop);root.setPadding(dp(pageInset),dp(8),dp(pageInset),dp(8));root.removeAllViews()
        if(packageName.endsWith(".validation"))root.addView(label("검증 전용 · TEST",20,true).apply{tag="test_banner";contentDescription="검증 전용 TEST 앱"})
        val scroll=ScrollView(this).apply{isFillViewport=true;clipToPadding=false;tag="screen_scroll"};scrollView=scroll
        val body=column();scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        try {
            val available=if(root.width>0)root.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat()
            val helpScreen=screen in setOf(Screen.HELP,Screen.HELP_TOPIC)
            val header=navigationBar(available)
            val shortWindow=resources.configuration.screenHeightDp<480||resources.configuration.fontScale>=1.5f
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
    }
    private fun themeSetting(body:LinearLayout){
        val preference=CaritdaTheme.preferences(this,fixturePrefix).getString("theme","light")
        body.addView(label("화면 테마",22,true))
        val choices=listOf("light" to "밝게","dark" to "어둡게","system" to "기기 설정")
        body.addView(ui.grid(choices.map{(value,title)->ui.action((if(value==preference)"✓ "else"")+title,"theme_$value",ActionStyle.SECONDARY,canEdit()){
            edit{check(CaritdaTheme.preferences(this,fixturePrefix).edit().putString("theme",value).commit());notice="화면 테마를 저장했어요.";draw()}
        }.apply{isSelected=value==preference}},if(columns()==2)3 else 1))
    }
    private fun vehicleSettings(body:LinearLayout){
        body.addView(label("차량 동력 방식",26,true))
        body.addView(label("화면에 먼저 보여드릴 정보를 정해요. 차량이 알려준 차종이나 인증 정보가 아니에요.",22))
        body.addView(label("현재: ${vehicleDisplay.layoutDescription}",22).apply{tag="layout_current"})
        PowertrainLayout.entries.forEach{layout->
            body.addView(ui.action((if(vehicleDisplay.layout==layout)"✓ "else"")+layout.label,"layout_${layout.name}",ActionStyle.SECONDARY,canEdit()){
                edit{vehicleDisplay.setLayout(layout);notice="화면 배치를 저장했어요. 차량 정보와 이전 기록은 바꾸지 않아요.";draw()}
            }.apply{isSelected=vehicleDisplay.layout==layout})
        }
        body.addView(label("차종을 몰라도 이용할 수 있어요. 지원되지 않는 값은 만들지 않으며, 이전 조회 기록은 그대로 남아요.",20))
        body.addView(label("차량 조회 시각",26,true))
        body.addView(label("${vehicleDisplay.timeDescription}\n기기 시간대: ${ZoneId.systemDefault().id}\n차량 조회·연결 정보의 표시만 바뀌어요. 원본 시각과 기기 설정은 그대로예요.",22).apply{tag="time_zone_setting"})
        for(device in listOf(false,true))body.addView(ui.action((if(vehicleDisplay.deviceTime==device)"✓ "else"")+(if(device)"기기 시간 사용"else"한국 시간 사용"),if(device)"time_device"else"time_seoul",ActionStyle.SECONDARY,canEdit()){
            edit{vehicleDisplay.setDeviceTime(device);notice="시각 표시 설정을 저장했어요.";draw()}
        }.apply{isSelected=vehicleDisplay.deviceTime==device})
    }
    private fun statusText(status:IssueStatus)=status.label
    private fun sectionName()=when(screen){Screen.HOME->"홈";Screen.ISSUES->"문제";Screen.VEHICLE->"차량";Screen.DRIVERS,Screen.DRIVER_EDIT->"운전자";else->"기록"}
    private fun columns(minWidth:Int=600)=if((if(root.width>0)root.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat())>=minWidth && resources.configuration.fontScale<1.5f)2 else 1
    private fun home(body:LinearLayout){
        val all=service.issues();val unread=if(service.confirmed())all.filter{service.needsAcknowledgement(it)}else emptyList()
        val driver=column();driver.addView(label("기록 운전자",20))
        driver.addView(label(if(service.confirmed())service.driver().label else"기록할 사람을 확인해 주세요",26,true))
        val change=ui.action(if(service.confirmed())"운전자 변경"else"운전자 확인","change_driver",ActionStyle.QUIET,canEdit(),HmiIcon.PERSON){edit{chooseDriver()}}
        body.addView(ui.grid(listOf(driver,change),columns()))
        val primary=column();val secondary=column()
        if(unread.isNotEmpty()){
            val card=ui.attention(AttentionContent("이전 운전자가 전할 내용이 있어요",unread.first().situation.take(100),"미확인 ${unread.size}건 · 읽음과 점검은 별개예요."))
            card.addView(ui.action("인계 확인하기 · ${unread.size}건","handover",ActionStyle.PRIMARY,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.HANDOVER)}});primary.addView(card)
        }
        val checks=vehicleChecks();healthFreshness=VehicleHealth.labels.keys.mapNotNull{observations.mostRecent(it,true)?.receivedAt}
        if(checks.isNotEmpty()){
            val first=checks.first();val card=ui.checkCard(first.title,first.reason,first.origin).apply{tag="home_vehicle_attention"}
            card.addView(label(vehicleDisplay.time(first.reading.receivedAt!!)+(if(checks.size>1)" · 그 밖에 ${checks.size-1}개 항목"else""),20))
            card.addView(ui.action("차량 안내 확인하기","open_vehicle_check",ActionStyle.SECONDARY,canEdit(),HmiIcon.VEHICLE){edit{openScreen(Screen.VEHICLE)}});primary.addView(card)
        }
        if(service.draft()!=null)draftBanner(primary)else{
            val create=panel();create.addView(ui.sectionTitle("어떤 문제가 있었나요?",HmiIcon.PLUS))
            create.addView(label("말하거나 직접 적은 뒤 저장해 주세요.",22))
            create.addView(ui.action("문제 남기기","new_issue",if(unread.isEmpty()&&checks.isEmpty())ActionStyle.PRIMARY else ActionStyle.SECONDARY,canEdit(),HmiIcon.PLUS){edit{beginRecord()}});primary.addView(create)
        }
        val health=panel();health.addView(ui.sectionTitle("차량 건강 상태",HmiIcon.VEHICLE))
        health.addView(label(if(pleos.busy)"차량 정보를 확인하고 있어요."else"지원되는 차량 정보와 안내를 확인해요.",22).apply{tag="auto_read_status"})
        health.addView(ui.action("차량 상태 보기","vehicle_health",ActionStyle.SECONDARY,canEdit(),HmiIcon.VEHICLE){edit{openScreen(Screen.VEHICLE)}});secondary.addView(health)
        val records=panel();records.addView(ui.sectionTitle("기록·인계",HmiIcon.RECORD));records.addView(label("저장한 기록 ${all.size}건",22).apply{tag="home_counts"})
        records.addView(ui.action("기록 전체 보기","all_issues",ActionStyle.SECONDARY,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.ISSUES)}});secondary.addView(records)
        body.addView(ui.twoPane(primary,secondary,columns(840)==2))
        pausedGuide?.let{paused->body.addView(ui.action("안내 이어보기","resume_help",ActionStyle.QUIET,canEdit()){pausedGuide=null;screen=paused;draw()})}
    }
    private fun vehicleChecks()=VehicleAttention.list({observations.mostRecent(it)},{observations.mostRecent(it,true)},lowSoc.policy())
    private fun handover(body:LinearLayout){
        if(!service.confirmed()){
            body.addView(ui.state("기록 운전자를 확인해 주세요.","선택한 사람에게 남은 인계 내용을 보여드려요."))
            body.addView(ui.action("운전자 확인","change_driver",ActionStyle.PRIMARY,canEdit(),HmiIcon.PERSON){edit{chooseDriver()}});return
        }
        body.addView(label("${service.driver().label}님이 아직 확인하지 않은 다른 운전자의 기록이에요.",22))
        val pending=service.issues().filter{service.needsAcknowledgement(it)}
        body.addView(label("미확인 ${pending.size}건",24,true).apply{tag="handover_count"})
        if(pending.isEmpty())body.addView(ui.state("미확인 인계가 없어요.","읽음은 차량 정상이나 점검 완료를 뜻하지 않아요."))
        pending.forEach{issueCard(body,it)}
    }

    private fun issues(body:LinearLayout){

        body.addView(button("증상 남기기","new_issue",primary=true,enabled=canWrite()){beginRecord()})
        draftBanner(body)
        val all=service.issues()
        if(recordFilter==RecordFilter.MINE&&!service.confirmed())recordFilter=RecordFilter.ALL
        val chosen=service.issues(recordFilter,recordQuery)
        body.addView(label("카테고리·발생 상황·추가 메모에서 찾아요.",20))
        searchInput=input("단어나 상황으로 찾기",100).apply{tag="record_search";minLines=1;maxLines=2;setText(searchText)}
        body.addView(searchInput)
        body.addView(button("검색","apply_search",enabled=canEdit()){
            recordQuery=searchInput?.text?.toString()?.trim().orEmpty();searchText=recordQuery
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken,0)
            positions[Screen.ISSUES]=0;draw()
        })
        val filters=LinearLayout(this).apply{orientation=if(root.width/resources.displayMetrics.density>=600 && resources.configuration.fontScale<1.5f)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL}
        RecordFilter.entries.forEach{filter->
            val count=if(filter==RecordFilter.MINE&&!service.confirmed())null else service.issues(filter,recordQuery).size
            val item=button((if(recordFilter==filter)"✓ " else "")+filter.label+(count?.let{" $it"}?:""),"filter_${filter.name}",enabled=canEdit()&&(filter!=RecordFilter.MINE||service.confirmed())){recordFilter=filter;positions[Screen.ISSUES]=0;draw()}
            if(filters.orientation==LinearLayout.VERTICAL)filters.addView(item) else filters.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})
        };body.addView(filters)
        body.addView(label("${recordFilter.label} · ${if(recordQuery.isBlank())"검색어 없음" else "검색: $recordQuery"}\n결과 ${chosen.size}건 · 저장된 전체 기록 ${all.size}건",24,true).apply{tag="record_filter_status"})
        body.addView(button("조건 초기화","reset_conditions",enabled=canEdit()){recordFilter=RecordFilter.ALL;recordQuery="";searchText="";searchInput?.setText("");positions[Screen.ISSUES]=0;draw()})
        body.addView(label("아직 읽지 않은 전달 기록부터 보여드려요.",20))
        if(chosen.isEmpty()){
            body.addView(label(if(all.isEmpty())"아직 남긴 기록이 없어요. 차량이 정상이라는 뜻은 아니에요." else "조건에 맞는 기록이 없어요. 다른 기록은 조건을 초기화해서 확인해 주세요.",24))
            if(all.isNotEmpty())body.addView(button("전체 기록 보기","show_all_records",enabled=canEdit()){openRecords(RecordFilter.ALL)})
        }
        chosen.sortedBy{if(service.confirmed()&&service.needsAcknowledgement(it))0 else 1}.forEach{issueCard(body,it)}
        body.addView(button("정비 상담용 요약 · 전체 ${all.size}건","all_summary",enabled=canEdit()&&all.isNotEmpty()){selectedIssue=null;openScreen(Screen.SUMMARY)})
    }
    private fun issueCard(body:LinearLayout,issue:VehicleIssue){
        body.addView(ui.issueRecord(issue.type.label,statusText(issue.status),issue.situation.take(100),
            "${ConsultationSummary.time(issue.discoveredAt)} · ${issue.reporter.label}",
            if(service.confirmed()&&service.needsAcknowledgement(issue))"아직 읽지 않은 전달 기록"else"직접 남긴 증상",
            "issue_${issue.id}",canEdit()){edit{selectedIssue=issue.id;openScreen(Screen.DETAIL)}})
    }
    private fun driverScreen(body:LinearLayout){

        body.addView(label("기록에 사용할 이름이나 별명을 골라 주세요.",20))
        val drivers=service.drivers()
        if(drivers.isEmpty())body.addView(label("등록된 운전자가 없습니다. 먼저 이름을 등록해 주세요.",24))
        val choices=drivers.map{driver->
            ui.action((if(pendingDriver==driver.id)"✓ "else"")+driver.label+(if(pendingDriver==driver.id)" · 선택됨"else""),"driver_${driver.id}",ActionStyle.SECONDARY,canEdit(),HmiIcon.PERSON){
                edit{pendingDriver=driver.id;notice="이름을 골랐어요. 운전 시작을 눌러 주세요.";draw()}
            }.apply{isSelected=pendingDriver==driver.id}
        }
        body.addView(ui.grid(choices,columns()))
        body.addView(button("운전자 추가","add_driver",enabled=canEdit()){editingDriver=null;driverNameDraft=null;openScreen(Screen.DRIVER_EDIT)})
        body.addView(button("운전자 관리","manage_drivers",enabled=canEdit()){openScreen(Screen.DRIVER_MANAGE)})
        val candidate=drivers.find{it.id==pendingDriver}
        val driverControls=if(root.height/resources.displayMetrics.density<480||resources.configuration.fontScale>=1.5f)body else root
        driverControls.addView(label("이 이름으로 기록을 남겨요",20))
        driverControls.addView(button("${candidate?.label ?: "운전자 선택 후"} · 운전 시작","start_driving",primary=true,enabled=canEdit()&&candidate!=null){
            edit{service.selectDriver(candidate!!);notice="";history.clear();screen=Screen.HOME
                pendingRecordFilter?.let{recordFilter=it;recordQuery="";searchText="";screen=Screen.ISSUES};pendingRecordFilter=null;draw()}
        })
        body.addView(button("선택 취소 · 홈으로","cancel_driver",enabled=canEdit()){pendingDriver=repository.currentDriver()?.id;pendingRecordFilter=null;goHome()})
        draftBanner(body)
    }
    private fun driverManage(body:LinearLayout){

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
            captureDraft();edit{
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
        if(!fromSnapshot)body.addView(label(vehicleDisplay.layoutDescription,20).apply{tag="powertrain_context"})
        val cards=kinds.map{kind->
            val pair=pair(kind)
            val view=VehiclePresentation.card(kind,pair.first,pair.second,System.currentTimeMillis(),vehicleDisplay::time)
            val value=if(pair.second==null)when(pair.first?.status){ObservationStatus.UNSUPPORTED->"지원되지 않음";null->"확인 전";else->"확인할 수 없음"}else view.value
            ui.metric(VehicleInfoPolicy.name(kind,layout),value,view.time,view.attempt,kind,when(kind){"ODOMETER"->HmiIcon.DISTANCE;"FUEL_PERCENT"->HmiIcon.FUEL;else->HmiIcon.BATTERY})
        }
        body.addView(ui.grid(cards,columns()))
        body.addView(label(vehicleDisplay.timeDescription,20).apply{tag="vehicle_time_zone"})
        val source=if(fromSnapshot)VehicleUi.source(snapshot)else{
            val item=observations.mostRecent("EV_SOC")?:observations.mostRecent("ODOMETER")
            when(item?.source){ObservationSource.PLEOS_EMULATOR_SDK->"에뮬레이터에서 받은 값이에요.";ObservationSource.PLEOS_VEHICLE_SDK->"차량 SDK에서 받은 값이에요.";else->"아직 차량 정보를 받지 못했어요."}
        }
        body.addView(label(source,20).apply{tag="vehicle_source"})
    }
    private fun healthSummary(body:LinearLayout){
        val policy=lowSoc.policy()
        val readings=(listOf("EV_SOC")+VehicleHealth.labels.keys).map{kind->DriveCheck.assess(kind,observations.mostRecent(kind),observations.mostRecent(kind,true),policy)}
        healthFreshness=readings.mapNotNull{it.receivedAt}
        val attention=readings.filter{it.attention}
        if(attention.isNotEmpty()){
            body.addView(ui.state("DriveCheck · 확인할 항목", "받은 차량 안내 또는 사용자 기준에 해당해요. 자동 고장 판정이나 저장된 사용자 문제가 아니에요.").apply{tag="drivecheck_attention"})
            attention.forEach{reading->
                val card=panel();card.addView(ui.state(reading.title,reading.text(vehicleDisplay::time)))
                card.addView(ui.action("직접 확인한 증상 남기기","drivecheck_draft_${reading.kind}",ActionStyle.SECONDARY,canWrite(),HmiIcon.RECORD){beginFromCheck(reading.kind)})
                if(!canWrite())card.addView(label("증상을 남기려면 먼저 기록할 사람으로 운전 시작을 눌러 주세요.",20))
                body.addView(card)
            }
        }
        if(service.draft()!=null)draftBanner(body)
        body.addView(ui.expandable("문·등화·차량 안내 확인 · DriveCheck","health_expand",showHealth,canEdit()){edit{showHealth=!showHealth;draw()}})
        if(!showHealth)return
        body.addView(label("받은 항목만 표시해요. 등화 켜짐은 전구·LED의 정상 작동을 보증하지 않아요.",20))
        val cards=readings.map{reading->ui.state(reading.title,reading.text(vehicleDisplay::time)).apply{tag="drivecheck_item_${reading.kind}"}}
        body.addView(ui.grid(cards,columns()))
        body.addView(label("타이어 항목은 차량이 제공한 저압 안내 기준이에요. 실제 타이어 압력·TPMS 경고 전체·등화 고장은 일반 앱 지원을 확인하지 못했어요.",20))
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
        body.addView(ui.loading(if(pleos.busy)"차량 정보를 불러오는 중이에요."else"앱을 열 때 지원되는 값을 한 번 확인해요.").apply{tag="load_status"})
        metricGroup(body)
        healthSummary(body)
        val refresh=ui.action("차량값 불러오기","read_vehicle",ActionStyle.PRIMARY,canEdit()&&!pleos.busy,HmiIcon.VEHICLE){edit{entry.markRequested();pleos.readOnce()}}
        val history=ui.action("조회 기록 보기","auto_snapshots",ActionStyle.SECONDARY,canEdit(),HmiIcon.CLOCK){edit{openScreen(Screen.SNAPSHOTS)}}
        body.addView(ui.grid(listOf(refresh,history),columns()))
        body.addView(ui.action("충전량 자동 안내 · 설정과 기록","soc_notices",ActionStyle.QUIET,canEdit(),HmiIcon.BATTERY){edit{openScreen(Screen.SOC_NOTICES)}})
        body.addView(ui.action("차량 데이터 분석","vehicle_analytics",ActionStyle.SECONDARY,canEdit(),HmiIcon.DISTANCE){edit{requestAnalytics();openScreen(Screen.ANALYTICS)}})
        if(packageName=="com.nextdrive.pleos.validation")body.addView(ui.action("Gemini 연결 시험 · 합성 SOC","gemini_poc",ActionStyle.QUIET,canEdit(),HmiIcon.RECORD){edit{startActivity(android.content.Intent().setClassName(this,"com.nextdrive.pleos.GeminiPocActivity"))}})
        if(packageName=="com.nextdrive.pleos.validation")body.addView(ui.action("Firebase 로그인 호환성 확인","firebase_auth_probe",ActionStyle.QUIET,canEdit(),HmiIcon.PERSON){edit{startActivity(android.content.Intent().setClassName(this,"com.nextdrive.pleos.AuthCompatibilityActivity"))}})
        body.addView(label("차량 전체의 고장 여부를 판단한 결과는 아니에요.",20))
        body.addView(ui.expandable("제공되지 않는 항목","unavailable",showUnavailable,canEdit()){edit{showUnavailable=!showUnavailable;draw()}})
        if(showUnavailable){
            val fuel=VehiclePresentation.card("FUEL_PERCENT",observations.mostRecent("FUEL_PERCENT"),observations.mostRecent("FUEL_PERCENT",true),System.currentTimeMillis())
            body.addView(label("연료 잔량 · ${fuel.attempt.ifEmpty{fuel.value}}",22).apply{tag="fuel_status"})
        }
        body.addView(ui.action("자세한 수신 기록","load_details",ActionStyle.QUIET,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.LOAD_DETAIL)}})
        snapshots.latest()?.let{historyPreview(body,it.cardId)}
    }
    private fun openSnapshot(s:Snapshot){openScreen(Screen.SNAPSHOT_DETAIL){selectedSnapshot=s.id;technicalExpanded=false}}
    private fun socNotices(body:LinearLayout){
        val p=lowSoc.policy()
        val current=panel().apply{tag="soc_current"}
        current.addView(label("최근 충전량",26,true))
        val view=VehiclePresentation.card("EV_SOC",observations.mostRecent("EV_SOC"),observations.mostRecent("EV_SOC",true),System.currentTimeMillis(),vehicleDisplay::time)
        current.addView(label(view.value,36,true).apply{tag="soc_current_value"})
        current.addView(label(view.time,22));if(view.attempt.isNotBlank())current.addView(label(view.attempt,22))
        current.addView(label(view.source,20));body.addView(current)
        body.addView(label("${if(p.enabled)"사용자 안내 켜짐"else"사용자 안내 꺼짐"} · ${p.threshold}% 미만",26,true).apply{tag="soc_policy"})
        body.addView(label(socObserver.message,22).apply{tag="soc_watch_status"})
        val notices=lowSoc.notices()
        val latest=observations.mostRecent("EV_SOC")
        body.addView(ui.state("현재 안내",SocGuide.text(p,notices,latest)).apply{tag="soc_core"})
        if(DriveCheck.assess("EV_SOC",latest,observations.mostRecent("EV_SOC",true),p).attention)body.addView(ui.action("이 안내에서 직접 증상 남기기","drivecheck_draft_EV_SOC",ActionStyle.SECONDARY,canWrite(),HmiIcon.RECORD){beginFromCheck("EV_SOC")})
        if(service.draft()!=null)draftBanner(body)
        body.addView(label("사용자 편의 안내예요. 고장 원인·수리 완료·운행 안전을 판단한 결과는 아니에요.",20))
        body.addView(label("앱이 보이고 입력이 허용된 동안만 SOC 변화를 확인해요. 종료·제한·연결 끊김 동안에는 관측 공백이 있어요.",20))
        body.addView(label("사용자 편의 기준이에요. 제조사 고장 기준이 아니며, 안내 해제는 기준보다2%p 높은 값부터 기록해요.",20))
        socThresholdInput=input("안내 기준(1~95%)",2).apply{tag="soc_threshold";inputType=InputType.TYPE_CLASS_NUMBER;setText(socThresholdDraft?:p.threshold.toString());minLines=1}
        body.addView(socThresholdInput)
        body.addView(button("이 기준으로 안내 켜기","enable_soc",enabled=canEdit()){
            val threshold=socThresholdInput?.text?.toString()?.toIntOrNull()?:throw IllegalArgumentException("안내 기준을 숫자로 입력해 주세요.")
            lowSoc.configure(true,threshold,observations);socThresholdDraft=null;socThresholdInput=null;notice="사용자 기준을 저장했어요. 새로 받은 값부터 안내해요.";stopSocWatch("설정을 반영하고 있어요.");maybeSocWatch();draw()
        })
        if(p.enabled)body.addView(button("안내 끄기","disable_soc",enabled=canEdit()){lowSoc.configure(false,p.threshold,observations);stopSocWatch("자동 안내를 껐어요. 이전 기록은 남아 있어요.");draw()})
        body.addView(button("자동 기록 다시 정리","reconcile_soc",enabled=canEdit()){lowSoc.reconcile(observations);notice="저장된 관측으로 안내를 다시 확인했어요.";draw()})
        body.addView(label("이 안내는 기기 내 규칙으로 만들어요. AI 설명은 직접 저장한 증상에서 선택해요. 차량 수치를 자동 전송하지 않아요.",22).apply{tag="ai_connection_status"})
        body.addView(ui.expandable("수신 근거 자세히","soc_evidence",socTechnical,canEdit()){edit{socTechnical=!socTechnical;draw()}})
        body.addView(ui.expandable("이전 안내 기록 · ${notices.size}건","soc_history",socHistory,canEdit()){edit{socHistory=!socHistory;draw()}})
        if(notices.isEmpty())body.addView(ui.state("아직 낮은 충전량 안내가 없어요.","실제로 새 값을 받은 뒤에만 사용자 기준과 비교해요."))
        if(socHistory)notices.take(socHistoryLimit).forEach{n->
            val card=panel().apply{tag="soc_notice_${n.id}"}
            card.addView(label(if(n.phase=="LOW")"안내 유지 기록"else"조건 해제 기록",26,true))
            card.addView(label("사건 마지막 수신 ${n.value}${n.unit}\n${vehicleDisplay.time(n.last)}",22))
            lowSoc.changes().filter{it.episode==n.id}.forEach{e->card.addView(label("${if(e.kind=="USER_LOW_SOC")"안내 진입"else"조건 해제"} · ${e.raw}% · 당시 기준 ${e.threshold}% 미만\n${vehicleDisplay.time(e.at)}",22))}
            card.addView(label("이전 사건이며 위의 최근 충전량과 구분해 주세요.",20))
            if(socTechnical)card.addView(label("규칙 ${SocRule.VERSION}:${n.version} · 실제 수신 근거 ${n.store}:${n.row}",20))
            if(System.currentTimeMillis()-n.last>=600000||n.last>System.currentTimeMillis()+60000)card.addView(label("오래된 이전 관측이에요. 현재 충전량은 다시 확인해 주세요.",22))
            body.addView(card)
        }
        if(socHistory&&notices.size>socHistoryLimit)body.addView(button("이전 안내 더 보기","soc_history_more",enabled=canEdit()){socHistoryLimit+=20;draw()})
    }
    private fun analyticsScreen(body:LinearLayout){
        body.addView(label("새로 받은 값을 자동으로 분석해요. 이 화면을 열어도 차량을 다시 조회하지 않아요.",20))
        body.addView(ui.grid(listOf("EV_SOC" to "충전량","ODOMETER" to "누적거리").map{(kind,name)->ui.action(name,"analysis_$kind",if(kind==analysisKind)ActionStyle.PRIMARY else ActionStyle.SECONDARY,canEdit(),if(kind=="EV_SOC")HmiIcon.BATTERY else HmiIcon.DISTANCE){edit{analysisKind=kind;analysisPage=0;draw()}}},columns()))
        body.addView(ui.grid(listOf(1 to "최근 하루",7 to "최근 일주일",0 to "최근 최대 2000건").map{(days,name)->ui.action(name,"analysis_days_$days",if(days==analysisDays)ActionStyle.PRIMARY else ActionStyle.QUIET,canEdit(),HmiIcon.CLOCK){edit{analysisDays=days;analysisPage=0;analysisResults=emptyMap();requestAnalytics();draw()}}},columns()))
        if(analysisBusy)body.addView(ui.loading("저장된 수신값을 분석하고 있어요."))
        if(analysisError.isNotBlank())body.addView(ui.state("분석을 갱신하지 못했어요.",analysisError,true))
        val a=analysisResults[analysisKind]?:run{body.addView(ui.state("분석을 준비하고 있어요.","수신 기록이 없으면 그래프를 만들지 않아요."));return}
        body.addView(label("분석 범위 · ${if(a.since==0L)"저장된 최근 최대 2000행"else vehicleDisplay.time(a.since)}\n${vehicleDisplay.time(a.until)}까지",20).apply{tag="analysis_period"})
        if(a.points.isEmpty()){body.addView(ui.state("유효한 차량값이 없어요.","미지원·오류를 0으로 바꾸지 않아요. 차량 정보에서 연결 상태를 확인해 주세요."))}else{
            val latest=a.latest!!
            if(a.lastAttempt?.value?.let{!SnapshotStore.accepted(it)}==true)body.addView(ui.state("최근 수신을 확인해 주세요.","아래 값은 이전 성공값이에요. 최신 조회 실패·미지원·유효성 오류를 정상 상태로 판단하지 않아요.",true).apply{tag="analysis_latest_failure"})
            body.addView(ui.metric(if(a.kind=="EV_SOC")"최근 수신 충전량"else"최근 수신 누적거리","${VehicleAnalysis.fmt(latest.number)} ${a.unit}",vehicleDisplay.time(latest.at),"${if(System.currentTimeMillis()-latest.at>=600000)"이전 값 · 오래된 수신이에요."else"앱이 받은 값이에요."}\n${if(latest.ref.value.source==ObservationSource.PLEOS_EMULATOR_SDK)"Pleos 에뮬레이터"else"차량 SDK"}","analysis_latest",if(a.kind=="EV_SOC")HmiIcon.BATTERY else HmiIcon.DISTANCE))
            val p=lowSoc.policy()
            body.addView(VehicleChart(this,a,palette,p.threshold.takeIf{p.enabled},vehicleDisplay::time))
            body.addView(label(a.textSummary,22).apply{tag="analysis_statistics"})
            body.addView(label(a.delta?.let{"직전 수신 대비 ${VehicleAnalysis.fmt(it)}${if(a.kind=="EV_SOC")"%p"else"km"}"}?:"직전값과 안전하게 비교할 수 없어요. 세션·공백·표본을 확인해 주세요.",24,true).apply{tag="analysis_delta"})
            if(p.enabled&&a.kind=="EV_SOC")body.addView(label("점선: 사용자 기준 ${p.threshold}% 미만 · 원: 저장된 진입/해제 지점\n기준이 바뀐 이전 사건은 당시 기준을 상세에서 확인해 주세요.",20))
        }
        body.addView(label("자동으로 찾은 내용",26,true))
        if(a.insights.isEmpty())body.addView(ui.state("추가 해석을 보류해요.","같은 값의 반복·표본 부족만으로 고장이나 효율을 판단하지 않아요. 최근 차량 표시를 확인해 주세요."))
        a.insights.take(8).forEach{i->val card=panel().apply{tag="insight_${i.kind}"};card.addView(label(i.title,24,true));card.addView(label(i.meaning,22));card.addView(label(i.action,22,true));card.addView(label(vehicleDisplay.time(i.last),20));body.addView(card)}
        body.addView(label("분석 설명은 규칙으로 작성했어요. AI 모델 응답이 아니에요. 관측값 평균은 시간 평균·배터리 효율이 아니며, 변화 시각은 실제 변화 발생 시각이 아닌 앱 수신 시각이에요.",20))
        if(a.kind=="EV_SOC")body.addView(ui.action("AI로 쉽게 설명","portable_ai",ActionStyle.SECONDARY,canEdit()&&a.points.isNotEmpty(),HmiIcon.RECORD){edit{startActivity(android.content.Intent(this,PortableAiActivity::class.java).putExtra("request_from_analysis",true))}})
        body.addView(ui.action("전송 요약 미리보기 · 전송 없음","analysis_ai",ActionStyle.QUIET,canEdit()&&a.points.isNotEmpty(),HmiIcon.RECORD){edit{aiPreview=AnalysisExplanation.payload(a);aiConsent=AnalysisConsent(aiPreview);openScreen(Screen.AI_PREVIEW)}})
        body.addView(ui.expandable("상세 데이터 품질과 분석 근거","analysis_detail",analysisDetail,canEdit()){edit{analysisDetail=!analysisDetail;draw()}})
        if(analysisDetail){
            body.addView(label("단순 평균의 모집단: 선택 기간·동일 출처의 유효 수신 ${a.points.size}건(반복 수신 포함). 선은 같은 세션에서만 이어요. 10분 이상 공백은 분석상 구분 기준이며 차량 규격이 아니에요. 세션은 운행·차량 식별자가 아니에요.",22))
            a.quality.forEach{(k,n)->body.addView(label("$k · ${n}건",22))}
            a.insights.take(8).forEach{i->body.addView(label("${i.kind} · ${i.rule}\n${vehicleDisplay.time(i.first)} → ${vehicleDisplay.time(i.last)}\n근거 ${i.evidence.joinToString{it.storeId+":"+it.rowId}}",20))}
            body.addView(label("그래프의 수신값 목록 · 최근 순서",24,true))
            a.points.asReversed().drop(analysisPage*20).take(20).forEach{p->body.addView(label("${vehicleDisplay.time(p.at)} · ${VehicleAnalysis.fmt(p.number)}${a.unit}\n원본 ${p.ref.storeId}:${p.ref.rowId} · ${if(p.ref.value.detail.contains("CALLBACK"))"CALLBACK"else"GET/기존 읽기"} · 세션 ${p.ref.value.session}",20))}
            if(a.points.size>(analysisPage+1)*20)body.addView(button("다음 수신값 20개","analysis_more",enabled=canEdit()){analysisPage++;draw()})
            if(analysisPage>0)body.addView(button("가장 최근 수신값","analysis_reset_page",enabled=canEdit()){analysisPage=0;draw()})
        }
    }
    private fun aiPreviewScreen(body:LinearLayout){
        body.addView(ui.state("로컬 전송 요약 미리보기예요.","이 화면에서는 외부로 전송하지 않아요. 계정 로그인·기기 등록을 사용하는 AI 설명은 분석 화면의 별도 버튼에서 확인해 주세요."))
        body.addView(label("전송 후보: 선택한 신호의 수치·시각·변화량·통계·품질·분석 한계예요. 이름·차량 식별자·위치·전체 DB·증상 원문은 포함하지 않아요.",22))
        body.addView(label(JSONObjectPretty(aiPreview),20).apply{tag="ai_payload_preview"})
        body.addView(button("이 내용만 전송에 동의","ai_consent",enabled=canEdit()){aiConsent?.accept();notice="동의 범위만 확인했어요. AI 미연결로 전송하지 않았어요.";draw()})
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
        val card=panel().apply{tag=testTag}
        card.addView(label("함께 저장한 차량 정보",24,true))
        card.addView(label(VehicleUi.compact(s),24))
        card.addView(label("조회 ${vehicleDisplay.time(s.at)} · ${VehicleUi.source(s)}",22))
        card.addView(ui.action("차량 정보 자세히","linked_snapshot_detail",ActionStyle.QUIET,canEdit(),HmiIcon.CHEVRON){edit{openSnapshot(s)}})
        body.addView(card)
    }
    private fun loadDetails(body:LinearLayout){
        body.addView(label("Vehicle SDK 2.3.2 · ${pleos.message}",22))
        body.addView(label("기기 시간대: ${ZoneId.systemDefault().id} · 차량 측 측정시각: 제공되지 않음",20))
        observations.refs().takeLast(100).asReversed().forEach{ref->val item=ref.value
            val card=panel();card.addView(label("${item.kind} · ${item.status}",24,true))
            card.addView(label("관측 데이터 ID: ${ref.storeId}:${ref.rowId}\n원본: ${item.raw?:"미수신"} · 단위: ${item.unit?:"없음"}\n지원: ${item.supported} · 유효: ${item.valid}\n출처: ${item.source}\n수신 시각: ${item.receivedAt?:"없음"}\n기록 시각: ${item.recordedAt}\n세션 ID: ${item.session}\n${item.detail}",20));body.addView(card)
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
        if(!canEdit()||!captureDraft())return
        rememberLocation()
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
            listOf("내가 남긴 기록을 다른 사람이 알아볼 수 있게 이름을 정해요.","기록 이름 변경 → 이름 추가 또는 카드 선택 → 운전 시작을 눌러 주세요.","이 이름으로 기록을 남겨요. 차량 시동·계정·시트 설정은 바뀌지 않아요. 이름 선택은 잠금이나 본인 인증 기능이 아니에요."),
            listOf("지원되는 충전량과 주행거리를 불러볼 수 있어요.","차량 정보 → 차량값 불러오기를 눌러 주세요. 받은 시각과 출처를 함께 확인해 주세요.","자동으로 계속 수집하거나 고장 여부를 판단하지는 않아요. 자세한 연결 기록은 불러오기 상세에 있어요."),
            listOf("차에서 느낀 소리나 떨림을 남겨 보세요. 고장 이름을 몰라도 괜찮아요.","문제 남기기 → 증상과 상황 입력 → 이 차량 앱에 저장을 눌러 주세요.","작성 중인 내용은 원래 이름으로 이어 쓸 수 있어요. 이름을 바꾸기 전에는 저장하거나 작성을 취소해 주세요."),
            listOf("같은 앱을 쓰는 다른 사람이 남긴 내용을 살펴봐요.","기록 보기 → 내용을 열고 읽었다면 내용을 읽었어요를 눌러 주세요.","읽음은 수리가 끝났다는 뜻이 아니에요. 이름을 바꾸거나 상세를 열기만 해서는 읽음으로 저장하지 않아요."),
            listOf("어떤 상황과 점검 기록이 있었는지 정비 상담 때 보여줄 수 있어요.","기록 → 내용을 열고 이 문제의 정비 상담용 요약을 눌러 주세요. 전체 요약은 기록 목록에 있어요.","이 화면은 고장 진단이 아니며 외부 정비소로 자동 전송되지 않아요."),
            listOf("지금은 차량의 이상을 자동으로 찾아주지는 않아요.","앱이 보이고 입력 허용 상태가 확인되면 차량값을 한 번 기록해요. 문제는 직접 남기며 차량 정보를 선택해 연결할 수 있어요.","앱 종료·절전·연결 단절 중에는 기록 공백이 있어요. 저장된 기록이 없다고 차량이 정상이라는 뜻은 아니에요.")
        )

        lines[helpTopic].forEach{body.addView(label(it,24))}
        body.addView(button("사용법 목록","help_contents",enabled=canEdit()){onBackPressed()})
    }
    private fun recordForm(body:LinearLayout){
        speechFormVisible=true
        val active=journal?:run{
            body.addView(ui.state("작성 중인 내용이 없어요.","새 증상을 남기거나 저장된 기록을 확인해 주세요."))
            body.addView(button("전체 기록 보기","recover_record_route",enabled=canEdit()){openRecords(RecordFilter.ALL)});return
        }
        val editing=active.kind=="EDIT"
        body.addView(label("${active.ownerName}님이 직접 남기는 기록이에요.",20))
        val types=SymptomType.entries.map{type->ui.action((if(draft.type==type)"✓ "else"")+type.label,"type_${type.name}",ActionStyle.SECONDARY,canWrite()){
            edit{if(captureDraft()){draft.type=type;captureDraft();draw()}}
        }.apply{isSelected=draft.type==type}}
        val available=if(root.width>0)root.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat()
        body.addView(ui.grid(types,if(available>=1000&&resources.configuration.fontScale<1.5f)4 else if(available>=480&&resources.configuration.fontScale<1.5f)2 else 1).apply{tag="symptom_type"})
        body.addView(label("어떤 상황이었나요?",24,true))
        situationInput=input("예: 가속할 때 핸들이 떨려요.",1000).apply{tag="situation";setText(draft.situation)};body.addView(situationInput)
        situationInput!!.addTextChangedListener(watcher{captureDraft()})
        body.addView(SpeechControls(this,ui,speechInput,{canWrite()},{voiceDraft()}))
        val speechObserver=speechInput.observer
        speechInput.observer={state->speechObserver(state);listOf("save_issue","save_issue_ai").forEach{tag->root.findViewWithTag<Button>(tag)?.isEnabled=canWrite()&&!recordSaving&&!state.busy}}
        body.addView(button(if(editing)"수정 내용 저장"else"이 차량 앱에 저장","save_issue",primary=true,enabled=canWrite()&&!recordSaving&&!speechInput.busy){saveRecord()})
        body.addView(ui.action(if(editing)"수정 저장하고 AI 설명"else"저장하고 AI 설명","save_issue_ai",ActionStyle.SECONDARY,canWrite()&&!recordSaving&&!speechInput.busy,HmiIcon.RECORD){
            if(canWrite()&&!speechInput.busy)saveRecord(openAi=true)
        })
        body.addView(label("AI 설명을 선택해도 먼저 이 기록을 저장해요. 다음 화면에서 전송할 증상을 확인하고 요청해 주세요.",20))
        body.addView(ui.expandable("차량 정보·메모·발견 시각 (선택)","record_extras",recordExtras,canEdit()){
            edit{if(captureDraft()){recordExtras=!recordExtras;draw()}}
        })
        if(recordExtras){
            val extras=panel()
            if(!editing){
                snapshots.latest()?.let{availableSnapshot->
                    val selected=snapshots.selected(active.id)==availableSnapshot.id
                    extras.addView(ui.action((if(selected)"✓ "else"")+"차량 정보 함께 저장","select_snapshot",ActionStyle.SECONDARY,canWrite(),HmiIcon.VEHICLE){
                        edit{snapshots.select(active.id,if(selected)null else availableSnapshot.id);draw()}
                    })
                    extras.addView(label("조회 ${VehicleUi.shortTime(availableSnapshot.at)} · ${VehicleUi.compact(availableSnapshot)}",20))
                    extras.addView(label("조회 시점의 값이며 증상 당시 값이나 원인은 아니에요.",20))
                }
                extras.addView(button("발견 시각 · ${ConsultationSummary.time(draft.foundAt)}","discovered_at",enabled=canEdit()){pickTime()})
            }else extras.addView(label("처음 발견 · ${ConsultationSummary.time(draft.foundAt)}\n처음 발견한 시각은 유지해요.",20))
            extras.addView(label("추가 메모",22,true))
            memoInput=input("추가로 남길 내용을 적어 주세요.",2000).apply{tag="memo";setText(draft.memo)};extras.addView(memoInput)
            memoInput!!.addTextChangedListener(watcher{captureDraft()})
            extras.addView(label("기기 시간대: ${ZoneId.systemDefault().id}",20));body.addView(extras)
        }
        intent.getStringExtra("voice_draft")?.takeIf{it.length<=800&&it.isNotBlank()}?.let{candidate->
            body.addView(button("음성 명령의 내용 확인","adopt_voice_action",enabled=canWrite()){
                if(draft.situation.isNotBlank())toast("작성 중인 내용을 자동으로 덮어쓰지 않아요. 먼저 확인해 주세요.")
                else if(canWrite()){
                    situationInput?.setText(CaritdaActions.draft(candidate));captureDraft();intent.removeExtra("voice_draft")
                    notice="입력 내용을 확인한 뒤 저장해 주세요.";draw()
                }
            })
        }
        cancelDraftButton(body)
    }
    private fun openIssueAi(id:String,afterSave:Boolean=false){
        if(!canEdit())return
        val request=android.content.Intent(this,PortableAiActivity::class.java).putExtra("issue_id",id).putExtra("issue_after_save",afterSave)
        // Same unique validation family across both Activities. Ordinary extras are ignored.
        fixturePrefix?.let{request.putExtra("validation_fixture",it)}
        startActivity(request)
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
        val content=panel()
        content.addView(ui.status("${issue.type.label} · ${statusText(issue.status)}"))
        content.addView(label(issue.situation,26,true))
        content.addView(label("${issue.reporter.label} · 발견 ${ConsultationSummary.time(issue.discoveredAt)}",20))
        if(issue.memo.isNotBlank())content.addView(label("메모 · ${issue.memo}",22))
        if(service.events(id).any{it.kind==EventKind.EDITED})content.addView(label("내용이 수정됐어요. 이전 내용은 아래 이력에 남아 있어요.",20))
        body.addView(content)
        body.addView(ui.action("AI로 쉽게 설명","issue_ai_explain",ActionStyle.SECONDARY,canEdit(),HmiIcon.RECORD){
            openIssueAi(id)
        })
        body.addView(ui.action("이 기록 음성으로 듣기","speak_issue",ActionStyle.QUIET,canEdit()){readAloud(issue.type.label+". "+issue.situation+". 현재 상태는 "+statusText(issue.status)+"입니다.")})
        linkedLocations(body,id)
        if(service.canManage(issue))body.addView(ui.action("현재 위치 함께 저장 (선택)","attach_location",ActionStyle.QUIET,canWrite()&&!locationReader.busy){attachLocation(id)})
        if(locationReader.busy)body.addView(ui.action("이번 위치 확인 취소","cancel_location",ActionStyle.QUIET,canWrite()){locationReader.cancel();draw()})
        snapshots.linked(id)?.let{linkedVehicle(body,it)}
        if(service.canManage(issue)&&snapshots.linked(id)==null){
            body.addView(ui.action(if(snapshots.pending(id))"차량 정보 연결 다시 시도"else"차량 정보 함께 저장","link_snapshot",ActionStyle.QUIET,canWrite(),HmiIcon.VEHICLE){
                confirm("차량 정보를 함께 저장할까요?","최근 조회값이며 증상 발생 당시 값은 아니에요. 원문과 상태는 바뀌지 않아요."){
                    if(!service.canManage(service.issue(id)))throw EditingBlockedException()
                    if(!snapshots.pending(id))snapshots.prepareLink(id,snapshots.latest()?.id?:error("먼저 차량값을 불러와 주세요."))
                    snapshots.completeLink(id);notice="차량 정보를 연결했습니다. 증상 원문은 그대로예요.";draw()
                }
            })
        }
        if(service.canManage(issue)){
            val editButton=ui.action("내 기록 수정","edit_issue",ActionStyle.QUIET,canWrite(),HmiIcon.RECORD){edit{beginEdit(issue)}}
            val remove=ui.action("내 기록 삭제","delete_issue",ActionStyle.DANGER,canWrite()){
                confirm("이 기록을 삭제할까요?","${issue.type.label} · ${issue.situation.take(80)}\n목록과 정비 상담에서 제외해요. 원본과 이력은 기기에 남으며, 이 화면에서는 복구하거나 영구 삭제할 수 없어요.","삭제"){
                    service.deleteOwned(id);selectedIssue=null;history.clear();recordFilter=RecordFilter.ALL;screen=Screen.ISSUES;positions[screen]=0;notice="삭제되었습니다. 다른 기록은 그대로 유지돼요.";draw()
                }
            }
            body.addView(ui.grid(listOf(editButton,remove),columns()))
        }else body.addView(label("작성한 내용의 수정·삭제는 원래 작성자만 할 수 있어요.",20))
        if(!service.confirmed())body.addView(label("기록할 사람을 선택하면 점검·읽음 확인을 남길 수 있어요.",20))
        else if(service.driver().id!=issue.reporter.id){
            if(service.needsAcknowledgement(issue))body.addView(button("내용을 읽었어요","acknowledge",primary=true,enabled=canWrite()){service.acknowledge(id);notice="읽음 확인을 저장했어요.";draw()})
            else body.addView(label("현재 운전자의 인계 확인이 저장되었습니다.",20).apply{tag="acknowledged"})
        }
        val note=ui.action("점검·조치 메모","add_note",ActionStyle.SECONDARY,canWrite(),HmiIcon.PLUS){edit{beginAction("NOTE",issue)}}
        val status=ui.action("상태 변경","change_status",ActionStyle.PRIMARY,canWrite(),HmiIcon.CHECK){edit{statusDialog(issue)}}
        body.addView(ui.grid(listOf(note,status),columns()))
        val recurrence=ui.action("다시 발생했어요","recurrence",ActionStyle.QUIET,canWrite(),HmiIcon.RECORD){edit{beginAction("RECURRENCE",issue)}}
        val summary=ui.action("정비 상담용 기록","issue_summary",ActionStyle.QUIET,canEdit(),HmiIcon.RECORD){edit{openScreen(Screen.SUMMARY)}}
        body.addView(ui.grid(listOf(recurrence,summary),columns()))
        draftBanner(body)
        body.addView(label("확인·점검 이력",24,true))
        body.addView(label("다시 발생 ${service.events(id).count{it.kind==EventKind.RECURRENCE}}회 · 상태는 직접 확인해 남긴 기록이에요.",20))
        service.events(id).asReversed().forEach{event->
            val row=panel();row.addView(label("${event.kind.label} · ${event.actor.label}",22,true))
            row.addView(label("${ConsultationSummary.time(event.at)}\n${event.text}",20));body.addView(row)
        }
        if(service.issues(recordFilter,recordQuery).none{it.id==id})body.addView(button("전체 기록에서 보기","show_in_all",enabled=canEdit()){openRecords(RecordFilter.ALL)})
    }
    private fun summary(body: LinearLayout) {

        body.addView(label("시간대: ${ZoneId.systemDefault().id}\n외부 정비소에는 전송되지 않았습니다.", 18))
        val issues = selectedIssue?.let { listOf(service.issue(it)) } ?: service.issues()
        body.addView(label("범위: ${if(selectedIssue==null) "전체 기록" else "선택한 문제"} ${issues.size}건 · 사용자 직접 기록",24,true))
        if(issues.isNotEmpty())body.addView(ui.action("정비 장소 길찾기 (좌표 입력)","inspection_navigation",ActionStyle.QUIET,canEdit()&&!navigation.busy){destination()})
        if(navigationEvents.isNotEmpty())body.addView(ui.state("길찾기 수신 이력 · 현재 경로는 내비게이션에서 확인해 주세요",navigationEvents.joinToString("\n\n"){event->vehicleDisplay.time(event.at)+"\n"+InspectionNavigation.message(event.code)}).apply{tag="navigation_progress"})
        if(navigation.busy)body.addView(ui.action("이번 요청 결과 확인 멈추기","cancel_navigation_monitor",ActionStyle.QUIET,canEdit()){navigation.cancel();navigationEvents=navigation.events;draw()})
        issues.forEach { issue ->
            val card = panel()
            card.addView(label(ConsultationSummary.build(issue, service.events(issue.id),::statusText), 24).apply { tag = "consultation_summary" })
            snapshots.linked(issue.id)?.let{linkedVehicle(card,it)}
            card.addView(ui.action("이 상담 기록 듣기","speak_consultation_"+issue.id,ActionStyle.QUIET,canEdit()){readAloud(ConsultationSummary.build(issue,service.events(issue.id),::statusText))})
            val question="‘${issue.situation.take(300)}’ 증상에 어떤 점검이 필요할까요?"
            card.addView(label("정비사에게 물어볼 질문\n$question",22).apply{tag="consultation_question"})
            card.addView(ui.action("질문 복사","copy_question_${issue.id}",ActionStyle.SECONDARY,canEdit(),HmiIcon.RECORD){edit{
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("정비 상담 질문",question));notice="질문을 복사했어요. 외부로 전송하지 않았어요.";draw()
            }})
            body.addView(card)
        }
    }

    private fun statusDialog(issue: VehicleIssue) {
        if(!canWrite())return
        val content=column().apply{setPadding(dp(24),dp(16),dp(24),dp(16))}
        content.addView(label("점검 완료는 점검한 기록이에요. 해결 확인은 문제 해결을 직접 확인한 기록이에요.",22))
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
    private fun label(value:String,size:Int,bold:Boolean=false)=ui.text(value,size.coerceAtLeast(CaritdaTokens.SUPPORT),bold,size<22)
    private fun button(value:String,testTag:String,primary:Boolean=false,enabled:Boolean=true,action:()->Unit)=
        ui.action(value,testTag,if(primary)ActionStyle.PRIMARY else if(testTag=="delete_issue")ActionStyle.DANGER else ActionStyle.SECONDARY,enabled){edit{action()}}
    private fun input(hintText:String,max:Int)=EditText(this).apply{
        hint=hintText;textSize=24f;minLines=3;maxLines=8;minHeight=dp(76)
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        filters=arrayOf(InputFilter.LengthFilter(max));setTextColor(ink);setHintTextColor(muted)
        setPadding(dp(20),dp(16),dp(20),dp(16));background=shape(cardColor,muted)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,dp(12))}
    }
    private fun shape(color:Int,stroke:Int?=null)=ui.shape(color,CaritdaTokens.BUTTON_RADIUS,stroke)
    private fun toast(message:String){notice=message;feedback?.apply{text=message;visibility=View.VISIBLE};Toast.makeText(this,message,Toast.LENGTH_LONG).show()}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
