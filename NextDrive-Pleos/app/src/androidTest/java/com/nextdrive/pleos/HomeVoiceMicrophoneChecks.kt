package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.SpeechInput
import com.nextdrive.pleos.integration.SpeechBackend
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.voice.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Human input by default; fixtureInput is explicitly reported separately and never fakes TTS
 * or model replies. Both modes reuse the original persistent two-operation guard. */
class HomeVoiceMicrophoneChecks(private val inst:Instrumentation){
    private val ctx=inst.targetContext;private val h=BLightHarness(inst);private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private var runToken=""
    private class FixtureSpeech:SpeechBackend{
        override val source="VALIDATION_INPUT_REAL_MODEL";lateinit var events:SpeechBackend.Events
        override fun initialize(ready:()->Unit,failed:(String)->Unit)=ready()
        override fun listen(events:SpeechBackend.Events){this.events=events;events.ready();events.started()}
        override fun finish(){};override fun detach(){};override fun release(){}
    }
    private fun phase(value:String){File(ctx.filesDir,"home-microphone-stage.json").writeText("{\"phase\":\"$value\",\"run\":\"$runToken\"}")}
    private fun await(timeout:Long=45000,condition:()->Boolean){val end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(h.main(condition))return;Thread.sleep(80)};error("HOME_MICROPHONE_TIMEOUT_NO_SYNTHETIC_INPUT")}
    private fun budget(session:PortableSession):JSONObject{
        val ready=CountDownLatch(1);var uid:String?=null;var bearer:String?=null
        h.main{session.acquire{u,t,_->uid=u;bearer=t;ready.countDown()}}
        check(ready.await(12,TimeUnit.SECONDS)&&uid!=null&&bearer!=null)
        val api=session.client(PortableAiStore(ctx,"portable-issue-v1-"+session.subject),uid!!,bearer!!,{true},"HOME_BUDGET_READ",null)
        return try{api.status()}finally{api.cancel()}
    }
    fun run(realModel:Boolean,approval:String,fixtureInput:Boolean=false):List<String>{
        ValidationEnvironment.requireIsolated(ctx)
        if(realModel){check(approval=="USER_APPROVED_MAX_TWO_20261112");VoiceRealModelChecks(inst).run(approval,true)}
        else{h.main{FirebaseSessionProvider.prepare(ctx)};await(12000){FirebaseSessionProvider.initialized||FirebaseSessionProvider.configState!="READY"}}
        val session:PortableSession=if(realModel)VoiceRealModelChecks(inst).GuardSession()else FirebasePortableSession(ctx)
        val before=if(realModel)budget(session)else null
        val account=session.subject.orEmpty();val prefix="gleo-ui-"+UUID.randomUUID()
        runToken=prefix
        val owner=SqliteIssueRepository(ctx,"$prefix.db").use{val s=CaritdaService(it,{true});s.registerDriver("음성 시험 운전자").also(s::selectDriver).id}
        VoiceSettings(ctx,"$prefix-settings").write(owner,account,VoiceSettings.Permit(true,realModel,true,true,true,false,true))
        val a=inst.startActivitySync(Intent(ctx,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        val monitor=inst.addMonitor(VoiceConversationActivity::class.java.name,null,false)
        lateinit var runtime:HomeVoiceRuntime
        val fixtureEngines=mutableListOf<FixtureSpeech>()
        val inputFactory:(()->SpeechBackend)?=if(fixtureInput)({FixtureSpeech().also{fixtureEngines.add(it)}})else null
        fun say(text:String){val e=fixtureEngines.last().events;h.main{e.updated(text,true);e.ended()}}
        try{
            h.main{
                (h.get(a,"homeVoice") as HomeVoiceRuntime).close()
                val service=h.get(a,"service") as CaritdaService;val paint=a.javaClass.getDeclaredMethod("paintHomeVoice").apply{isAccessible=true}
                runtime=HomeVoiceRuntime(a,prefix,service,h.get(a,"repository") as SqliteIssueRepository,h.get(a,"observations") as ObservationStore,h.get(a,"lowSoc") as LowSocStore,h.get(a,"attentionEvents") as AttentionEventStore,
                    {h.get(a,"visible")==true&&h.get(a,"screen").toString()=="HOME"&&(h.get(a,"safety") as CarSafetyGate).state==EditingState.ALLOWED&&service.confirmed()},{paint.invoke(a)},session,inputFactory)
                h.set(a,"homeVoice",runtime);runtime.resume()
            }
            phase("READY_TO_START")
            if(!fixtureInput)await(900000){File(ctx.filesDir,"home-microphone-control.txt").takeIf{it.isFile}?.readText()?.trim()=="START:$runToken"}
            // A human may have left the preparation page or already confirmed this driver.
            // Bring only this TEST task forward; never manipulate the ordinary app.
            h.main{a.startActivity(Intent(a,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))}
            await(20000){(h.get(a,"safety") as CarSafetyGate).state==EditingState.ALLOWED&&h.get(a,"visible")==true}
            if(!h.main{(h.get(a,"service") as CaritdaService).confirmed()}){
                phase("DRIVER_CONFIRMING")
                h.click(a,"driver_$owner")
                phase("DRIVER_SELECTED")
                h.click(a,"start_driving")
            }
            // READY permits prompting the human. STARTED can require the first spoken sound.
            // Do not claim microphone activity before STARTED, or wait for the next utterance
            // to begin before reporting that the SDK is ready to accept it.
            fun ready(input:SpeechInput)=input.busy&&input.state.ready
            await(180000){runtime.microphoneActive||ready(h.get(runtime,"wake") as SpeechInput)};phase("WAKE_LISTENING")
            ck(monitor.hits==0,"home "+(if(fixtureInput)"fixture ASR"else"actual SDK STT request")+" is ready after driver confirmation without voice screen/start button")
            if(fixtureInput)say("카잇다")
            try{await(180000){runtime.controller?.sessionId!=null}}catch(e:Exception){error(e.message+" "+runtime.wakeDiagnostics)}
            phase("WAKE_ACCEPTED")
            ck(!runtime.controller!!.sessionId.isNullOrBlank(),(if(fixtureInput)"fixture final+ended wake"else"actual wake")+" released input and reached existing conversation engine")
            if(fixtureInput){
                await{fixtureEngines.size==2&&runtime.controller?.state==VoiceConversationController.State.LISTENING&&runtime.microphoneActive}
                say("카잇다, 차 앞쪽에서 이상한 소리가 나. 기록해 줘.")
            }
            await{runtime.controller?.issueId!=null};phase("ISSUE_SAVED")
            val c=runtime.controller!!;val id=c.issueId!!;val revision=c.issueRevision;val sid=c.sessionId!!
            val issue=SqliteIssueRepository(ctx,"$prefix.db").use{it.issues().single()}
            ck(issue.id==id&&issue.reporter.id==owner,(if(fixtureInput)"explicit validation symptom"else"actual human symptom")+" committed once to fixture driver/issue")
            await(210000){c.state==VoiceConversationController.State.LISTENING&&ready(h.get(runtime,"input") as SpeechInput)&&c.lastReply.isNotBlank()}
            if(realModel)ck(c.replyOrigin in setOf("GEMINI_NEW","GEMINI_RECOVERED"),"actual initial Gemini response followed full TTS DONE")
            else ck(c.replyOrigin=="LOCAL_RULE","actual local record acknowledgement followed TTS DONE; model disabled")
            phase("FOLLOW_UP_LISTENING")
            val input=h.get(runtime,"input") as SpeechInput
            // A confirmed ambiguous wake can itself consume one real STT completion.
            // Require a NEW completion after the symptom/ack, not the old absolute count of two.
            val beforeFollowUp=input.completions
            if(fixtureInput)say("그럼 어떻게 해야 해?")
            await(210000){input.completions>beforeFollowUp&&c.state==VoiceConversationController.State.LISTENING&&ready(input)}
            ck(c.issueId==id&&c.issueRevision==revision&&c.sessionId==sid,(if(fixtureInput)"validation follow-up"else"actual next human utterance")+" retains issue/revision/session without microphone button")
            if(realModel){
                ck(c.replyOrigin in setOf("GEMINI_NEW","GEMINI_RECOVERED"),"actual new dialogue response followed full TTS DONE")
                ck(ctx.getSharedPreferences("voice-real-model-20261112",0).getStringSet("operations",emptySet()).orEmpty().size==2,"shared persistent guard dispatched exactly two approved operations")
                ConversationStore(ctx,"$prefix-conversations.db").use{store->
                    val turns=store.messages(sid,owner,account).filter{it.role in setOf("USER","ASSISTANT")&&it.origin!="ASR_WAKE"}.dropWhile{it.id!=store.messages(sid,owner,account).firstOrNull{m->m.role=="USER"&&m.origin=="ASR"}?.id}
                    val pairs=turns.windowed(2).filter{it[0].role=="USER"&&it[1].origin.startsWith("GEMINI")}.takeLast(2).flatMap{it.map{m->IssueDialogueContract.Turn(m.role,if(m.role=="ASSISTANT")m.text.substringBefore("\n\n")else m.text)}}
                    val payload=IssueDialogueContract.payload(issue,"정비소에 가야 할까?",pairs)
                    ck(payload.toByteArray().size<=3500,"third question context contract checked without a third model request")
                    ck(store.messages(sid,owner,account).count{it.origin.startsWith("GEMINI")}==2,"two actual new model replies retained encrypted with the issue")
                }
                val after=budget(session)
                ck(after.getInt("portable_attempts")-before!!.getInt("portable_attempts")==2&&!after.optBoolean("uncertain_block"),"authenticated usage increased exactly twice with no uncertain result")
                lines.add("BUDGET before_remaining=${before.getInt("remaining")} after_remaining=${after.getInt("remaining")} attempts_before=${before.getInt("portable_attempts")} attempts_after=${after.getInt("portable_attempts")} approved_operations=2")
            }
            h.main{runtime.disable()};phase("COMPLETE")
            ck(monitor.hits==0&&!runtime.microphoneActive,"home session ended and ASR released")
            lines.add("BOUNDARIES ASR=${if(fixtureInput)"EXPLICIT_FIXTURE_INPUT"else"REAL_HUMAN"} TTS=REAL_DONE SQL=REAL MODEL=${if(realModel)"REAL_NEW_X2"else"OFF"} WAKE_SOURCE=${runtime.wakeSource} ORDINARY_APP_WRITES=0")
            return lines
        }finally{h.main{runCatching{runtime.pause()};a.finish()};inst.removeMonitor(monitor)}
    }
}
