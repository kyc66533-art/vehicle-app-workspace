package com.nextdrive.pleos

import android.app.*
import android.content.Intent
import android.view.View
import android.widget.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.ai.IssueExplanation
import org.json.JSONObject
import java.util.UUID

/** Synthetic values and callback fixtures are confined to unique validation database families. */
class DriveCheckP1Checks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T{var value:T?=null;var error:Throwable?=null;inst.runOnMainSync{try{value=block()}catch(t:Throwable){error=t}};error?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    private fun await(block:()->Boolean){val end=System.currentTimeMillis()+18000;while(System.currentTimeMillis()<end){if(main(block))return;Thread.sleep(60)};error("DRIVECHECK_SOC_TIMEOUT")}
    class Soc:SocBackend{
        override val provenance="SYNTHETIC_VALIDATION_ONLY"
        var supported=true;var sendInitial=true;var initialValue:Int?=80;var registered=0;var released=0
        lateinit var value:(Int?)->Unit;lateinit var failure:(Exception)->Unit
        override fun initialize(){}
        override fun capability(done:(Boolean)->Unit,failed:(Exception)->Unit)=done(supported)
        override fun subscribe(value:(Int?)->Unit,failed:(Exception)->Unit){registered++;this.value=value;failure=failed}
        override fun initial(value:(Int?)->Unit,failed:(Exception)->Unit){if(sendInitial)value(initialValue)}
        override fun release(){released++}
    }
    fun core():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val now=System.currentTimeMillis();val policy=SocPolicy(true,20,1,0,"fixture")
        fun point(kind:String,raw:String,unit:String,at:Long=now)=VehicleObservation(kind,raw,unit,at,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"SYNTHETIC_VALIDATION_ONLY",ObservationStatus.RECEIVED,"Synthetic validation value",at)
        fun assess(v:VehicleObservation)=DriveCheck.assess(v.kind,v,v,policy,now)
        verify(assess(point("ODOMETER","0.0","km")).let{it.quality==ReadingQuality.CURRENT&&it.value.contains("0.0")},"DC01 actual-format zero distance is not missing data")
        val unsupported=VehicleObservation("FUEL_PERCENT",null,null,null,ObservationSource.PLEOS_EMULATOR_SDK,false,false,"fixture",ObservationStatus.UNSUPPORTED,"fixture",now)
        verify(DriveCheck.assess("FUEL_PERCENT",unsupported,null,policy,now).let{it.quality==ReadingQuality.UNSUPPORTED&&it.receivedAt==null&&!it.value.contains("0")},"DC02 unsupported fuel remains absent with no invented zero")
        verify(!assess(point("WASHER_LOW","true","state",now-600000)).attention&&assess(point("WASHER_LOW","true","state",now-600000)).quality==ReadingQuality.STALE,"DC03 stale warning cannot create a current attention item")
        val denied=point("POWER_DOWN","true","state").copy(raw=null,unit=null,receivedAt=null,valid=false,status=ObservationStatus.PERMISSION_DENIED)
        verify(DriveCheck.assess("POWER_DOWN",denied,point("POWER_DOWN","true","state"),policy,now).let{it.quality==ReadingQuality.PERMISSION_DENIED&&!it.attention&&it.receivedAt==now},"DC04 permission failure retains a labelled prior value without declaring normal")
        verify(listOf(point("HIGH_BEAM","ON","state"),point("DOOR_FL","1","position"),point("BRAKE_PEDAL","true","state"),point("TIRE_LIMIT_FL","200","kPa")).all{!assess(it).attention},"DC05 lamp/door/pedal/low-pressure threshold are not mechanical faults")
        verify(listOf(point("WASHER_LOW","true","state"),point("POWER_DOWN","true","state"),point("ENGINE_OIL","LOW","state"),point("BRAKE_FLUID","HIGH","state")).all{assess(it).attention},"DC06 fresh supported vehicle warning states are review items only")
        val low=point("EV_SOC","18","%")
        verify(assess(low).attention&&!DriveCheck.assess("EV_SOC",low,low,policy.copy(enabled=false),now).attention,"DC07 low SOC uses the enabled user criterion rather than a fault threshold")
        verify(!DriveCheck.accepted(low.copy(unit="kPa"))&&!DriveCheck.accepted(low.copy(source=ObservationSource.TEST_ONLY))&&!assess(low.copy(receivedAt=now+1)).attention,"DC08 wrong units, synthetic provenance and future clocks cannot be current checks")
        verify(!Regex("[0-9%]").containsMatchIn(DriveCheck.draftText("EV_SOC"))&&DriveCheck.draftType("EV_SOC")==SymptomType.OTHER,"DC09 suggested symptom contains no automatic vehicle number/time/identity")

