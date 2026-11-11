package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.widget.*
import android.view.View
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.ai.IssueExplanation
import com.nextdrive.pleos.safety.*
import java.io.File
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual Console -> public SDK -> actual MainActivity -> explicit fixture issue -> handover.
 * The coordinator owns only its bounded Console session and always restores SOC100/P/0. */
class DriveCheckSocLiveChecks(private val inst:Instrumentation,private val runId:String=""){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T{var value:T?=null;var error:Throwable?=null;inst.runOnMainSync{try{value=block()}catch(t:Throwable){error=t}};error?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    private fun await(block:()->Boolean){val end=System.currentTimeMillis()+40000;while(System.currentTimeMillis()<end){if(main(block))return;Thread.sleep(80)};error("ACTUAL_SOC_STAGE_TIMEOUT")}
    private fun stage(phase:String){c.openFileOutput("drivecheck-soc-$runId.json",0).use{it.write(JSONObject().put("phase",phase).toString().toByteArray())}}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c);check(Regex("[a-f0-9-]{36}").matches(runId)){"CONSOLE_COORDINATOR_REQUIRED"}
        val prefix="gleo-ui-"+UUID.randomUUID();val db=prefix+".db"
        val (a,b)=SqliteIssueRepository(c,db).use{val s=CaritdaService(it,{true});s.registerDriver("SDK작성A") to s.registerDriver("SDK작성B")}
        val raw=ObservationStore(c,prefix+"-observations.db");val low=LowSocStore(c,prefix+"-soc.db")
        val activity=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        fun click(tag:String){if(BLightTestNavigation.prepare(inst,activity,tag))return;await{activity.window.decorView.findViewWithTag<Button>(tag)?.isEnabled==true};main{check(activity.window.decorView.findViewWithTag<Button>(tag).performClick())}}
        var issueId=""
        try{
            click("driver_${a.id}");click("start_driving");click("nav_VEHICLE");click("soc_notices")
            main{activity.window.decorView.findViewWithTag<EditText>("soc_threshold").setText("20")};click("enable_soc")
            await{raw.mostRecent("EV_SOC",true)?.raw=="100"}
            verify(raw.refs().any{it.value.raw=="100"&&it.value.detail.contains("PLEOS_PUBLIC_SOC_2_3_2")&&it.value.detail.contains("SOC GET")},"SL01 initial100 is an actual SDK receipt, not Console HTTP success")
            verify(low.notices().isEmpty(),"SL02 current normal range creates no low episode")
            stage("READY_100")
            await{low.notices().singleOrNull()?.phase=="LOW"}
            val episode=low.notices().single();val entered=raw.refs().single{it.rowId==episode.row&&it.storeId==episode.store}
            verify(entered.value.raw=="18"&&entered.value.source==ObservationSource.PLEOS_EMULATOR_SDK&&entered.value.detail.contains("SOC CALLBACK"),"SL03 actual18 callback creates the evidence-linked user criterion episode")
            verify(main{activity.window.decorView.findViewWithTag<Button>("drivecheck_draft_EV_SOC")?.isEnabled==true}&&SqliteIssueRepository(c,db).use{it.issues().isEmpty()},"SL04 actual warning offers a user draft without confirming a problem automatically")
            stage("LOW_18")
            click("drivecheck_draft_EV_SOC")
            val initial=main{activity.window.decorView.findViewWithTag<EditText>("situation").text.toString()}
            verify(!Regex("[0-9%]").containsMatchIn(initial),"SL05 actual sensor numbers are excluded from the editable symptom and AI input")
            main{activity.window.decorView.findViewWithTag<EditText>("situation").append(" 정차 중 직접 확인한 내용을 남겨요.")};click("save_issue")
            val issue=SqliteIssueRepository(c,db).use{it.issues().single()};issueId=issue.id
            verify(issue.reporter.id==a.id&&JSONObject(IssueExplanation.payload(issue)).length()==4,"SL06 explicit A save retains the existing minimal optional AI contract; model not called")
            await{File(c.filesDir,"drivecheck-control-$runId.txt").let{it.isFile&&it.readText()=="REPEAT"}}
            click("nav_VEHICLE");click("read_vehicle");await{activity.window.decorView.findViewWithTag<Button>("read_vehicle")?.isEnabled==true}
            verify(raw.mostRecent("EV_SOC",true)?.raw=="18"&&low.notices().single().id==episode.id&&low.changeCount()==1,"SL07 repeated actual SDK18 GET preserves one episode and one entry")
            click("soc_notices");stage("REPEAT_DONE")
            await{low.notices().single().phase=="EXITED"}
            val exit=low.notices().single();val exitRef=raw.refs().single{it.storeId==exit.store&&it.rowId==exit.row}
            verify(exit.id==episode.id&&exit.value=="45"&&low.changeCount()==2&&exitRef.value.detail.contains("SOC CALLBACK"),"SL08 actual45 callback exits the same condition with immutable entry/exit evidence")
            click("menu");click("change_driver");click("driver_${b.id}");click("start_driving");click("all_issues");click("issue_${issue.id}");click("acknowledge")
            SqliteIssueRepository(c,db).use{verify(it.issue(issue.id)?.copy(updatedAt=issue.updatedAt)==issue&&it.events(issue.id).any{e->e.kind==EventKind.ACKNOWLEDGED&&e.actor.id==b.id},"SL09 B handover is an explicit user history, separate from automatic SOC exit")}
            stage("EXIT_45")
        }finally{
            main{activity.finish()}
            await{low.gaps().isNotEmpty()}
            val rows=raw.refs().size;Thread.sleep(600)
            verify(raw.refs().size==rows,"SL10 hidden app stops receiving and records an observation gap")
            if(issueId.isNotBlank())check(c.getSharedPreferences("drivecheck-soc-live-cold-pointer",0).edit().putString("prefix",prefix).putString("issue",issueId).putInt("rows",rows).commit())
            raw.close();low.close();stage("STOPPED")
        }
        return lines
    }
    fun cold():List<String>{
        ValidationEnvironment.requireIsolated(c);val p=c.getSharedPreferences("drivecheck-soc-live-cold-pointer",0);val prefix=p.getString("prefix",null)?:error("RUN_ACTUAL_SOC_FIRST")
        check(Regex("gleo-ui-[a-f0-9-]{36}").matches(prefix))
        LowSocStore(c,prefix+"-soc.db").use{verify(it.policy().enabled&&it.policy().threshold==20&&it.notices().single().phase=="EXITED"&&it.changeCount()==2&&it.gaps().isNotEmpty(),"SL11 actual SDK policy/episode/exit/gap survive a cold process without a new episode")}
        ObservationStore(c,prefix+"-observations.db").use{verify(it.refs().size==p.getInt("rows",-1)&&it.refs().all{ref->ref.value.source==ObservationSource.PLEOS_EMULATOR_SDK},"SL12 actual raw SDK observations restore without copied synthetic or normal-app data")}
        SqliteIssueRepository(c,prefix+".db").use{verify(it.issues().size==1&&it.draft()==null&&it.events(p.getString("issue",null)!!).size==2,"SL13 user-confirmed A/B record remains intact after automatic-state restoration")}
        return lines
    }
    fun restored():List<String>{
        ValidationEnvironment.requireIsolated(c);val safe=CountDownLatch(1);val done=CountDownLatch(1)
        var gate:CarSafetyGate?=null;var reader:PleosReadClient?=null
        val raw=ObservationStore(c,"drivecheck-restored-${UUID.randomUUID()}.db")
        try{
            main{gate=CarSafetyGate(c){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()};check(safe.await(12,TimeUnit.SECONDS))
            main{reader=PleosReadClient(c,raw){if(reader?.busy==false)done.countDown()};reader!!.readOnce()};check(done.await(20,TimeUnit.SECONDS))
            verify(raw.mostRecent("EV_SOC",true)?.let{it.raw=="100"&&it.source==ObservationSource.PLEOS_EMULATOR_SDK}==true,"SL14 actual SDK confirms restored SOC100, independently of Console HTTP")
            verify(main{gate?.state==EditingState.ALLOWED},"SL15 actual safety gate is parked/allowed after the bounded Console experiment")
        }finally{main{reader?.stop();gate?.stop()};raw.close()}
        return lines
    }
}
