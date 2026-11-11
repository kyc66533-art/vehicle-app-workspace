package com.nextdrive.pleos

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.analytics.VehicleAnalytics
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.ui.VehicleUi
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Same Activities, SQLite, encrypted store and client; synthetic STT/provider/TTS boundaries.
 * Only a unique validation family is used. No Firebase login, real relay or model request. */
class IntegratedFlowChecks(private val inst:Instrumentation){
    private val c=inst.targetContext
    private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T {var value:T?=null;var error:Throwable?=null;inst.runOnMainSync{try{value=block()}catch(t:Throwable){error=t}};error?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    private fun await(block:()->Boolean){val end=System.currentTimeMillis()+14000;while(System.currentTimeMillis()<end){if(main(block))return;Thread.sleep(80)};error("INTEGRATED_UI_TIMEOUT")}
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
    private fun text(a:Activity,tag:String)=all(a.window.decorView.findViewWithTag<View>(tag)).filterIsInstance<TextView>().joinToString("\n"){it.text}
    private fun set(a:Any,name:String,value:Any?){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun render(a:PortableAiActivity){a.javaClass.getDeclaredMethod("render").apply{isAccessible=true}.invoke(a)}
    private fun session(a:PortableAiActivity,s:FixtureSession){main{set(a,"session\$delegate",lazy{s as PortableSession});set(a,"loadedSubject",s.subject);set(a,"excerpt",null);set(a,"excerptInput",null);render(a)}}
    private class Speech:SpeechBackend{
        override val source="VALIDATION_CALLBACK_FIXTURE";lateinit var events:SpeechBackend.Events
        override fun initialize(ready:()->Unit,failed:(String)->Unit)=ready()
        override fun listen(events:SpeechBackend.Events){this.events=events}
        override fun finish(){};override fun detach(){};override fun release(){}
    }
    inner class FixtureSession:PortableSession {
        val id=UUID.randomUUID().toString();val uid="flow-fixture-$id"
        val key=DeviceKeyStore("caritda.validation.flow.$id")
        override val signedIn=true
        override val subject=VehicleAnalytics.id(uid)
        override fun matches(uid:String)=uid==this.uid
        override fun acquire(done:(String?,String?,String?)->Unit)=done(uid,"fixture-bearer",null)
        var posts=0;var remaining=4;var enabled=true;var outcome="SUCCEEDED";var drop=false
        var operation="";var frozen="";var entered:CountDownLatch?=null;var release:CountDownLatch?=null
        fun answer():JSONObject=JSONObject().put("contract",IssueExplanation.CONTRACT).put("origin","MODEL").put("model",PortableAnalysis.MODEL)
            .put("operation_id",operation).put("evidence_digest",VehicleAnalytics.id(frozen)).put("facts",JSONObject(frozen)).put("evidence_quote",JSONObject(frozen).getString("situation").take(3))
            .put("possible_causes","여러 부위에서 소음이 전달될 수 있어요. ".repeat(7))
            .put("checks","정차한 뒤 증상이 나타나는 상황을 적어 주세요. ".repeat(6))
            .put("inspection","증상이 반복되거나 심해지면 정비소에서 점검을 받아 주세요. ".repeat(5))
        private val transport={path:String,body:JSONObject,_:String,_:Int->
            when {
                path.endsWith("challenge")->{val purpose=body.getString("purpose");if(purpose=="EXPLAIN")operation="o2.${System.currentTimeMillis()}.${UUID.randomUUID()}"
                    JSONObject().put("challenge_id",UUID.randomUUID().toString()).put("binding_id","fixture-device").put("operation_id",if(purpose=="REGISTER")"-"else operation.ifBlank{"-"})
                        .put("nonce",DeviceKeyStore.b64(ByteArray(32){1})).put("expires_at",System.currentTimeMillis()/1000+60)}
                path.endsWith("register")->JSONObject().put("device_id","fixture-device").put("key_version",1)
                path=="/v2/account/status"->JSONObject().put("model_enabled",enabled).put("remaining",remaining).put("uncertain_block",false).put("quota_mode","recurring")
                path=="/v2/issues/explanations"->{posts++;remaining--;frozen=body.getString("analysis_payload");entered?.countDown();release?.let{check(it.await(8,TimeUnit.SECONDS))};if(drop){drop=false;throw PortableFailure("APP_NETWORK")};JSONObject().put("state",outcome)}
                path.startsWith("/v2/operations/")->JSONObject().put("state",outcome).put("result",if(outcome=="SUCCEEDED")answer()else JSONObject.NULL)
                else->error("UNEXPECTED_FIXTURE_ROUTE")
            }
        }
        override fun client(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?)=
            PortableAiClient(store,key,uid,bearer,allowed,transport,job,record)
        fun store()=PortableAiStore(c,"portable-issue-v1-$subject")
        fun api(issue:VehicleIssue)=client(store(),uid,"fixture-bearer",{true},IssueExplanation.jobKey(issue),issue.id)
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val prefix="gleo-ui-"+UUID.randomUUID();val db=prefix+".db"
        val(a,b)=SqliteIssueRepository(c,db).use{val s=CaritdaService(it,{true});s.registerDriver("인계 운전자 A") to s.registerDriver("인계 운전자 B")}
        var activity=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var ai:PortableAiActivity?=null
        fun button(tag:String)=activity.window.decorView.findViewWithTag<Button>(tag)
        fun click(tag:String){if(BLightTestNavigation.prepare(inst,activity,tag))return;await{button(tag)?.isEnabled==true};main{check(button(tag).performClick())}}
        try{
            click("driver_${a.id}");click("start_driving")
            val engine=Speech();val input=main{SpeechInput(activity,factory={engine},allowed={true})}
            main{set(activity,"speechInput\$delegate",lazy{input})}
            click("new_issue")
            val blocked=inst.addMonitor(PortableAiActivity::class.java.name,null,false)
            click("save_issue_ai")
            verify(blocked.hits==0&&SqliteIssueRepository(c,db).use{it.issues().isEmpty()},"IF01 failed empty save retains form and never opens AI")
            inst.removeMonitor(blocked)
            main{activity.window.decorView.findViewWithTag<EditText>("situation").setText("정차 중 기존 소음이에요.")}
            click("voice_symptom");await{input.busy}
            main{engine.events.started();engine.events.updated("음성으로 덧붙인 소음이에요.",true)}
            verify(main{!button("save_issue_ai").isEnabled},"IF02 AI-save waits for actual STT ended after final")
            main{engine.events.ended()};await{button("save_issue_ai").isEnabled}
            val original="정차 중 기존 소음이에요.\n음성으로 덧붙인 소음이에요. 직접 확인한 문장이에요."
            main{activity.window.decorView.findViewWithTag<EditText>("situation").append(" 직접 확인한 문장이에요.")}
            SqliteIssueRepository(c,db).use{verify(it.issues().isEmpty()&&it.draft()?.body==original,"IF03 STT and manual edits preserve draft without automatic issue or AI")}
            click("nav_HOME");click("resume_draft")
            verify(main{activity.window.decorView.findViewWithTag<EditText>("situation").text.toString()==original},"IF04 home and resume restore complete edited draft")
            val monitor=inst.addMonitor(PortableAiActivity::class.java.name,null,false)
            click("save_issue_ai")
            ai=(inst.waitForMonitorWithTimeout(monitor,14000) as? PortableAiActivity)?:error("AI_ROUTE_MISSING");inst.removeMonitor(monitor)
            val screen=ai!!
            val issue=SqliteIssueRepository(c,db).use{it.issues().single()}
            verify(issue.reporter.id==a.id&&issue.situation==original&&issue.status==IssueStatus.UNRESOLVED,"IF05 explicit integrated save commits one original A record")
            SqliteIssueRepository(c,db).use{verify(it.draft()==null&&it.events(issue.id).size==1,"IF06 committed draft consumed; no AI state or history mutation")}
            await{screen.window.decorView.findViewWithTag<EditText>("issue_ai_excerpt")!=null}
            verify(main{screen.intent.getStringExtra("issue_id")==issue.id&&screen.window.decorView.findViewWithTag<View>("portable_saved_record")!=null&&screen.window.decorView.findViewWithTag<EditText>("issue_ai_excerpt").text.toString()==original},"IF07 committed ID and symptom reach existing AI screen without retyping")
            verify(main{screen.window.decorView.findViewWithTag<View>("portable_payload_preview")!=null&&screen.window.decorView.findViewWithTag<View>("portable_speak")==null},"IF08 save route previews transmission; no speech before a validated result")
            val fixture=FixtureSession();fixture.api(issue).register();session(screen,fixture)
            val excerpt="정차 중 들리는 소음만 설명해 주세요."
            main{screen.window.decorView.findViewWithTag<EditText>("issue_ai_excerpt").setText(excerpt)}
            verify(fixture.posts==0,"IF09 entering AI and editing preview make no provider request")
            main{check(screen.window.decorView.findViewWithTag<Button>("portable_explain").performClick())}
            await{screen.window.decorView.findViewWithTag<View>("portable_speak")!=null}
            verify(fixture.posts==1&&fixture.remaining==3,"IF10 explicit AI button dispatches exactly one fixture analysis and consumes one fixture allowance")
            verify(JSONObject(fixture.frozen).let{it.length()==4&&it.getString("situation")==excerpt&&!fixture.frozen.contains(issue.id)&&!it.has("vehicle")},"IF11 existing category/situation contract excludes identity, time, location and sensors")
            verify(main{text(screen,"portable_model_result").contains("이번 요청")&&text(screen,"portable_model_result").contains("정비소 점검")&&text(screen,"portable_result_input").contains(excerpt)},"IF12 actual View presents new conditional Korean result with its frozen input")
            val spoken=mutableListOf<String>();val done=mutableListOf<(Boolean)->Unit>()
            val player=ExplanationPlayback({true},{phrase,complete,_->spoken.add(phrase);done.add(complete)},{})
            main{set(screen,"playback\$delegate",lazy{player});screen.window.decorView.findViewWithTag<Button>("portable_speak").performClick()}
            verify(spoken.size==1&&player.busy,"IF13 TTS receives prepared result only on explicit playback")
            var n=0;while(player.busy){val callback=done[n++];main{callback(true);callback(true)};check(n<40)}
            val expected=IssueAnalysisService.saved(fixture.api(issue).jobState().getJSONObject("last")).text
            verify(spoken.joinToString("")==expected&&spoken.all{it.length<=100}&&spoken.size>1,"IF14 complete long result reaches bounded TTS phrases; duplicate done never repeats text")
            verify(fixture.posts==1&&fixture.remaining==3,"IF15 voice playback never consumes Gemini allowance")
            main{screen.window.decorView.findViewWithTag<EditText>("issue_ai_excerpt").setText("다음에 보낼 사본도 보존해 주세요.");screen.finish()};ai=null
            await{button("issue_ai_explain")?.isEnabled==true}
            SqliteIssueRepository(c,db).use{verify(it.issue(issue.id)==issue&&it.events(issue.id).size==1,"IF16 AI, TTS and return preserve original content/status/history")}
            click("menu");click("change_driver");click("driver_${b.id}");click("start_driving");click("all_issues");click("issue_${issue.id}")
            verify(main{button("acknowledge")?.isEnabled==true},"IF17 B sees A handover with explicit unread action")
            click("acknowledge");click("add_note")
            main{activity.window.decorView.findViewWithTag<EditText>("action_note").setText("정차 후 확인한 점검 메모예요.")};click("save_action")
            SqliteIssueRepository(c,db).use{repo->val events=repo.events(issue.id);verify(events.count{it.kind==EventKind.ACKNOWLEDGED&&it.actor.id==b.id}==1&&events.any{it.kind==EventKind.NOTE&&it.actor.id==b.id},"IF18 B read and inspection-note history commits with B identity")
                verify(repo.issue(issue.id)?.copy(updatedAt=issue.updatedAt)==issue&&events.first().actor.id==a.id,"IF19 original author, symptom, version and manual status remain unchanged; event update time may advance")}
            val again=inst.addMonitor(PortableAiActivity::class.java.name,null,false);click("issue_ai_explain")
            ai=(inst.waitForMonitorWithTimeout(again,14000) as? PortableAiActivity)?:error("AI_REENTRY_MISSING");inst.removeMonitor(again)
            val reopened=ai!!;session(reopened,fixture)
            await{reopened.window.decorView.findViewWithTag<View>("portable_speak")!=null}
            verify(main{text(reopened,"portable_model_result").contains("이전에 저장")&&reopened.window.decorView.findViewWithTag<EditText>("issue_ai_excerpt").text.toString()=="다음에 보낼 사본도 보존해 주세요."},"IF20 AI reentry restores encrypted excerpt and saved result separately")
            verify(fixture.posts==1,"IF21 B/cache reentry creates no additional analysis")
            main{reopened.finish()};ai=null
            main{activity.finish()}
            activity=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
            click("driver_${b.id}");click("start_driving");click("all_issues");click("issue_${issue.id}")
            verify(main{activity.window.decorView.findViewWithTag<View>("acknowledged")!=null},"IF22 restarted Activity restores B acknowledgement without a second read event")
            SqliteIssueRepository(c,db).use{verify(it.issues().size==1&&it.events(issue.id).size==3&&it.draft()==null,"IF23 restart preserves single issue and complete A/B history with no ghost draft")}
            check(c.getSharedPreferences("integrated-flow-fixture-pointer",0).edit().putString("store","portable-issue-v1-${fixture.subject}")
                .putString("uid",fixture.uid).putString("key","caritda.validation.flow.${fixture.id}").putString("job",IssueExplanation.jobKey(issue)).putString("db",db).putString("issue",issue.id).putString("author",a.id).putString("reader",b.id).commit())
        }finally{main{ai?.finish();activity.finish()}}
        policies()
        return lines
    }
    private fun policies(){
        val issue=VehicleIssue(UUID.randomUUID().toString(),SymptomType.NOISE,"정차 중 들리는 소음이에요.",1,2,Driver.A,"private memo")
        val s=FixtureSession();val api=s.api(issue);api.register();s.outcome="UNKNOWN";s.drop=true
        fun explain(fresh:Boolean=true,current:()->VehicleIssue?={issue})=IssueAnalysisService.explain(api,issue,issue.situation,fresh,current)
        verify(runCatching{explain()}.isFailure&&api.hasPending()&&s.posts==1,"IF24 interrupted provider response preserves pending intent")
        val intent=api.jobState().getJSONObject("pending").getString("intent")
        verify((runCatching{explain()}.exceptionOrNull() as? PortableFailure)?.code=="MODEL_OUTCOME_UNKNOWN"&&s.posts==1&&api.jobState().getJSONObject("pending").getString("intent")==intent,"IF25 UNKNOWN recovery never creates another dispatch/intent")
        s.outcome="SUCCEEDED";verify(explain().origin==IssueAnalysisService.Origin.RECOVERED&&s.posts==1,"IF26 existing operation recovers frozen validated result without rebilling")
        verify(explain(false).origin==IssueAnalysisService.Origin.SAVED&&s.posts==1,"IF27 saved service result stays offline and marked saved")
        s.remaining=0;verify((runCatching{explain()}.exceptionOrNull() as? PortableFailure)?.code=="TOTAL_LIMIT"&&s.posts==1&&!api.hasPending(),"IF28 daily/monthly zero blocks fresh body before new intent")
        s.remaining=3;s.enabled=false;verify((runCatching{explain()}.exceptionOrNull() as? PortableFailure)?.code=="MODEL_DISABLED"&&s.posts==1,"IF29 model-OFF policy is honored without changing deployed ON state")
        verify((runCatching{explain(current={issue.copy(revision=2)})}.exceptionOrNull() as? PortableFailure)?.code=="ISSUE_CHANGED"&&s.posts==1,"IF30 changed revision cannot reuse stale selected input")
        verify((runCatching{explain(current={null})}.exceptionOrNull() as? PortableFailure)?.code=="ISSUE_UNAVAILABLE","IF31 deleted or missing record cannot dispatch")
        val concurrent=FixtureSession();val first=concurrent.api(issue);first.register();concurrent.entered=CountDownLatch(1);concurrent.release=CountDownLatch(1)
        var failure:Throwable?=null;val thread=Thread{try{IssueAnalysisService.explain(first,issue,issue.situation,true){issue}}catch(t:Throwable){failure=t}};thread.start()
        check(concurrent.entered!!.await(8,TimeUnit.SECONDS))
        val blocked=runCatching{IssueAnalysisService.explain(concurrent.api(issue),issue,issue.situation,true){issue}}.exceptionOrNull()
        concurrent.release!!.countDown();thread.join(10000)
        verify(blocked is PortableFailure&&blocked.code=="REQUEST_PENDING"&&concurrent.posts==1&&failure==null&&!thread.isAlive,"IF32 simultaneous UI/Gleo service entry cannot double dispatch")
        var speechCount=0;var complete:((Boolean)->Unit)?=null;var allowed=true
        val playback=ExplanationPlayback({allowed},{_,done,_->speechCount++;complete=done},{})
        playback.play("확인할 설명이에요. ".repeat(30)){};playback.cancel();complete!!(true)
        verify(speechCount==1&&!playback.busy,"IF33 cancelled TTS ignores late completion and never reads next phrase")
        allowed=false;playback.play("제한 중 설명"){}
        verify(speechCount==1,"IF34 safety restriction prevents result playback")
        allowed=true;var failureVisible=false
        playback.play("설명을 읽다가 실패한 경우예요. ".repeat(20)){failureVisible=!playback.busy}
        complete!!(false)
        verify(!playback.busy&&speechCount==2&&failureVisible,"IF41 TTS failure ends playback and restores retry controls without another phrase")
    }

    fun cold():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val pointer=c.getSharedPreferences("integrated-flow-fixture-pointer",0)
        val name=pointer.getString("db",null)?:error("RUN_INTEGRATED_FLOW_FIRST")
        check(Regex("gleo-ui-[a-f0-9-]{36}\\.db").matches(name))
        val store=PortableAiStore(c,pointer.getString("store",null)!!);val job=pointer.getString("job",null)!!
        val state=store.read();val saved=state.getJSONObject("jobs").getJSONObject(job)
        val client=PortableAiClient(store,DeviceKeyStore(pointer.getString("key",null)!!),pointer.getString("uid",null)!!,"unused",{true},
            {_,_,_,_->error("COLD_RESULT_MUST_BE_OFFLINE")},job)
        verify(client.explain("").getString("origin")=="MODEL"&&IssueAnalysisService.saved(saved.getJSONObject("last")).origin==IssueAnalysisService.Origin.SAVED,"IF37 encrypted integrated fixture survives actual process restart offline")
        verify(saved.getString("excerpt")=="다음에 보낼 사본도 보존해 주세요.","IF38 separate AI excerpt survives process restart without rewriting the issue")
        verify(state.getJSONObject("usage_status").getInt("remaining")==3&&!saved.has("pending"),"IF39 fixture usage and completed operation survive restart without a new request")
        SqliteIssueRepository(c,name).use{repo->val id=pointer.getString("issue",null)!!;val issue=repo.issue(id)!!;val events=repo.events(id)
            verify(issue.reporter.id==pointer.getString("author",null)&&events.size==3&&events.any{it.kind==EventKind.ACKNOWLEDGED&&it.actor.id==pointer.getString("reader",null)}&&repo.draft()==null,"IF40 A/B original and read/inspection history survive actual process restart")}
        return lines
    }

    /** Actual SDK receipts beside a provider-format fixture result. Vehicle data never enters
     * the relay request, and the ordinary/previous validation databases are not selected. */
    fun vehicle():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val prefix="gleo-ui-"+UUID.randomUUID()
        val raw=ObservationStore(c,prefix+"-observations.db");val snapshots=SnapshotStore(c,prefix+"-snapshots.db")
        var gate:CarSafetyGate?=null;var reader:PleosReadClient?=null;var activity:PortableAiActivity?=null
        val safe=CountDownLatch(1);val received=CountDownLatch(1)
        try{
            main{gate=CarSafetyGate(c){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()}
            check(safe.await(12,TimeUnit.SECONDS))
            main{reader=PleosReadClient(c,raw){if(reader?.busy==false)received.countDown()};reader!!.readOnce()}
            check(received.await(22,TimeUnit.SECONDS));main{reader!!.stop()}
            snapshots.reconcile(raw);val snapshot=snapshots.latest()?:error("NO_ACTUAL_SNAPSHOT")
            verify(listOf("EV_SOC","ODOMETER").all{kind->snapshot.evidence.any{it.value.kind==kind&&SnapshotStore.accepted(it.value)&&it.value.source==ObservationSource.PLEOS_EMULATOR_SDK}},"IF42 actual SDK SOC and distance committed before presentation; no synthetic replacements")
            val issue=SqliteIssueRepository(c,prefix+".db").use{repo->val s=CaritdaService(repo,{gate?.state==EditingState.ALLOWED});val a=s.registerDriver("차량연결 fixture");s.selectDriver(a);s.record(SymptomType.NOISE,"정차 중 소음이 들려요.",System.currentTimeMillis(),"전송하지 않는 메모")}
            snapshots.prepareLink(issue.id,snapshot.id);snapshots.completeLink(issue.id)
            val screen=inst.startActivitySync(Intent(c,PortableAiActivity::class.java).putExtra("validation_fixture",prefix).putExtra("issue_id",issue.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PortableAiActivity;activity=screen
            await{screen.window.decorView.findViewWithTag<View>("portable_vehicle_context")!=null&&screen.window.decorView.findViewWithTag<EditText>("issue_ai_excerpt")!=null}
            main{screen.window.decorView.findViewWithTag<Button>("portable_vehicle_details").performClick()};await{all(screen.window.decorView).filterIsInstance<TextView>().any{it.text.contains("마지막 수신")}}
            verify(main{val text=all(screen.window.decorView).filterIsInstance<TextView>().joinToString("\n"){it.text};
                val receivedValues=snapshot.evidence.filter{it.value.kind in setOf("EV_SOC","ODOMETER")&&SnapshotStore.accepted(it.value)}
                receivedValues.size==2&&receivedValues.all{ref->val v=ref.value;val shown=java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(java.math.BigDecimal(v.raw!!).stripTrailingZeros());text.contains("$shown ${v.unit}")}&&text.contains("마지막 수신")&&text.contains("에뮬레이터")&&text.contains("기기에서만")
            },"IF43 actual linked SDK SOC/distance/time/source remain available under local-only details")
            val fixture=FixtureSession();fixture.api(issue).register();session(screen,fixture)
            main{screen.window.decorView.findViewWithTag<Button>("portable_explain").performClick()}
            await{screen.window.decorView.findViewWithTag<View>("portable_model_result")!=null}
            verify(fixture.posts==1&&fixture.frozen==IssueExplanation.payload(issue)&&JSONObject(fixture.frozen).length()==4,"IF44 actual SDK context cannot enrich the existing category/situation wire payload (provider fixture)")
            verify(main{val texts=all(screen.window.decorView).filterIsInstance<TextView>().joinToString("\n"){it.text};texts.contains("마지막 수신")&&texts.contains("증상 당시 값이나 원인")&&screen.window.decorView.findViewWithTag<View>("portable_model_result")!=null}&&SqliteIssueRepository(c,prefix+".db").use{it.issue(issue.id)==issue&&it.events(issue.id).size==1},"IF45 actual SDK context and fixture AI result coexist without changing original issue/history")
        }finally{main{activity?.finish();reader?.stop();gate?.stop()};raw.close();snapshots.close()}
        return lines
    }

    /** Actual SDK TTS with a validated fixture explanation, never an actual Gemini response. */
    fun tts():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val foreground=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture","gleo-ui-"+UUID.randomUUID()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var gate:CarSafetyGate?=null;val safe=CountDownLatch(1)
        main{gate=CarSafetyGate(c){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()}
        check(safe.await(12,TimeUnit.SECONDS))
        var output:SpeechOutput?=null;var playback:ExplanationPlayback?=null
        try{
            main{output=SpeechOutput(c){gate?.state==EditingState.ALLOWED};playback=ExplanationPlayback({gate?.state==EditingState.ALLOWED},
                {phrase,complete,changed->output!!.speak(phrase,complete,changed)},{output!!.stop()})}
            repeat(2){round->
                val done=CountDownLatch(1);var last=""
                main{playback!!.play("가능한 원인입니다. 여러 부위에서 소음이 전달될 수 있어요. 정차한 뒤 증상이 나타난 상황을 적어 주세요. 증상이 반복되거나 심해지면 정비소에서 점검을 받아 주세요. 확정 진단은 아니에요."){message->last=message;if(playback?.busy==false)done.countDown()}}
                val complete=done.await(55,TimeUnit.SECONDS)
                val code=when{last=="음성 안내를 마쳤어요."->"COMPLETED";last.contains("응답이 늦어")->"WATCHDOG";last.contains("확인 코드: ")->last.substringAfter("확인 코드: ").takeIf{Regex("[A-Z_0-9]+").matches(it)}?:"SDK_ERROR";last.contains("멈췄어요")->"STOPPED";else->"INCOMPLETE"}
                check(complete&&code=="COMPLETED"){"IF35_TTS_METADATA_ONLY round=$round code=$code callback=$complete source=${main{output?.source}} busy=${main{output?.busy}} started=${main{output?.started}}"}
                verify(true,"IF35_${round+1} actual Korean TTS completes the whole prepared text sequentially on the same player")
            }
            verify(main{output?.source=="PLEOS_ON_DEVICE"},"IF36 actual Pleos on-device output, not Gemini or Gleo playback")
        }finally{main{playback?.cancel();gate?.stop();foreground.finish()};inst.waitForIdleSync()}
        return lines
    }
}