        val id=UUID.randomUUID().toString();val rawName="drivecheck-raw-$id.db";val lowName="drivecheck-low-$id.db"
        ObservationStore(c,rawName).use{raw->LowSocStore(c,lowName).use{store->
            raw.append(low);store.configure(true,20,raw);store.reconcile(raw,now+100)
            verify(store.notices().isEmpty(),"DC10 enabling does not replay historical low values")
            raw.append(point("EV_SOC","80","%",now+10));store.reconcile(raw,now+100)
            raw.append(point("EV_SOC","18","%",now+20));store.reconcile(raw,now+100);val episode=store.notices().single().id
            raw.append(point("EV_SOC","18","%",now+30));store.reconcile(raw,now+100)
            verify(store.notices().single().id==episode&&store.changeCount()==1,"DC11 repeated low receipts retain one episode and one entry")
            raw.append(point("EV_SOC","45","%",now+15));store.reconcile(raw,now+100)
            verify(store.notices().single().phase=="LOW"&&store.changeCount()==1,"DC12 an out-of-order receipt cannot falsely clear an active low episode")
            val failure=low.copy(raw=null,unit=null,receivedAt=null,valid=false,status=ObservationStatus.TIMEOUT)
            raw.append(failure);store.reconcile(raw,now+100)
            verify(store.notices().single().phase=="LOW"&&SocGuide.text(store.policy(),store.notices(),failure,now+100).contains("판단하지"),"DC13 receive interruption preserves history and presents unknown current condition")
            raw.append(point("EV_SOC","21","%",now+50));store.reconcile(raw,now+100)
            verify(store.notices().single().phase=="LOW","DC14 two-point exit margin avoids boundary chatter")
            raw.append(point("EV_SOC","22","%",now+60));store.reconcile(raw,now+100)
            verify(store.notices().single().let{it.id==episode&&it.phase=="EXITED"}&&store.changeCount()==2,"DC15 return to range closes the same condition, never a user issue")
            verify(SocGuide.text(store.policy(),store.notices(),point("EV_SOC","22","%",now+60),now+100).contains("판정한 결과는 아니"),"DC16 condition exit is explicitly separate from repair/safe driving")
        }}
        ObservationStore(c,rawName).use{raw->LowSocStore(c,lowName).use{store->
            verify(store.notices().single().phase=="EXITED"&&store.changeCount()==2&&raw.refs().size==8,"DC17 raw originals and entry/exit restore independently after reopening")
            store.configure(false,20,raw);raw.append(point("EV_SOC","18","%",now+70));store.reconcile(raw,now+100)
            verify(store.changeCount()==2&&store.notices().single().phase=="EXITED","DC18 turning off preserves previous evidence without a new episode")
        }}

