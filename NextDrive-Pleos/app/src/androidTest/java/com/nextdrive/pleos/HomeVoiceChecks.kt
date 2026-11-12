package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.voice.*
import java.util.UUID

/** Actual home Activity, safety gate, SQL and shared voice engine; ASR/AI/TTS are fixtures. */
class HomeVoiceChecks(private val inst:Instrumentation){
    private val ctx=inst.targetContext;private val h=BLightHarness(inst);private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,text:String){check(ok){text};lines.add("PASS $text")}
    private fun await(check:()->Boolean){val end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){if(h.main(check))return;Thread.sleep(60)};error("HOME_VOICE_TIMEOUT")}
    private class Speech:SpeechBackend{
        override val source="HOME_ASR_FIXTURE";var requests=0;var releases=0;lateinit var events:SpeechBackend.Events
        override fun initialize(ready:()->Unit,failed:(String)->Unit)=ready()
        override fun listen(events:SpeechBackend.Events){requests++;this.events=events;events.ready();events.started()}
        override fun finish(){};override fun detach(){};override fun release(){releases++}
    }
    private class Focus:VoiceListeningFocus{
        var granted=true;var held=false;var lost:(()->Unit)?=null
        override fun acquire(lost:()->Unit):Boolean{this.lost=lost;held=granted;return granted}
        override fun release(){held=false}
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(ctx)
        val prefix="gleo-ui-"+UUID.randomUUID();val session=VoiceAutomationUiChecks(inst).Session()
        val settings=VoiceSettings(ctx,"$prefix-settings")
        val drivers=SqliteIssueRepository(ctx,"$prefix.db").use{val s=CaritdaService(it,{true});val a=s.registerDriver("홈 음성 A");val b=s.registerDriver("다음 운전자 B");s.selectDriver(a);a.id to b.id}
        val owner=drivers.first;val nextOwner=drivers.second
        ck(!settings.read(owner,session.subject).autoWait,"new auto standby defaults OFF, without changing existing consent bits")
        settings.write(owner,session.subject,VoiceSettings.Permit(true,true,true,true,true,false,true))
        ck(settings.read(owner,session.subject).autoWait&&!settings.read("B",session.subject).autoWait,"standby permission remains driver/account scoped")
        val a=inst.startActivitySync(Intent(ctx,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        val monitor=inst.addMonitor(VoiceConversationActivity::class.java.name,null,false)
        val engines=mutableListOf<Speech>();val focus=Focus();val completions=java.util.ArrayDeque<(PlaybackOutcome)->Unit>();val spoken=mutableListOf<String>()
        var allowed=true;lateinit var runtime:HomeVoiceRuntime
        try{
            h.main{
                (h.get(a,"homeVoice") as HomeVoiceRuntime).close()
                val service=h.get(a,"service") as CaritdaService
                val paint=a.javaClass.getDeclaredMethod("paintHomeVoice").apply{isAccessible=true}
                runtime=HomeVoiceRuntime(a,prefix,service,h.get(a,"repository") as SqliteIssueRepository,h.get(a,"observations") as ObservationStore,h.get(a,"lowSoc") as LowSocStore,h.get(a,"attentionEvents") as AttentionEventStore,
                    {allowed&&h.get(a,"screen").toString()=="HOME"&&(h.get(a,"safety") as CarSafetyGate).state==EditingState.ALLOWED&&service.confirmed()},{paint.invoke(a)},session,{Speech().also{engines.add(it)}},focus)
                h.set(runtime,"playback",WholeExplanationPlayback({true},{text,done->spoken.add(text);completions.add(done)},{completions.clear()}))
                h.set(a,"homeVoice",runtime);runtime.resume()
            }
            ck(engines.isEmpty(),"no microphone before explicit driver confirmation")
            h.click(a,"driver_$owner");h.click(a,"start_driving")
            await{runtime.microphoneActive}
            ck(engines.single().requests==1&&monitor.hits==0,"home starts one bounded STT window without a voice screen/start button")
            ck(h.main{a.window.decorView.findViewWithTag<android.widget.TextView>("home_voice_status")?.text.toString()=="음성 대기 중"},"home waiting label is backed by actual started callback fixture")
            val wake=engines[0].events
            h.main{wake.updated("카잇다",false)};ck(runtime.controller==null,"partial wake cannot start a conversation")
            h.main{wake.updated("카 있다",true)};ck(runtime.controller==null,"final wake including ASR word spacing still waits for actual ended callback")
            h.main{wake.ended()};await{spoken.any{it.contains("말씀하세요")}}
            ck(engines.size==1&&!runtime.microphoneActive&&engines[0].releases>0,"wake service is released before prompt TTS; no microphone overlap")
            fun finishSpeech(){h.main{while(completions.isNotEmpty())completions.removeFirst()(PlaybackOutcome.DONE)}}
            finishSpeech();await{engines.size==2&&engines[1].requests==1}
            ck(runtime.controller?.state==VoiceConversationController.State.LISTENING,"prompt DONE automatically starts symptom input")
            fun say(text:String){val e=engines[1].events;h.main{e.updated(text,true);e.ended()}}
            say("차 앞쪽에서 이상한 소리가 나.");await{session.issueCalls==1&&completions.isNotEmpty()}
            val issue=SqliteIssueRepository(ctx,"$prefix.db").use{it.issues().single()}
            ck(issue.reporter.id==owner&&runtime.controller?.issueId==issue.id,"home speech commits one real SQLite record to confirmed driver")
            h.main{wake.updated("카잇다",true);wake.ended()};ck(session.issueCalls==1,"late wake callback cannot duplicate the issue or model request")
            finishSpeech();await{engines[1].requests==2}
            val localQuestion="배터리 얼마나 남았어?"
            say(localQuestion);await{completions.isNotEmpty()}
            val localReply=runtime.controller!!.lastReply
            ck(session.issueCalls==1&&session.dialogueCalls==0,"local vehicle query never makes a model request")
            finishSpeech();await{engines[1].requests==3}
            say("그럼 어떻게 해야 해?");await{session.dialogueCalls==1&&completions.isNotEmpty()}
            val sent=org.json.JSONObject(session.payload).getJSONArray("context_turns")
            ck(sent.length()==2&&(0 until sent.length()).none{sent.getJSONObject(it).getString("text") in setOf(localQuestion,localReply)},"local vehicle question/answer excluded from Gemini context; only prior validated AI retained")
            ck(runtime.controller?.issueId==issue.id,"new follow-up keeps same issue and session without another microphone button")
            finishSpeech();await{engines[1].requests==4}
            val sid=runtime.controller!!.sessionId!!
            say("그만할게");await{runtime.title=="음성 대기 중"}
            ck(engines.size==3&&monitor.hits==0,"normal conversation end returns to another bounded home window")
            ConversationStore(ctx,"$prefix-conversations.db").use{s->ck(s.messages(sid,owner,session.subject).any{it.origin=="ASR_WAKE"}&&s.messages(sid,owner,session.subject).count{it.origin=="GEMINI_NEW"}==2,"wake, symptom and two generated fixture replies persist in one encrypted session")}
            val second=engines[2].events;h.main{second.updated("마이크 꺼줘",true);second.ended()};await{!runtime.active}
            ck(!settings.read(owner,session.subject).autoWait&&!runtime.microphoneActive,"explicit stop listening persists OFF and releases microphone")
            ck(h.main{a.window.decorView.findViewWithTag<View>("home_voice_toggle")?.visibility!=View.VISIBLE},"spoken OFF hides the inactive standby retry button")
            val count=engines.size;h.main{runtime.evaluate();second.updated("카잇다",true);second.ended()}
            ck(engines.size==count,"redraw and late callbacks never revive disabled standby")
            settings.write(owner,session.subject,settings.read(owner,session.subject).copy(autoWait=true));focus.granted=false
            h.main{runtime.retry()};ck(!runtime.active&&engines.size==count,"focus rejection never labels an inactive microphone as waiting")
            focus.granted=true;h.main{runtime.retry()};await{runtime.microphoneActive}
            h.main{focus.lost!!.invoke()};ck(!runtime.active&&!runtime.microphoneActive,"audio focus loss stops STT and conversation, no gain retry")
            h.main{runtime.retry()};await{runtime.microphoneActive}
            allowed=false;h.main{runtime.evaluate()};ck(!runtime.active,"restricted foreground gate stops current window")
            allowed=true;h.main{runtime.pause()};ck(!runtime.microphoneActive,"background transition releases microphone")
            h.main{runtime.resume()};await{runtime.microphoneActive}
            h.click(a,"change_driver");h.click(a,"driver_$nextOwner");h.click(a,"start_driving")
            ck(!runtime.active&&!runtime.microphoneActive,"driver change ends old session; new driver has no inherited microphone consent")
            ConversationStore(ctx,"$prefix-conversations.db").use{store->ck(runCatching{store.messages(sid,nextOwner,session.subject)}.isFailure,"other driver's handover rejects access to private dialogue")}
            ck(SqliteIssueRepository(ctx,"$prefix.db").use{it.issues().size==1},"lifecycle changes and wake windows do not create extra issues")
            settings.write(nextOwner,session.subject,VoiceSettings.Permit(false,false,true,true,true,true,true))
            // Main's alert ingress uses the actual Firebase scope; only this isolated
            // runtime's model transport uses the fixture subject.
            settings.write(nextOwner,com.nextdrive.pleos.ai.auth.FirebaseSessionProvider.subject().orEmpty(),VoiceSettings.Permit(false,false,true,true,true,true,true))
            h.main{runtime.retry()};await{runtime.microphoneActive}
            val ref=h.main{(h.get(a,"observations") as ObservationStore).appendRef(VehicleObservation("WASHER_LOW","true","state",System.currentTimeMillis(),ObservationSource.PLEOS_EMULATOR_SDK,true,true,"home-fixture",ObservationStatus.RECEIVED,"SYNTHETIC_FIXTURE"))}
            h.main{a.javaClass.getDeclaredMethod("receiveAttention",ObservationRef::class.java).apply{isAccessible=true}.invoke(a,ref)}
            await{completions.isNotEmpty()}
            ck(runtime.controller?.alertId!=null&&monitor.hits==0,"new warning uses inline home conversation instead of opening a voice screen")
            finishSpeech();await{runtime.microphoneActive}
            val episode=runtime.controller!!.alertId!!
            ck((h.get(a,"attentionEvents") as AttentionEventStore).readableDatabase.rawQuery("SELECT state FROM deliveries WHERE episode=?",arrayOf(episode)).use{it.moveToFirst();it.getString(0)=="DONE"},"warning delivery and follow-up session remain linked once")
            val playCount=spoken.size
            h.main{a.javaClass.getDeclaredMethod("receiveAttention",ObservationRef::class.java).apply{isAccessible=true}.invoke(a,ref)}
            ck(spoken.size==playCount&&session.issueCalls==1&&session.dialogueCalls==1,"repeated warning neither speaks twice nor invokes Gemini")
            lines.add("BOUNDARIES HOME=ACTUAL_ACTIVITY SQL=REAL SAFETY=REAL_PARKED ASR_AI_TTS=FIXTURE INDEPENDENT_KWS=NOT_TESTED MODEL_CALLS=0")
        }finally{inst.removeMonitor(monitor);h.main{a.finish()}}
        return lines
    }
}
