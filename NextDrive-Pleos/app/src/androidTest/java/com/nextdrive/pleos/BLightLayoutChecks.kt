package com.nextdrive.pleos

import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.view.*
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.PortableSession
import com.nextdrive.pleos.analytics.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.safety.EditingState
import com.nextdrive.pleos.ui.*
import com.nextdrive.pleos.vehicle.*
import org.json.*
import java.io.File
import java.util.UUID

/** Resizes only this unique fixture's real Activity window. Never changes wm/OS settings.
 * Render scenes are synthetic and distinct from BLightStageTwoChecks' real action paths. */
class BLightLayoutChecks(private val inst:Instrumentation){
    private val h=BLightHarness(inst);private val c=inst.targetContext
    private val prefix="gleo-ui-"+UUID.randomUUID();private val rows=JSONArray()
    private val lines=mutableListOf<String>()
    private val synthetic="합성 UI 예시 · 실제 차량이나 모델 결과가 아니에요."
    private lateinit var a:MainActivity;private lateinit var first:VehicleIssue
    private lateinit var driver:Driver;private lateinit var other:Driver
    private lateinit var raw:ObservationStore;private lateinit var snapshots:SnapshotStore
    private lateinit var analysis:Map<String,VehicleAnalysis>
    private val scenes=listOf("HOME","VEHICLE","RECORD","ISSUES","DETAIL","HANDOVER","MENU","HELP","DRIVERS","DRIVER_MANAGE","DRIVER_EDIT","SUMMARY","LOAD_DETAIL","ACTION","WELCOME","HELP_TOPIC","SNAPSHOTS","SNAPSHOT_DETAIL","HISTORY","VEHICLE_SETTINGS","SOC_NOTICES","ANALYTICS","AI_PREVIEW")
    private fun checkIt(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    fun run(smoke:Boolean=false):List<String>{
        ValidationEnvironment.requireIsolated(c)
        raw=ObservationStore(c,prefix+"-observations.db");snapshots=SnapshotStore(c,prefix+"-snapshots.db")
        val now=System.currentTimeMillis()
        SqliteIssueRepository(c,prefix+".db").use{repo->
            val service=CaritdaService(repo,{true});driver=service.registerDriver("예시운전자A");other=service.registerDriver("예시운전자B")
            service.selectDriver(driver);first=service.record(SymptomType.NOISE,"브레이크를 밟으면 끼익 소리가 나요.",now-3600000,"정차 후 직접 확인할 예시 메모예요.")
            service.selectDriver(other);service.record(SymptomType.WARNING,"차량 화면의 안내를 다음 사람이 확인해 주세요.",now-1800000,"");service.selectDriver(driver)
        }
        fun sample(kind:String,v:String,u:String,t:Long,session:String)=VehicleObservation(kind,v,u,t,ObservationSource.PLEOS_EMULATOR_SDK,true,true,session,ObservationStatus.RECEIVED,"SYNTHETIC_B_LAYOUT_FIXTURE",t)
        listOf(80,60,18).forEachIndexed{i,soc->val t=now-60000+i*20000L
            raw.appendRef(sample("EV_SOC",soc.toString(),"%",t,"SYNTHETIC_BATCH_$i"));raw.appendRef(sample("ODOMETER",(24810+i*3).toString(),"km",t,"SYNTHETIC_BATCH_$i"))
            raw.appendRef(sample("FUEL_PERCENT","0","%",t,"SYNTHETIC_BATCH_$i").copy(raw=null,unit=null,valid=false,supported=false,receivedAt=null,status=ObservationStatus.UNSUPPORTED))
        }
        raw.appendRef(sample("DOOR_FL","3","position",now,"SYNTHETIC_GROUP"));raw.appendRef(sample("LOW_BEAM","ON","state",now,"SYNTHETIC_GROUP"))
        raw.appendRef(sample("WASHER_LOW","true","state",now,"SYNTHETIC_GROUP"))
        snapshots.reconcile(raw);val snap=snapshots.latest()?:error("FIXTURE_SNAPSHOT")
        snapshots.prepareLink(first.id,snap.id);snapshots.completeLink(first.id)
        IssueLocationStore(c,prefix+"-locations.db").use{it.save(first.id,IssueLocation(37.0,127.0,now-900000,now,"SYNTHETIC_LAYOUT_FIXTURE"))}
        analysis=listOf("EV_SOC","ODOMETER").associateWith{VehicleAnalytics.analyze(it,raw.refs(),now-86400000,now)}
        a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        val config=Configuration(a.resources.configuration)
        try{
            h.click(a,"driver_${driver.id}");h.click(a,"start_driving");h.await{h.get(a,"displayedSafety")==EditingState.ALLOWED}
            h.main{
                (h.get(a,"socObserver") as PleosSocObserver).stop("합성 배치 전용")
                h.set(a,"socObserver",PleosSocObserver(a,raw,{false},{},{ }))
                h.set(a,"analysisResults",analysis);h.set(a,"analysisBusy",false);h.set(a,"analysisDirty",false)
                h.set(a,"selectedIssue",first.id);h.set(a,"selectedSnapshot",snap.id);h.set(a,"selectedCard",snap.cardId)
                h.set(a,"guideReplay",true);h.set(a,"pendingDriver",driver.id)
                val handler=h.get(a,"freshnessHandler") as android.os.Handler;handler.removeCallbacksAndMessages(null)
            }
            val widths=if(smoke)listOf(320,1024)else listOf(320,600,840,1024)
            val fonts=if(smoke)listOf(1f,2f)else listOf(1f,1.3f,1.5f,2f)
            for(dark in listOf(false,true))for(width in widths)for(portrait in listOf(false,true))for(font in fonts){
                val height=if(portrait)maxOf(width+96,720).coerceAtMost(1160)else minOf((width*.7).toInt(),720).coerceAtLeast(240)
                configure(a,width,height,font,dark)
                for(scene in scenes){
                    show(scene)
                    try{inspect(a,scene,width,height,font,dark)}catch(t:Throwable){h.capture(a,"failed-${scene.lowercase().replace('_','-')}.png");throw t}
                    if(!smoke&&width==1024&&!portrait&&font==1f&&(!dark||scene in setOf("HOME","VEHICLE")))h.capture(a,scene.lowercase().replace('_','-')+if(dark)"-dark.png"else"-light.png")
                    if(!smoke&&width==320&&portrait&&font==2f&&!dark&&scene=="RECORD")h.capture(a,"record-320-large.png")
                }
            }
            // Rendering never presses Save/Acknowledge/Status or changes the confirmed journal.
            SqliteIssueRepository(c,prefix+".db").use{checkIt(it.issues().size==2&&it.events(first.id).size==1,"BL01 all Main scenes retain the confirmed issue/event originals")}
            h.main{clearDraftFields();h.set(a,"journal",null);h.set(a,"lastDraft",null)}
            ai(smoke)
            checkIt(rows.length()==if(smoke)400 else 1600,"BL02 every requested width/orientation/font/theme/scene was actually measured")
            File(c.filesDir,"b-light-layout-results.json").writeText(JSONObject().put("fixture",prefix).put("synthetic",true).put("cases",rows).toString())
            checkIt(true,"BL03 each native case passed measured bounds, text lines, 76dp targets and scroll reachability; no OS size/font setting changed")
        }finally{
            h.main{restore(a,config);a.finish()};inst.waitForIdleSync();raw.close();snapshots.close()
        }
        return lines
    }
    @Suppress("DEPRECATION")
    private fun configure(activity:Activity,w:Int,height:Int,font:Float,dark:Boolean){
        h.main{
            CaritdaTheme.preferences(activity,prefix).edit().putString("theme",if(dark)"dark"else"light").commit()
            val next=Configuration(activity.resources.configuration).apply{fontScale=font;screenWidthDp=w;screenHeightDp=height;orientation=if(height>w)Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE}
            activity.resources.updateConfiguration(next,null)
            val density=activity.resources.displayMetrics.density
            activity.window.setLayout((w*density).toInt(),(height*density).toInt());activity.window.setGravity(Gravity.CENTER)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            activity.currentFocus?.clearFocus()
            (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
        }
        inst.waitForIdleSync();h.await{activity.window.decorView.width>0}
        // WindowManager delivers the new dimensions asynchronously; require the requested width.
        h.await{kotlin.math.abs(activity.window.decorView.width-w*activity.resources.displayMetrics.density)<=2}
    }
    @Suppress("DEPRECATION") private fun restore(activity:Activity,config:Configuration){activity.resources.updateConfiguration(config,null);activity.window.setLayout(-1,-1)}
    private fun clearDraftFields(){for(name in listOf("situationInput","memoInput","actionField","driverNameInput","socThresholdInput","searchInput"))h.set(a,name,null)}
    @Suppress("UNCHECKED_CAST")
    private fun show(scene:String){
        h.main{
            clearDraftFields();h.set(a,"journal",null);h.set(a,"lastDraft",null);h.set(a,"notice","")
            (h.get(a,"positions") as MutableMap<Any,Int>).clear();(h.get(a,"expandedSections") as MutableSet<String>).clear()
            h.set(a,"recordExtras",false);h.set(a,"showHealth",false);h.set(a,"technicalExpanded",false)
            h.set(a,"selectedIssue",first.id);h.set(a,"editingDriver",driver.id);h.set(a,"driverNameDraft",driver.label)
            if(scene in setOf("RECORD","ACTION")){
                val j=JournalDraft("layout-memory-only",driver.id,driver.label,if(scene=="RECORD")"RECORD"else"NOTE",if(scene=="ACTION")first.id else null,body=if(scene=="RECORD")"브레이크를 밟으면 소리가 나요."else"정차 후 확인한 예시 메모예요.",foundAt=first.discoveredAt)
                h.set(a,"journal",j);h.set(a,"lastDraft",j)
                val type=Class.forName("com.nextdrive.pleos.MainActivity\$Draft");val constructor=type.declaredConstructors.first{it.parameterCount==4}.apply{isAccessible=true}
                h.set(a,"draft",constructor.newInstance(j.type,j.body,j.foundAt,j.memo))
            }
            if(scene=="AI_PREVIEW"){val payload=AnalysisExplanation.payload(analysis.getValue("EV_SOC"));h.set(a,"aiPreview",payload);h.set(a,"aiConsent",AnalysisConsent(payload))}
            val screen=h.get(a,"screen")!!.javaClass.enumConstants.first{(it as Enum<*>).name==scene};h.set(a,"screen",screen);h.draw(a)
        }
        inst.waitForIdleSync()
    }
    private fun inspect(activity:Activity,scene:String,w:Int,height:Int,font:Float,dark:Boolean){
        h.main{
            val root=h.get(activity,if(activity is MainActivity)"root"else"shell") as View
            val scale=activity.resources.displayMetrics.density;val epsilon=3f
            check(root.width>0&&root.height>0){"EMPTY_ROOT $scene $w/$font"}
            check(kotlin.math.abs(root.width-w*scale)<=epsilon){"WINDOW_CLAMPED $scene requested=$w actual=${root.width/scale}"}
            val all=h.views(root).filter{it.visibility==View.VISIBLE&&it.width>0&&it.height>0}
            check(h.text(root).contains("기록을 불러오지 못했습니다.").not()){ "SCENE_RENDER_ERROR $scene" }
            val buttons=all.filterIsInstance<Button>();check(buttons.isNotEmpty()){ "NO_ACTION $scene" }
            for(v in all){
                val p=v.parent as? View?:continue
                check(v.left>=-epsilon&&v.right<=p.width+epsilon){"HORIZONTAL_OVERFLOW $scene/$w/$font tag=${v.tag} ${v.left}..${v.right} parent=${p.width}"}
                if(v is Button)check(v.width+epsilon>=76*scale&&v.height+epsilon>=76*scale){"SMALL_TARGET $scene/${v.tag} ${v.width}x${v.height}"}
                if(v is TextView&&v.text.isNotEmpty()){
                    val l=v.layout?:continue
                    val usable=v.width-v.compoundPaddingLeft-v.compoundPaddingRight
                    for(i in 0 until l.lineCount){
                        check(l.getEllipsisCount(i)==0){"ELLIPSIS $scene/${v.tag}"}
                        // Layout.getLineWidth includes invisible trailing whitespace after a wrap.
                        check(l.getLineMax(i)<=usable+epsilon){"TEXT_CLIPPED $scene/$w/$font tag=${v.tag} ink=${l.getLineMax(i)} usable=$usable"}
                    }
                    check(l.height<=v.height-v.compoundPaddingTop-v.compoundPaddingBottom+epsilon){"VERTICAL_TEXT_CLIPPED $scene/${v.tag}"}
                }
            }
            val scroll=all.filterIsInstance<ScrollView>().firstOrNull()?:error("NO_SCROLL $scene")
            check(scroll.height>=40*scale){"NO_SCROLL_VIEWPORT $scene $w/$height/$font height=${scroll.height}"}
            // Use native parent scrolling for every button; do not click any action.
            for(button in buttons){button.requestRectangleOnScreen(Rect(0,0,button.width,minOf(button.height,(76*scale).toInt())),true)
                val r=Rect();check(button.getGlobalVisibleRect(r)&&r.width()>=minOf(button.width,(60*scale).toInt())&&r.height()>=minOf(button.height,(40*scale).toInt())){"UNREACHABLE_ACTION $scene/$w/$font ${button.tag}"}}
            scroll.scrollTo(0,0)
            rows.put(JSONObject().put("scene",scene).put("requested_width_dp",w).put("requested_height_dp",height).put("actual_width_dp",root.width/scale).put("actual_height_dp",root.height/scale).put("font",font).put("dark",dark).put("buttons",buttons.size).put("views",all.size))
        }
    }
    private fun ai(smoke:Boolean){
        for(issueMode in listOf(true,false)){
            val request=Intent(c,PortableAiActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            if(issueMode)request.putExtra("issue_id",first.id)
            val screen=inst.startActivitySync(request) as PortableAiActivity;val config=Configuration(screen.resources.configuration)
            val fixture=object:PortableSession{
                override val signedIn=true;override val subject="b-light-layout-"+UUID.randomUUID()
                override fun matches(uid:String)=uid==subject
                override fun acquire(done:(String?,String?,String?)->Unit){error("LAYOUT_MUST_NOT_ACQUIRE_OR_CALL_MODEL")}
                override fun client(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?):PortableAiClient=error("LAYOUT_MUST_NOT_BUILD_CLIENT")
            }
            try{
                h.await{(h.get(screen,"gate") as com.nextdrive.pleos.safety.CarSafetyGate).state==EditingState.ALLOWED}
                if(issueMode)h.await{h.get(screen,"issue")!=null}else h.await{h.get(screen,"analysis")!=null}
                PortableAiStore(c,if(issueMode)"portable-issue-v1-${fixture.subject}"else"portable-ai-v2-${fixture.subject}").write(JSONObject().put("consent",if(issueMode)IssueExplanation.CONSENT else PortableAnalysis.CONSENT))
                h.main{h.set(screen,"session\$delegate",lazy{fixture as PortableSession});h.set(screen,"loadedSubject",fixture.subject);h.set(screen,"analysis",analysis["EV_SOC"]);h.set(screen,"message",synthetic);h.set(screen,"resultText","가능한 원인\n여러 부위에서 소음이 전달될 수 있어요.\n\n확인할 일\n정차 후 언제 소리가 나는지 직접 확인해 주세요.\n\n점검 참고\n반복되면 정비소에 상담해 주세요. 확정 진단은 아니에요.");h.set(screen,"resultOrigin","합성 배치 검증 · 실제 AI 응답 아님")}
                val widths=if(smoke)listOf(320,1024)else listOf(320,600,840,1024);val fonts=if(smoke)listOf(1f,2f)else listOf(1f,1.3f,1.5f,2f)
                for(dark in listOf(false,true))for(width in widths)for(portrait in listOf(false,true))for(font in fonts){
                    val height=if(portrait)maxOf(width+96,720).coerceAtMost(1160)else minOf((width*.7).toInt(),720).coerceAtLeast(240)
                    configure(screen,width,height,font,dark)
                    h.main{h.set(screen,"ui",CaritdaComponents(screen,CaritdaPalette.of(dark)));h.render(screen)};inst.waitForIdleSync()
                    inspect(screen,if(issueMode)"AI_ISSUE"else"AI_SOC",width,height,font,dark)
                    if(!smoke&&width==1024&&!portrait&&font==1f&&!dark)h.capture(screen,if(issueMode)"ai-light.png"else"ai-soc-light.png")
                }
            }finally{h.main{restore(screen,config);screen.finish()};inst.waitForIdleSync()}
        }
    }
}
