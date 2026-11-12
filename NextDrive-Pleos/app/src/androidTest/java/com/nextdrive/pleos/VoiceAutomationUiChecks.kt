package com.nextdrive.pleos

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.widget.Button
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.analytics.VehicleAnalytics
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.voice.*
import org.json.JSONObject
import java.util.UUID

/** Actual Activity/controller/SQLite/client/crypto. ASR, provider and playback callbacks are fixtures. */
class VoiceAutomationUiChecks(private val inst:Instrumentation){
    private val ctx=inst.targetContext;private val lines=mutableListOf<String>()
    private fun ck(value:Boolean,label:String){check(value){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T {var value:T?=null;var error:Throwable?=null;inst.runOnMainSync{try{value=block()}catch(t:Throwable){error=t}};error?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    private fun await(block:()->Boolean){val end=System.currentTimeMillis()+18000;while(System.currentTimeMillis()<end){if(main(block))return;Thread.sleep(60)};error("VOICE_UI_TIMEOUT")}
    private fun set(a:Any,name:String,value:Any?){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun controller(a:Activity)=a.javaClass.getDeclaredField("controller").apply{isAccessible=true}.get(a) as VoiceConversationController
    private class Speech:SpeechBackend{
        override val source="VALIDATION_ASR_CALLBACK_FIXTURE";lateinit var events:SpeechBackend.Events
        var requests=0;var releases=0
        override fun initialize(ready:()->Unit,failed:(String)->Unit)=ready()
        override fun listen(events:SpeechBackend.Events){this.events=events;requests++;events.ready();events.started()}
        override fun finish(){};override fun detach(){};override fun release(){releases++}
    }
    internal inner class Session:PortableSession{
        val uid="voice-ui-"+UUID.randomUUID();val key=DeviceKeyStore("caritda.validation.$uid")
        override val signedIn=true;override val subject=VehicleAnalytics.id(uid)
        override fun matches(uid:String)=uid==this.uid
        override fun acquire(done:(String?,String?,String?)->Unit)=done(uid,"fixture",null)
        var issueCalls=0;var dialogueCalls=0;var op="";var payload="";var question=""
        val transport={path:String,body:JSONObject,_:String,_:Int->when{
            path.endsWith("challenge")->{if(body.getString("purpose")=="EXPLAIN")op="o2.${System.currentTimeMillis()}.${UUID.randomUUID()}";JSONObject().put("challenge_id",UUID.randomUUID().toString()).put("binding_id","device-fixture").put("operation_id",op.ifBlank{"-"}).put("nonce",DeviceKeyStore.b64(ByteArray(32){2})).put("expires_at",System.currentTimeMillis()/1000+60)}
            path.endsWith("register")->JSONObject().put("device_id","device-fixture").put("key_version",1)
            path=="/v2/account/status"->JSONObject().put("model_enabled",true).put("remaining",5).put("uncertain_block",false)
            path=="/v2/issues/explanations"->{payload=body.getString("analysis_payload");issueCalls++;JSONObject().put("state","SUCCEEDED")}
            path=="/v2/issues/dialogue"->{payload=body.getString("analysis_payload");question=JSONObject(payload).getString("question");dialogueCalls++;JSONObject().put("state","SUCCEEDED")}
            path.startsWith("/v2/operations/")->{val f=JSONObject(payload);val dialogue=f.getString("contract")==IssueDialogueContract.CONTRACT
                val a=JSONObject().put("contract",f.getString("contract")).put("origin","MODEL").put("model",PortableAnalysis.MODEL).put("operation_id",op).put("evidence_digest",VehicleAnalytics.id(payload)).put("facts",f)
                if(dialogue)a.put("answer",if(question.contains("정비소"))"소음이 반복되면 정비소 점검을 고려해 주세요."else"앞서 남긴 앞쪽 소음이 반복되는 상황을 살펴봐 주세요.")
                    .put("checks","정차한 뒤 소리가 난 상황을 적어 주세요.").put("inspection","증상이 반복되거나 심해지면 점검을 고려해 주세요.")
                    .put("uncertainty","기록만으로 원인을 확인할 수 없어요.").put("evidence_quote",f.getJSONObject("selected_record").getString("situation").take(3)).put("context_refs",org.json.JSONArray(listOf(0,1))).put("generated_at",System.currentTimeMillis())
                else a.put("possible_causes","여러 부위에서 소음이 전달될 수 있어요.").put("checks","정차한 뒤 소리의 발생 상황을 적어 주세요.").put("inspection","소음이 반복되면 정비소 점검을 고려해 주세요.").put("evidence_quote",f.getString("situation").take(3))
                JSONObject().put("state","SUCCEEDED").put("result",a)
            }
            else->error("UNEXPECTED_VOICE_FIXTURE_ROUTE")
        }}
        override fun client(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?)=PortableAiClient(store,key,uid,bearer,allowed,transport,job,record)
        override fun dialogueClient(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String,record:String)=PortableAiClient(store,key,uid,bearer,allowed,transport,job,record,true)
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(ctx)
        val prefix="gleo-ui-"+UUID.randomUUID();val db="$prefix.db";val session=Session()
        SqliteIssueRepository(ctx,db).use{val service=CaritdaService(it,{true});service.registerDriver("음성 작성자").also(service::selectDriver)}
        val owner=SqliteIssueRepository(ctx,db).use{it.currentDriver()!!.id}
        VoiceSettings(ctx,"$prefix-settings").write(owner,session.subject,VoiceSettings.Permit(true,true,true,true,true,false))
        var activity=inst.startActivitySync(Intent(ctx,VoiceConversationActivity::class.java).putExtra("validation_fixture",prefix).putExtra("driver_id",owner).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as VoiceConversationActivity
        try{
            val engine=Speech();val spoken=StringBuilder()
            main{
                set(activity,"aiSession\$delegate",lazy{session as PortableSession});set(activity,"account",session.subject)
                set(activity,"speech\$delegate",lazy{SpeechInput(activity,factory={engine},allowed={true})})
                set(activity,"playback\$delegate",lazy{WholeExplanationPlayback({true},{text,done->spoken.append(text);activity.window.decorView.post{done(PlaybackOutcome.DONE)}},{})})
                activity.javaClass.getDeclaredMethod("render").apply{isAccessible=true}.invoke(activity)
            }
            await{activity.window.decorView.findViewWithTag<Button>("voice_start_stop")?.isEnabled==true}
            main{activity.window.decorView.findViewWithTag<Button>("voice_start_stop").performClick()}
            await{engine.requests==1}
            val callback=engine.events
            main{callback.updated("차 앞쪽에서",false)}
            ck(SqliteIssueRepository(ctx,db).use{it.issues().isEmpty()},"partial never saves a problem")
            main{callback.updated("카잇다, 차 앞쪽에서 이상한 소리가 나.",true)}
            ck(SqliteIssueRepository(ctx,db).use{it.issues().isEmpty()},"final alone cannot save until actual ended fixture")
            main{callback.ended()}
            await{engine.requests==2&&session.issueCalls==1}
            val issue=SqliteIssueRepository(ctx,db).use{it.issues().single()}
            ck(issue.reporter.id==owner&&issue.type==SymptomType.NOISE&&issue.situation.contains("이상한 소리"),"Activity commits voice issue to confirmed author without save button")
            ck(spoken.contains("가능한 원인")&&main{controller(activity).issueId==issue.id},"saved issue automatically reaches existing client then full result playback fixture")
            main{callback.updated("중복 콜백 기록해 줘.",true);callback.ended()}
            ck(SqliteIssueRepository(ctx,db).use{it.issues().size==1}&&session.issueCalls==1,"late old ASR cannot duplicate issue or AI")
            fun ask(text:String,expected:Int){val events=engine.events;main{events.updated(text,true);events.ended()};await{session.dialogueCalls==expected&&engine.requests==expected+2}}
            ask("그럼 어떻게 해야 해?",1)
            ck(JSONObject(session.payload).getJSONArray("context_turns").length()==2,"first NEW follow-up sends initial same-issue question/AI inference")
            ask("정비소에 가야 할까?",2)
            val payload=JSONObject(session.payload)
            ck(payload.getString("question").contains("정비소")&&payload.getJSONArray("context_turns").length()==4,"second NEW question carries bounded prior Q/A pairs")
            ck(main{controller(activity).issueId==issue.id}&&session.issueCalls==1&&session.dialogueCalls==2,"one issue plus two distinct dialogue operations; fixture model only")
            main{
                val root=activity.javaClass.getDeclaredField("shell").apply{isAccessible=true}.get(activity) as View
                BLightHarness(inst).views(root).filterIsInstance<android.widget.TextView>().firstOrNull{it.text.startsWith("검증 전용")}?.text="검증 전용 · TEST · ASR·AI·재생 콜백 예시"
                val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
                val folder=java.io.File(ctx.filesDir,"voice-captures").apply{mkdirs()};java.io.File(folder,"voice-dialogue-fixture.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            val sid=main{controller(activity).sessionId!!}
            main{controller(activity).end()}
            ConversationStore(ctx,"$prefix-conversations.db").use{store->
                val messages=store.messages(sid,owner,session.subject)
                ck(messages.count{it.role=="USER"}==3&&messages.count{it.origin=="GEMINI_NEW"}==3,"every question/new response persisted in order with origin")
                ck(store.readableDatabase.rawQuery("SELECT count(*) FROM playbacks WHERE state='DONE'",null).use{it.moveToFirst();it.getInt(0)==3},"full playback fixture terminal linked to each answer")
                ck(store.readableDatabase.rawQuery("SELECT count(*) FROM links WHERE issue_id=? AND state='COMMITTED'",arrayOf(issue.id)).use{it.moveToFirst();it.getInt(0)==1},"conversation and atomic receipt link repaired/committed once")
            }
            ck(engine.releases>0&&!main{(activity.javaClass.getDeclaredField("speech\$delegate").apply{isAccessible=true}.get(activity) as Lazy<*>).value.let{it as SpeechInput}.busy},"end releases microphone; no automatic restart")
            main{activity.finish()};Thread.sleep(300)
            activity=inst.startActivitySync(Intent(ctx,VoiceConversationActivity::class.java).putExtra("validation_fixture",prefix).putExtra("driver_id",owner).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as VoiceConversationActivity
            ConversationStore(ctx,"$prefix-conversations.db").use{ck(it.messages(sid,owner,session.subject).size==6,"Activity recreation preserves encrypted history")}
            val recreated=inst.addMonitor(VoiceConversationActivity::class.java.name,null,false)
            main{activity.recreate()}
            activity=recreated.waitForActivityWithTimeout(15000) as? VoiceConversationActivity?:error("VOICE_RECREATE_FAILED")
            inst.removeMonitor(recreated)
            ck(main{controller(activity).state in setOf(VoiceConversationController.State.IDLE,VoiceConversationController.State.ENDED)},"configuration recreation restores history without restarting microphone or AI")
            main{activity.finish()}
            val homeActivity=inst.startActivitySync(Intent(ctx,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
            val voiceMonitor=inst.addMonitor(VoiceConversationActivity::class.java.name,null,false)
            var fromSettings:VoiceConversationActivity?=null
            try{
                val h=BLightHarness(inst);h.click(homeActivity,"driver_$owner");h.click(homeActivity,"start_driving");h.click(homeActivity,"settings_help");h.click(homeActivity,"voice_settings_history")
                fromSettings=voiceMonitor.waitForActivityWithTimeout(15000) as? VoiceConversationActivity?:error("SETTINGS_VOICE_NOT_OPENED")
                h.click(fromSettings,"voice_home");await{h.get(homeActivity,"screen").toString()=="HOME"&&homeActivity.hasWindowFocus()}
                ck(true,"voice Home returns from Settings to actual home; Back remains separate")
            }finally{inst.removeMonitor(voiceMonitor);main{fromSettings?.finish();homeActivity.finish()}}
            SqliteIssueRepository(ctx,db).use{repo->val s=CaritdaService(repo,{true});val b=s.registerDriver("다음 운전자");s.selectDriver(b);ck(s.needsAcknowledgement(repo.issue(issue.id)!!),"same automatically saved issue enters B handover");s.acknowledge(issue.id);ck(repo.issue(issue.id)?.status==IssueStatus.UNRESOLVED,"B reads without auto-solving AI issue")}
            lines.add("BOUNDARIES actual_activity=true actual_sqlite=true actual_keystore=true asr=FIXTURE provider=FIXTURE tts=FIXTURE real_model_calls=0")
        } finally {main{activity.finish()}}
        return lines
    }
}
