package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.widget.*
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.voice.*
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.ai.auth.FirebaseSessionProvider
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** Real Console→SOC callback→Main home→automatic real TTS→local query→voice commit.
 * ONLY the follow-up ASR final/ended callbacks are fixtures. No Gemini dispatch. */
class VoiceAttentionSocChecks(private val inst:Instrumentation,private val runId:String,private val manualConsole:Boolean=false){
    private val ctx=inst.targetContext;private val h=BLightHarness(inst);private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun await(timeout:Long=if(manualConsole)240000 else 45000,condition:()->Boolean){val end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(h.main(condition))return;Thread.sleep(70)};error("VOICE_ATTENTION_SOC_TIMEOUT")}
    private fun stage(phase:String){ctx.openFileOutput("drivecheck-soc-$runId.json",0).use{it.write(JSONObject().put("phase",phase).toString().toByteArray())}}
    private class Speech:SpeechBackend{
        override val source="VALIDATION_FOLLOWUP_ASR_FIXTURE";var requests=0;lateinit var events:SpeechBackend.Events
        override fun initialize(ready:()->Unit,failed:(String)->Unit)=ready()
        override fun listen(events:SpeechBackend.Events){requests++;this.events=events;events.ready();events.started()}
        override fun finish(){};override fun detach(){};override fun release(){}
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(ctx);check(Regex("[a-f0-9-]{36}").matches(runId))
        val prefix="gleo-ui-"+UUID.randomUUID();val raw=ObservationStore(ctx,"$prefix-observations.db");val low=LowSocStore(ctx,"$prefix-soc.db");val events=AttentionEventStore(ctx,"$prefix-attention.db")
        val owner=SqliteIssueRepository(ctx,"$prefix.db").use{val s=CaritdaService(it,{true});s.registerDriver("SDK음성A")}
        val main=inst.startActivitySync(Intent(ctx,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var voice:VoiceConversationActivity?=null;val monitor=inst.addMonitor(VoiceConversationActivity::class.java.name,null,false)
        try{
            h.click(main,"driver_${owner.id}");h.click(main,"start_driving");h.click(main,"nav_VEHICLE");h.click(main,"soc_notices")
            h.main{main.window.decorView.findViewWithTag<EditText>("soc_threshold").setText("20")};h.click(main,"enable_soc");h.click(main,"nav_HOME")
            await{raw.mostRecent("EV_SOC",true)?.raw=="100"}
            await{FirebaseSessionProvider.initialized||FirebaseSessionProvider.configState!="READY"}
            h.main{VoiceSettings(ctx,"$prefix-settings").write(owner.id,FirebaseSessionProvider.subject().orEmpty(),VoiceSettings.Permit(false,false,true,true,true,true))}
            stage("READY_100")
            voice=monitor.waitForActivityWithTimeout(if(manualConsole)240000 else 40000) as? VoiceConversationActivity?:error("AUTO_VOICE_ACTIVITY_NOT_OPENED")
            val v=voice;val speech=Speech()
            h.main{h.set(v,"speech\$delegate",lazy{SpeechInput(v,factory={speech},allowed={true})})}
            await{speech.requests==1||(h.get(v,"controller") as VoiceConversationController).state in setOf(VoiceConversationController.State.ERROR_PAUSED,VoiceConversationController.State.ENDED)}
            ck(speech.requests==1,"automatic alert TTS/follow-up state="+h.main{(h.get(v,"controller") as VoiceConversationController).state.name}+" output="+h.main{((h.get(v,"output\$delegate") as Lazy<*>).value as SpeechOutput).let{it.source+":"+it.busy+":"+it.started}})
            val episode=low.notices().single();val event=events.events().single{it.kind=="EV_SOC"}
            ck(episode.id==event.id&&event.phase=="ACTIVE"&&raw.refs().any{it.value.raw=="18"&&it.value.detail.contains("SOC CALLBACK")},"actual SDK18 uses same LowSoc/Attention episode")
            ck(h.main{main.window.decorView.findViewWithTag<View>("home_vehicle_attention")?.let{h.text(it).contains("사용자 기준 20% 미만")}==true},"actual callback immediately updates B home card")
            ck(events.readableDatabase.rawQuery("SELECT state FROM deliveries WHERE episode=?",arrayOf(event.id)).use{it.moveToFirst();it.getString(0)=="DONE"},"permitted new episode automatically finishes real TTS once")
            h.capture(main,"home-voice-sdk18.png",false);h.capture(v,"voice-sdk-attention.png",false)
            ck(SqliteIssueRepository(ctx,"$prefix.db").use{it.issues().isEmpty()},"vehicle warning does not automatically create a problem")
            fun say(text:String){val e=speech.events;h.main{e.updated(text,true);e.ended()}}
            say("배터리 얼마나 남았어?");await{speech.requests==2}
            val controller=h.main{h.get(v,"controller") as VoiceConversationController}
            ck(h.main{controller.lastReply.contains("18%")&&controller.alertId==event.id},"local follow-up uses actual18 and same alert; model0")
            say("이 내용 기록해 줘.");await{speech.requests==3}
            val issue=SqliteIssueRepository(ctx,"$prefix.db").use{it.issues().single()}
            ck(issue.reporter.id==owner.id&&issue.status==com.nextdrive.pleos.model.IssueStatus.UNRESOLVED,"explicit voice command commits warning-linked issue once")
            ck(events.readableDatabase.rawQuery("SELECT count(*) FROM issue_links WHERE episode=? AND issue=?",arrayOf(event.id,issue.id)).use{it.moveToFirst();it.getInt(0)==1},"attention/session/receipt/journal linkage persisted")
            h.capture(v,"voice-sdk-saved.png",false)
            say("그만할게.");await{controller.state==VoiceConversationController.State.ENDED}
            val session=h.main{controller.sessionId!!}
            ConversationStore(ctx,"$prefix-conversations.db").use{store->ck(store.messages(session,owner.id,FirebaseSessionProvider.subject().orEmpty()).any{it.text.contains("18%")&&it.origin=="LOCAL_RULE"},"actual SOC local answer and automatic speech history retained")}
            stage("LOW_18");await{File(ctx.filesDir,"drivecheck-control-$runId.txt").let{it.isFile&&it.readText()=="REPEAT"}}
            // An explicit one-shot repeat through the same public backend, not a synthetic callback.
            // Reuse this Activity's live connection. Releasing a second SDK connection in a
            // callback test can disturb the emulator's shared Vehicle service subscription.
            var repeated=false;h.main{val observer=h.get(v,"soc") as PleosSocObserver;check(observer.running&&observer.registered)
                val sdk=h.get(observer,"backend") as SocBackend;sdk.initial({value->
                    check(value==18);val at=System.currentTimeMillis();val ref=raw.appendRef(VehicleObservation("EV_SOC",value.toString(),"%",at,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"voice-repeat-$runId",ObservationStatus.RECEIVED,"PLEOS_PUBLIC_SOC_2_3_2 · SOC GET"));low.reconcile(raw);events.accept(ref,low);repeated=true
                },{error("EXISTING_SOC_CONNECTION_GET_FAILED")})};await{repeated}
            ck(low.notices().single().id==event.id&&events.events().count{it.kind=="EV_SOC"}==1&&events.readableDatabase.rawQuery("SELECT count(*) FROM deliveries WHERE episode=?",arrayOf(event.id)).use{it.moveToFirst();it.getInt(0)==1},"repeat actual18 creates neither second episode nor automatic speech")
            stage("REPEAT_DONE");await{low.notices().single().phase=="EXITED"&&events.events().first{it.id==event.id}.phase=="RECOVERED"}
            ck(raw.refs().any{it.value.raw=="45"&&it.value.detail.contains("SOC CALLBACK")},"actual45 callback recovers sensor episode")
            ck(SqliteIssueRepository(ctx,"$prefix.db").use{it.issue(issue.id)?.status==com.nextdrive.pleos.model.IssueStatus.UNRESOLVED},"SOC recovery does not resolve driver's issue")
            h.main{v.finish()};await{main.hasWindowFocus()}
            await{main.window.decorView.findViewWithTag<View>("home_vehicle_attention")==null}
            ck(true,"recovered actual SDK condition removes current home card")
            stage("EXIT_45")
            lines.add("BOUNDARIES SDK=REAL_EMULATOR HOME=ACTUAL_VIEW TTS=REAL_ENGINE_DONE FOLLOWUP_ASR=FIXTURE MODEL_CALLS=0")
        }finally{h.main{voice?.finish();main.finish()};inst.removeMonitor(monitor);raw.close();low.close();events.close();stage("STOPPED")}
        return lines
    }
}