        val observerRaw=ObservationStore(c,"drivecheck-observer-$id.db");var allowed=true;var next=Soc();var observer:PleosSocObserver?=null
        main{observer=PleosSocObserver(c,observerRaw,{allowed},{},{},factory={next});observer!!.start()}
        try{
            await{observer!!.receivedCount==1}
            verify(observer!!.registered&&observer!!.lastReceivedAt!=null&&observerRaw.mostRecent("EV_SOC")!!.detail.contains("SYNTHETIC_VALIDATION_ONLY"),"DC19 registration plus a valid fixture receipt are reported separately")
            main{observer!!.start()};verify(next.registered==1,"DC20 repeated start never duplicates SOC registration")
            val count=observerRaw.refs().size;val old=next;main{observer!!.stop();allowed=false;old.value(18)};Thread.sleep(100)
            verify(observerRaw.refs().size==count&&old.released==1,"DC21 stopped/unsafe observer rejects late callback before raw commit")
            main{allowed=true;next=Soc();observer!!.start()};await{observer!!.receivedCount==1}
            main{next.failure(SecurityException("fixture"))};await{!observer!!.running}
            verify(observerRaw.mostRecent("EV_SOC")!!.status==ObservationStatus.PERMISSION_DENIED&&next.released==1,"DC22 permission failure is terminal with provider cleanup")
            main{next=Soc().apply{supported=false};observer!!.start()};await{!observer!!.running}
            verify(observerRaw.mostRecent("EV_SOC")!!.status==ObservationStatus.UNSUPPORTED&&next.registered==0,"DC23 unsupported Capability never registers a listener")
            main{next=Soc().apply{sendInitial=false};observer!!.start()};await{!observer!!.running}
            verify(observerRaw.mostRecent("EV_SOC")!!.status==ObservationStatus.TIMEOUT&&observer!!.receivedCount==0&&next.released==1,"DC24 registration with no actual value times out instead of displaying success")
        }finally{main{observer?.stop()};observerRaw.close()}
        return lines
    }

    fun ui():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val prefix="gleo-ui-"+UUID.randomUUID();val db=prefix+".db"
        val (a,b)=SqliteIssueRepository(c,db).use{val service=CaritdaService(it,{true});service.registerDriver("DC작성A") to service.registerDriver("DC작성B")}
        val activity=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        val raw=ObservationStore(c,prefix+"-observations.db");val low=LowSocStore(c,prefix+"-soc.db");val fake=Soc();var observer:PleosSocObserver?=null
        fun click(tag:String){await{activity.window.decorView.findViewWithTag<Button>(tag)?.isEnabled==true};main{check(activity.window.decorView.findViewWithTag<Button>(tag).performClick())}}
        try{
            click("driver_${a.id}");click("start_driving")
            main{observer=PleosSocObserver(activity,raw,{true},{low.reconcile(raw)},{activity.javaClass.getDeclaredMethod("draw").apply{isAccessible=true}.invoke(activity)},factory={fake})
                val field=activity.javaClass.getDeclaredField("socObserver").apply{isAccessible=true};(field.get(activity) as PleosSocObserver).stop();field.set(activity,observer)}
            click("nav_VEHICLE");click("soc_notices");main{activity.window.decorView.findViewWithTag<EditText>("soc_threshold").setText("20")};click("enable_soc")
            await{observer!!.receivedCount==1};main{fake.value(18)};await{low.notices().size==1}
            SqliteIssueRepository(c,db).use{verify(it.issues().isEmpty()&&it.draft()==null,"DC25 automatic SOC warning creates no confirmed user problem or draft")}
            click("drivecheck_draft_EV_SOC")
            val draft=SqliteIssueRepository(c,db).use{it.draft()!!}
            verify(draft.ownerId==a.id&&draft.type==SymptomType.OTHER&&!Regex("[0-9%]").containsMatchIn(draft.body),"DC26 driver choice creates an editable original-author draft with no injected sensor values")
            click("nav_HOME");click("nav_VEHICLE");click("drivecheck_draft_EV_SOC")
            SqliteIssueRepository(c,db).use{verify(it.draft()?.id==draft.id&&it.draft()?.body==draft.body&&it.issues().isEmpty(),"DC27 repeated review preserves the existing draft without replacing or automatically saving it")}
            click("resume_draft");main{activity.window.decorView.findViewWithTag<EditText>("situation").append(" 정차 중 직접 확인한 내용이에요.")};click("save_issue")
            val issue=SqliteIssueRepository(c,db).use{it.issues().single()}
            verify(issue.reporter.id==a.id&&issue.status==IssueStatus.UNRESOLVED&&issue.situation.contains("직접 확인"),"DC28 explicit save creates exactly one driver-confirmed problem")
            val body=IssueExplanation.payload(issue)
            verify(JSONObject(body).length()==4&&!Regex("[0-9%]").containsMatchIn(JSONObject(body).getString("situation"))&&main{activity.window.decorView.findViewWithTag<Button>("issue_ai_explain")!=null},"DC29 existing optional AI route retains category/situation only; no model called")
            click("menu");click("change_driver");click("driver_${b.id}");click("start_driving");click("all_issues");click("issue_${issue.id}");click("acknowledge")
            SqliteIssueRepository(c,db).use{verify(it.issue(issue.id)?.copy(updatedAt=issue.updatedAt)==issue&&it.events(issue.id).count{e->e.kind==EventKind.ACKNOWLEDGED&&e.actor.id==b.id}==1,"DC30 B acknowledgement preserves A content and manual issue status")}
            verify(low.notices().single().phase=="LOW"&&low.changeCount()==1,"DC31 driver switch and issue save do not create duplicate SOC episodes")
            check(c.getSharedPreferences("drivecheck-soc-ui-cold-pointer",0).edit().putString("prefix",prefix).putString("issue",issue.id).putString("episode",low.notices().single().id).commit())
        }finally{main{activity.finish()};inst.waitForIdleSync();await{observer?.running!=true};raw.close();low.close()}
        return lines
    }
    fun cold():List<String>{
        ValidationEnvironment.requireIsolated(c);val pointer=c.getSharedPreferences("drivecheck-soc-ui-cold-pointer",0)
        val prefix=pointer.getString("prefix",null)?:error("RUN_SOC_UI_FIRST");check(Regex("gleo-ui-[a-f0-9-]{36}").matches(prefix))
        LowSocStore(c,prefix+"-soc.db").use{verify(it.policy().enabled&&it.policy().threshold==20&&it.notices().single().id==pointer.getString("episode",null)&&it.changeCount()==1&&it.gaps().isNotEmpty(),"DC32 policy, one episode and stop gap survive actual process restart")}
        SqliteIssueRepository(c,prefix+".db").use{verify(it.issues().size==1&&it.draft()==null&&it.events(pointer.getString("issue",null)!!).size==2,"DC33 manual A/B record remains independent from the restored automatic guide")}
        return lines
    }
}
