package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.ui.*
import com.nextdrive.pleos.vehicle.*
import java.util.UUID

/** B layout contract. Synthetic signal fixtures are never normal-app or actual-sensor evidence. */
class BLightStageOneChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T{var value:T?=null;var failure:Throwable?=null;inst.runOnMainSync{try{value=block()}catch(t:Throwable){failure=t}};failure?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    private fun await(block:()->Boolean){val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(main(block))return;Thread.sleep(60)};error("B_LIGHT_VIEW_TIMEOUT")}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun text(v:View)=views(v).filterIsInstance<TextView>().joinToString(" "){it.text.toString()}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val now=System.currentTimeMillis();val policy=SocPolicy(true,20,1,0,"fixture")
        fun sample(kind:String,value:String,unit:String="state",at:Long=now)=VehicleObservation(kind,value,unit,at,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"SYNTHETIC_B_LAYOUT_FIXTURE",ObservationStatus.RECEIVED,"Synthetic UI fixture",at)
        fun checks(values:List<VehicleObservation>,p:SocPolicy=policy)=VehicleAttention.list({kind->values.lastOrNull{it.kind==kind}},{kind->values.lastOrNull{it.kind==kind&&it.valid}},p,now)
        val neutral=listOf(sample("EV_SOC","80","%"),sample("LOW_BEAM","ON"),sample("DOOR_FL","3","position"),sample("TIRE_LIMIT_FL","200","kPa"),sample("BRAKE_PEDAL","true"))
        verify(checks(neutral).isEmpty(),"BL01 normal-range SOC, lamp ON, raw door, tire threshold and pedal never become home faults")
        val low=sample("EV_SOC","18","%")
        verify(checks(listOf(low)).single().origin=="사용자 기준 안내","BL02 below-user-threshold SOC is explicitly a user criterion")
        verify(checks(listOf(low),policy.copy(enabled=false)).isEmpty(),"BL03 disabled criterion never creates a low-SOC home warning")
        val sdk=sample("WASHER_LOW","true")
        verify(checks(listOf(sdk)).single().origin=="차량에서 받은 안내","BL04 a valid current SDK notice is distinguished from the SOC criterion")
        verify(checks(listOf(low,sdk,sdk)).map{it.kind}==listOf("WASHER_LOW","EV_SOC"),"BL05 stable SDK-before-user presentation priority deduplicates a signal without risk ranking")
        verify(checks(listOf(sdk.copy(receivedAt=now-600000))).isEmpty()&&checks(listOf(sdk.copy(receivedAt=now+1))).isEmpty(),"BL06 stale and future timestamps cannot enter current attention")
        for(status in listOf(ObservationStatus.UNSUPPORTED,ObservationStatus.PERMISSION_DENIED,ObservationStatus.TIMEOUT,ObservationStatus.INVALID)){
            verify(checks(listOf(low,low.copy(raw=null,receivedAt=null,valid=false,status=status))).isEmpty(),"BL07_${status.name} latest failure does not promote the previous value to a current warning")
        }
        verify(CaritdaPalette.of(false).background==Color.parseColor("#F4F7F9")&&CaritdaPalette.of(false).accent==Color.parseColor("#12596A")&&CaritdaPalette.of(true).accent==Color.parseColor("#9BE4D5"),"BL08 actual palette matches approved B light/dark tokens")
        val prefix="gleo-ui-"+UUID.randomUUID();val db=prefix+".db"
        val seed=SqliteIssueRepository(c,db).use{repo->val service=CaritdaService(repo,{true});val a=service.registerDriver("샘플A");val b=service.registerDriver("샘플B");service.selectDriver(a)
            Triple(a,b,listOf(service.record(SymptomType.NOISE,"정차 중 소리가 들린 기록 예시",now,""),service.record(SymptomType.OTHER,"직접 확인할 기록 예시",now,"")))}
        val (a,b,issues)=seed
        val activity=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        fun root()=activity.window.decorView
        fun click(tag:String){await{root().findViewWithTag<Button>(tag)?.isEnabled==true};main{check(root().findViewWithTag<Button>(tag).performClick())}}
        fun draw(){main{activity.javaClass.getDeclaredMethod("draw").apply{isAccessible=true}.invoke(activity)}}
        val raw=ObservationStore(c,prefix+"-observations.db");val lowStore=LowSocStore(c,prefix+"-soc.db")
        try{
            click("driver_${a.id}");click("start_driving")
            verify(main{views(root().findViewWithTag("navigation_bar")).filterIsInstance<Button>().map{it.tag}==listOf("settings_help")},"BL09 home header contains only settings/help and a noninteractive brand")
            verify(main{root().findViewWithTag<View>("nav_HOME")==null&&root().findViewWithTag<View>("nav_ISSUES")==null&&root().findViewWithTag<View>("nav_VEHICLE")==null&&root().findViewWithTag<View>("menu")==null},"BL10 removed global tabs have no hidden compatibility Views")
            verify(main{root().findViewWithTag<View>("home_vehicle_attention")==null&&root().findViewWithTag<View>("handover")==null&&views(root()).none{it.tag?.toString()?.startsWith("metric_")==true}},"BL11 zero-attention home omits empty warning cards and normal metric cards")
            verify(!main{CaritdaTheme.dark(activity,prefix)},"BL12 fixture with no stored choice defaults to B light")
            BLightHarness(inst).capture(activity,"home-empty-light.png")
            lowStore.configure(true,20,raw);raw.appendRef(low);draw()
            verify(main{views(root()).count{it.tag=="home_vehicle_attention"}==1&&text(root().findViewWithTag("home_vehicle_attention")).contains("사용자 기준")},"BL13 fresh fixture SOC produces one criterion card with a next action")
            SqliteIssueRepository(c,db).use{verify(it.issues().size==2&&it.draft()==null,"BL14 displaying a vehicle notice creates no issue or draft")}
            raw.appendRef(low.copy(raw=null,unit=null,receivedAt=null,valid=false,status=ObservationStatus.TIMEOUT));draw()
            verify(main{root().findViewWithTag<View>("home_vehicle_attention")==null},"BL15 latest failed receipt removes the current warning without clearing its raw history")
            click("settings_help")
            verify(main{views(root().findViewWithTag("navigation_bar")).filterIsInstance<Button>().map{it.tag}==listOf("back","nav_HOME")&&root().findViewWithTag<View>("my_issues")==null},"BL16 child header has previous/home only; settings is not another work menu")
            click("theme_dark");verify(main{CaritdaTheme.dark(activity,prefix)},"BL17 explicit B dark choice applies without changing another fixture or normal app")
            click("theme_light");click("nav_HOME");click("change_driver");click("driver_${b.id}");click("start_driving")
            click("handover")
            verify(main{text(root().findViewWithTag("handover_count")).contains("2")},"BL18 dedicated handover queue shows this driver's two pending records")
            SqliteIssueRepository(c,db).use{verify(it.events(issues.first().id).size==1,"BL19 entering the queue never marks a record read")}
            click("issue_${issues.first().id}");click("acknowledge");click("back")
            verify(main{text(root().findViewWithTag("handover_count")).contains("1")},"BL20 explicit acknowledgement refreshes the original queue")
            SqliteIssueRepository(c,db).use{verify(it.issue(issues.first().id)?.status==IssueStatus.UNRESOLVED&&it.events(issues.first().id).count{e->e.kind==EventKind.ACKNOWLEDGED}==1,"BL21 read acknowledgement preserves manual status and author")}
            click("nav_HOME");click("new_issue");main{root().findViewWithTag<EditText>("situation").setText("이동해도 유지할 작성 중인 내용")};click("nav_HOME");click("resume_draft")
            verify(main{root().findViewWithTag<EditText>("situation").text.toString()=="이동해도 유지할 작성 중인 내용"},"BL22 new header home/resume preserves the user's draft and edit text")
            SqliteIssueRepository(c,db).use{verify(it.draft()?.ownerId==b.id&&it.issues().size==2,"BL23 navigation preserves draft owner without confirming it as a problem")}
            click("nav_HOME");click("all_issues");main{root().findViewWithTag<EditText>("record_search").setText("소리")};click("apply_search");click("issue_${issues.first().id}");click("back")
            verify(main{root().findViewWithTag<EditText>("record_search").text.toString()=="소리"},"BL24 list-detail-previous keeps search conditions in the new navigation")
            click("nav_HOME");click("vehicle_health")
            verify(main{root().findViewWithTag<View>("soc_notices")!=null&&root().findViewWithTag<View>("vehicle_analytics")!=null},"BL25 SOC guidance and data analytics retain separate visible entry paths")
            click("nav_HOME");click("settings_help");click("manage_drivers");click("back")
            verify(main{root().findViewWithTag<View>("theme_light")!=null&&root().findViewWithTag<View>("settings_help")==null},"BL26 previous returns driver management to settings rather than skipping to home")
        }finally{main{activity.finish()};inst.waitForIdleSync();raw.close();lowStore.close()}
        return lines
    }
}
