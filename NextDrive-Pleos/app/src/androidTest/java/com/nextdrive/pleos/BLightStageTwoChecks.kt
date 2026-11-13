package com.nextdrive.pleos

import android.app.*
import android.content.Intent
import android.view.*
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.PortableSession
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.ui.*
import com.nextdrive.pleos.vehicle.*
import java.util.UUID

/** Real View interactions; SDK-shaped observations and AI provider are explicit unique fixtures. */
class BLightStageTwoChecks(private val inst:Instrumentation){
    private val h=BLightHarness(inst);private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    fun run():List<String>{
        val prefix="gleo-ui-"+UUID.randomUUID();val db=prefix+".db"
        val (a,b)=SqliteIssueRepository(c,db).use{val s=CaritdaService(it,{true});s.registerDriver("예시작성A") to s.registerDriver("예시인계B")}
        val raw=ObservationStore(c,prefix+"-observations.db");val snapshots=SnapshotStore(c,prefix+"-snapshots.db")
        val now=System.currentTimeMillis()
        fun sample(k:String,v:String,u:String="state")=VehicleObservation(k,v,u,now,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"SYNTHETIC_B_UI_FIXTURE",ObservationStatus.RECEIVED,"SYNTHETIC_B_UI_FIXTURE",now)
        listOf(sample("EV_SOC","80","%"),sample("ODOMETER","24816.0","km"),sample("DOOR_FL","3","position"),sample("LOW_BEAM","ON")).forEach{raw.appendRef(it)}
        raw.appendRef(sample("FUEL_PERCENT","0","%").copy(raw=null,unit=null,receivedAt=null,supported=false,valid=false,status=ObservationStatus.UNSUPPORTED))
        snapshots.reconcile(raw)
        val activity=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var ai:PortableAiActivity?=null
        fun click(key:String)=h.click(activity,key)
        fun text()=h.main{h.text(activity.window.decorView)}
        try{
            click("driver_${a.id}");click("start_driving");click("vehicle_health")
            verify(h.main{activity.window.decorView.findViewWithTag<View>("metric_EV_SOC")!=null&&activity.window.decorView.findViewWithTag<View>("drivecheck_item_DOOR_FL")==null},"BT01 health defaults to major values without expanded raw signal cards")
            click("health_expand")
            click("health_toggle_DOOR_FL")
            verify(text().contains("위치 정보 수신")&&!text().contains("문 위치값 3"),"BT02 door integer is grouped as a received position, never inferred open/closed")
            verify(h.main{h.views(activity.window.decorView).count{it.tag?.toString()?.startsWith("health_group_")==true}==4},"BT03 four door/lamp/notice/threshold groups replace individual technical cards")
            click("health_technical");verify(text().contains("position")&&text().contains("앱 수신"),"BT04 exact raw unit/time/support/source remain in explicit technical details")
            click("nav_HOME");raw.appendRef(sample("POWER_DOWN","true"));h.draw(activity)
            verify(h.main{activity.window.decorView.findViewWithTag<View>("home_vehicle_attention")!=null}&&text().contains("차량에서 받은 안내"),"BT05 fresh SDK-shaped notice has a home reason/action and distinct origin")
            click("open_vehicle_check");click("drivecheck_draft_POWER_DOWN")
            val draft=SqliteIssueRepository(c,db).use{it.draft()!!}
            verify(draft.ownerId==a.id&&!Regex("[0-9]").containsMatchIn(draft.body),"BT06 review choice opens an owned editable draft without automatic sensor numbers")
            h.main{activity.window.decorView.findViewWithTag<EditText>("situation").append(" 정차 후 직접 확인했어요.")};click("save_issue")
            var issue=SqliteIssueRepository(c,db).use{it.issues().single()}
            verify(issue.reporter.id==a.id&&h.main{activity.window.decorView.findViewWithTag<View>("issue_ai_explain")!=null&&activity.window.decorView.findViewWithTag<View>("edit_issue")==null},"BT07 explicit save opens simplified detail; AI is visible and management is folded")
            click("add_note");h.main{activity.window.decorView.findViewWithTag<EditText>("action_note").setText("먼저 보관한 점검 메모")}
            val before=SqliteIssueRepository(c,db).use{it.events(issue.id).size}
            c.openOrCreateDatabase(db,0,null).use{d->d.execSQL("CREATE TRIGGER b_fixture_insert BEFORE INSERT ON drafts BEGIN SELECT RAISE(FAIL,'B_DRAFT_FIXTURE'); END");d.execSQL("CREATE TRIGGER b_fixture_update BEFORE UPDATE ON drafts BEGIN SELECT RAISE(FAIL,'B_DRAFT_FIXTURE'); END")}
            try{
                h.main{activity.window.decorView.findViewWithTag<EditText>("action_note").append(" 보존 실패 후 수정")};click("save_action")
                SqliteIssueRepository(c,db).use{verify(it.events(issue.id).size==before,"BT08 draft persistence failure prevents NOTE commit rather than consuming the old draft")}
                click("nav_HOME")
                verify(h.main{activity.window.decorView.findViewWithTag<EditText>("action_note")?.text?.contains("보존 실패") == true},"BT09 failed draft retention keeps the exact editing screen/text on home navigation")
            }finally{c.openOrCreateDatabase(db,0,null).use{it.execSQL("DROP TRIGGER b_fixture_insert");it.execSQL("DROP TRIGGER b_fixture_update")}}
            click("save_action");verify(SqliteIssueRepository(c,db).use{it.events(issue.id).size==before+1},"BT10 retry commits one explicit note after recovery")
            click("edit_issue");h.main{activity.window.decorView.findViewWithTag<EditText>("situation").append(" 수정한 내용이에요.")};click("save_issue")
            val revised=SqliteIssueRepository(c,db).use{it.issue(issue.id)!!}
            verify(revised.id==issue.id&&revised.discoveredAt==issue.discoveredAt&&revised.revision==issue.revision+1,"BT11 content revision preserves identity/discovery time and the original history")
            issue=revised
            val point=IssueLocation(37.0,127.0,now-700000,now,"SYNTHETIC_B_UI_FIXTURE")
            val backend=object:LocationBackend{override fun supported()=true;override fun read(done:(IssueLocation?,String)->Unit)=done(point,"LOCATION_RECEIVED");override fun release(){}}
            h.main{h.set(activity,"locationReader\$delegate",lazy{OneShotLocation(activity,{backend}){true}})}
            click("attach_location");h.confirm(activity);h.await{(h.get(activity,"activeDialog") as? AlertDialog)?.isShowing==true};h.confirm(activity)
            h.await{activity.window.decorView.findViewWithTag<View>("linked_location")!=null}
            verify(h.main{h.text(activity.window.decorView.findViewWithTag("linked_location"))=="위치가 기록에 저장됐어요."}&&!text().contains("위도 37.0"),"BT12 saved location defaults to concise confirmation, with coordinates folded")
            click("location_details");click("location_history")
            verify(text().contains("위도 37.0")&&h.main{activity.window.decorView.findViewWithTag<View>("location_history_item")!=null},"BT13 actual location history/details remain reachable without rewriting the issue")
            click("link_snapshot");h.confirm(activity)
            verify(snapshots.linked(issue.id)!=null,"BT14 optional original vehicle snapshot link remains an explicit action")
            val monitor=inst.addMonitor(PortableAiActivity::class.java.name,null,false);click("issue_ai_explain");ai=inst.waitForMonitorWithTimeout(monitor,15000) as? PortableAiActivity?:error("AI_ACTIVITY_MISSING");inst.removeMonitor(monitor)
            val screen=ai!!;val fixture=IntegratedFlowChecks(inst).FixtureSession();fixture.api(issue).register()
            h.main{h.set(screen,"session\$delegate",lazy{fixture as PortableSession});h.set(screen,"loadedSubject",fixture.subject);h.render(screen)}
            h.await{screen.window.decorView.findViewWithTag<Button>("portable_explain")?.isEnabled==true}
            verify(fixture.posts==0,"BT15 opening the actual AI screen/preview does not dispatch a model request")
            h.click(screen,"portable_explain");h.await{screen.window.decorView.findViewWithTag<View>("portable_model_result")!=null}
            verify(fixture.posts==1&&fixture.frozen==IssueExplanation.payload(issue),"BT16 explicit fixture-provider request retains only the approved category/situation payload")
            h.click(screen,"portable_vehicle_details")
            verify(h.main{h.text(screen.window.decorView).contains("AI에 보내지")&&screen.window.decorView.findViewWithTag<Button>("portable_speak")?.isEnabled==true},"BT17 result/whole-TTS and local-only vehicle detail coexist without wire enrichment")
            h.click(screen,"portable_home");h.await{activity.window.decorView.findViewWithTag<Button>("settings_help")!=null}
            verify(fixture.posts==1,"BT18 actual AI home action returns to CARITDA home without another request")
            click("settings_help");click("replay_guide")
            verify(h.main{activity.window.decorView.findViewWithTag<Button>("back")!=null&&activity.window.decorView.findViewWithTag<Button>("close_guide")==null&&activity.window.decorView.findViewWithTag<Button>("guide_skip")==null},"BT24 replay uses one previous action without duplicate close or setup completion")
            click("back");click("manage_drivers");click("add_driver");h.main{activity.window.decorView.findViewWithTag<EditText>("driver_name").setText("예시추가C")};click("save_driver")
            val added=SqliteIssueRepository(c,db).use{it.drivers().single{d->d.label=="예시추가C"}}
            click("rename_${added.id}");h.main{activity.window.decorView.findViewWithTag<EditText>("driver_name").setText("예시수정C")};click("save_driver");click("archive_${added.id}");h.confirm(activity)
            SqliteIssueRepository(c,db).use{verify(it.drivers().none{d->d.id==added.id}&&it.issue(issue.id)?.reporter?.id==a.id,"BT19 settings registration/rename/archive preserve historical issue authors")}
            click("nav_HOME");click("change_driver");click("driver_${b.id}");click("start_driving");click("handover");click("issue_${issue.id}");click("acknowledge")
            SqliteIssueRepository(c,db).use{verify(it.issue(issue.id)?.status==issue.status&&it.events(issue.id).count{e->e.kind==EventKind.ACKNOWLEDGED&&e.actor.id==b.id}==1,"BT20 B explicitly acknowledges A's revised problem without resolving it")}
            check(c.getSharedPreferences("b-light-cold-pointer",0).edit().putString("prefix",prefix).putString("issue",issue.id).putInt("events",SqliteIssueRepository(c,db).use{it.events(issue.id).size}).commit())
        }finally{h.main{ai?.finish();activity.finish()};inst.waitForIdleSync();raw.close();snapshots.close()}
        return lines
    }
    fun cold():List<String>{
        val p=c.getSharedPreferences("b-light-cold-pointer",0);val prefix=p.getString("prefix",null)?:error("RUN_B_STAGE_TWO_FIRST");check(Regex("gleo-ui-[a-f0-9-]{36}").matches(prefix));val id=p.getString("issue",null)!!
        SqliteIssueRepository(c,prefix+".db").use{verify(it.issues().size==1&&it.draft()==null&&it.events(id).size==p.getInt("events",-1),"BT21 explicit issue/revision/notes/B handover survive a cold process")}
        IssueLocationStore(c,prefix+"-locations.db").use{verify(it.count(id)==1&&it.latest(id)?.source=="SYNTHETIC_B_UI_FIXTURE","BT22 original optional location restores unchanged")}
        SnapshotStore(c,prefix+"-snapshots.db").use{verify(it.linked(id)!=null,"BT23 original optional vehicle link restores unchanged")}
        return lines
    }
}
