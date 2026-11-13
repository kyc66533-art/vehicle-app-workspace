package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.voice.*
import java.util.UUID

/** Existing home/SQLite with synthetic ASR/TTS boundaries. Never a real wake or model claim. */
class HomeWakeConfirmationChecks(private val inst:Instrumentation){
    private val ctx=inst.targetContext;private val h=BLightHarness(inst);private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,text:String){check(ok){text};lines.add("PASS $text")}
    private fun await(ok:()->Boolean){val until=System.currentTimeMillis()+18000;while(System.currentTimeMillis()<until){if(h.main(ok))return;Thread.sleep(60)};error("WAKE_CONFIRMATION_TIMEOUT")}
    private class Speech:SpeechBackend{
        override val source="WAKE_CONFIRMATION_FIXTURE";var requests=0;var releases=0;lateinit var events:SpeechBackend.Events
        override fun initialize(ready:()->Unit,failed:(String)->Unit)=ready()
        override fun listen(events:SpeechBackend.Events){requests++;this.events=events;events.ready();events.started()}
        override fun finish(){};override fun detach(){};override fun release(){releases++}
    }
    private class Focus:VoiceListeningFocus{override fun acquire(lost:()->Unit)=true;override fun release(){}}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(ctx)
        ck(VoiceCommandRouter.needsWakeConfirmation("가 있다.")&&VoiceCommandRouter.afterWake("가 있다")==null,"observed whole phrase requires confirmation and is never accepted as an exact wake")
        ck(listOf("다 있다","거기 가 있다","가 있다 차에서 소리가 나 기록해 줘","네","카잇다").none(VoiceCommandRouter::needsWakeConfirmation),"ordinary variants, combined symptom, yes and exact wake do not enter the uncertain branch")
        ck(VoiceCommandRouter.afterWake("카 있다")==""&&VoiceCommandRouter.route("네",true,false)==VoiceCommand.Clarify,"exact wake remains unchanged and unprompted yes cannot save a record")
        val prefix="gleo-ui-"+UUID.randomUUID();val session=VoiceAutomationUiChecks(inst).Session()
        val owner=SqliteIssueRepository(ctx,"$prefix.db").use{val s=CaritdaService(it,{true});s.registerDriver("호출 확인").id}
        val settings=VoiceSettings(ctx,"$prefix-settings");settings.write(owner,session.subject,VoiceSettings.Permit(true,false,true,true,true,false,true))
        val a=inst.startActivitySync(Intent(ctx,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        val engines=mutableListOf<Speech>();val spoken=mutableListOf<String>();val done=java.util.ArrayDeque<(PlaybackOutcome)->Unit>();lateinit var runtime:HomeVoiceRuntime
        fun issues()=SqliteIssueRepository(ctx,"$prefix.db").use{it.issues()}
        fun say(e:SpeechBackend.Events,text:String){h.main{e.updated(text,true);e.ended()}}
        fun speakDone(){h.main{while(done.isNotEmpty())done.removeFirst()(PlaybackOutcome.DONE)}}
        try{
            h.main{
                (h.get(a,"homeVoice") as HomeVoiceRuntime).close();val service=h.get(a,"service") as CaritdaService
                runtime=HomeVoiceRuntime(a,prefix,service,h.get(a,"repository") as SqliteIssueRepository,h.get(a,"observations") as ObservationStore,h.get(a,"lowSoc") as LowSocStore,h.get(a,"attentionEvents") as AttentionEventStore,
                    {h.get(a,"screen").toString()=="HOME"&&(h.get(a,"safety") as CarSafetyGate).state==EditingState.ALLOWED&&service.confirmed()},{},session,{Speech().also{engines.add(it)}},Focus())
                h.set(runtime,"playback",WholeExplanationPlayback({true},{text,complete->spoken.add(text);done.add(complete)},{done.clear()}));h.set(a,"homeVoice",runtime);runtime.resume()
            }
            h.click(a,"driver_$owner");h.click(a,"start_driving");await{runtime.microphoneActive}
            val wake=engines.last();h.main{wake.events.updated("가 있다",true)}
            ck(runtime.controller==null,"uncertain final alone still waits for actual ended callback")
            h.main{wake.events.ended()};await{done.isNotEmpty()}
            ck(spoken.last().contains("부르셨나요")&&issues().isEmpty()&&session.issueCalls==0,"uncertain wake asks aloud without SQLite issue or AI request")
            ck(wake.releases>0&&!runtime.microphoneActive,"wake is released before confirmation TTS; no input overlap")
            speakDone();await{engines.last()!==wake};val input=engines.last()
            h.main{input.events.updated("네",true)};ck(issues().isEmpty()&&done.isEmpty(),"confirmation final without ended cannot advance")
            h.main{input.events.ended()};await{done.isNotEmpty()}
            ck(spoken.last().contains("말씀하세요")&&input.requests==1&&issues().isEmpty(),"yes asks for a separate symptom and waits for the whole prompt DONE")
            speakDone();await{input.requests==2}
            say(input.events,"차 앞쪽에서 이상한 소리가 나요");await{issues().size==1&&done.isNotEmpty()}
            ck(issues().single().reporter.id==owner&&session.issueCalls==0&&session.dialogueCalls==0,"only confirmed subsequent symptom commits one issue; cloud remains OFF")
            speakDone();await{input.requests==3};say(input.events,"음성 대기 꺼 줘");await{!runtime.autoEnabled&&!runtime.active}
            ck(!runtime.microphoneActive,"spoken OFF after the confirmed flow releases all input")
            fun uncertainAgain():Speech{
                settings.write(owner,session.subject,settings.read(owner,session.subject).copy(autoWait=true));h.main{runtime.retry()};await{runtime.microphoneActive}
                val next=engines.last();say(next.events,"가 있다");await{done.isNotEmpty()};speakDone();await{engines.last()!==next};return engines.last()
            }
            val rejected=uncertainAgain();say(rejected.events,"차 앞쪽에서 소리가 나 기록해 줘");await{runtime.controller?.state==VoiceConversationController.State.ENDED};h.main{runtime.disable()}
            ck(issues().size==1&&session.issueCalls==0,"symptom-shaped reply without yes never commits or calls AI")
            val no=uncertainAgain();say(no.events,"아니요");await{runtime.controller?.state==VoiceConversationController.State.ENDED};h.main{runtime.disable()}
            ck(issues().size==1,"negative confirmation ends without a second problem")
            settings.write(owner,session.subject,settings.read(owner,session.subject).copy(autoWait=true));h.main{runtime.retry()};await{runtime.microphoneActive}
            val cancelled=engines.last();say(cancelled.events,"가 있다");await{done.isNotEmpty()};val count=engines.size
            h.main{done.removeFirst()(PlaybackOutcome.CANCELLED)};await{runtime.controller?.state==VoiceConversationController.State.ERROR_PAUSED}
            ck(engines.size==count&&!runtime.microphoneActive,"cancelled confirmation TTS never starts a microphone")
            h.main{no.events.updated("네",true);no.events.ended();cancelled.events.updated("가 있다",true);cancelled.events.ended()}
            ck(issues().size==1&&engines.size==count,"late yes and old wake callbacks cannot create records or restart")
            lines.add("BOUNDARIES HOME_SQL=REAL ASR_TTS=FIXTURE MODEL_CALLS=0 INDEPENDENT_KWS=NOT_TESTED")
        }finally{h.main{a.finish()}}
        return lines
    }
}
