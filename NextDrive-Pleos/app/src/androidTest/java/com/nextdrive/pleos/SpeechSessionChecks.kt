package com.nextdrive.pleos

import android.app.Instrumentation
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.ui.*
import android.widget.Button
import android.widget.TextView

/** Synthetic callbacks only. Never captures audio, stores a draft, or touches default DBs. */
class SpeechSessionChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,name:String){check(ok){name};lines.add("PASS $name")}
    private fun <T> main(block:()->T):T{var v:T?=null;inst.runOnMainSync{v=block()};@Suppress("UNCHECKED_CAST")return v as T}
    private fun drain(){inst.waitForIdleSync()}
    private class Fake(val immediate:Boolean=true):SpeechBackend{
        override val source="VALIDATION_CALLBACK_FIXTURE";var initialized=0;var requested=0;var stopped=0;var detached=0;var released=0;var active=0;var maxActive=0
        lateinit var ready:()->Unit;lateinit var events:SpeechBackend.Events
        override fun initialize(ready:()->Unit,failed:(String)->Unit){initialized++;this.ready=ready;if(immediate)ready()}
        override fun listen(events:SpeechBackend.Events){check(active==0);active++;maxActive=maxOf(active,maxActive);requested++;this.events=events}
        override fun finish(){stopped++}
        override fun detach(){if(active>0){active--;detached++}}
        override fun release(){released++}
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val engines=mutableListOf<Fake>();var results=0;var allowed=true
        val speech=main{SpeechInput(c,factory={Fake().also{engines.add(it)}},allowed={allowed})}
        fun start(){main{speech.listen({},{results++})};drain()}
        start();val first=engines.single()
        verify(first.initialized==1&&first.requested==1,"AS01 initialization precedes one request")
        main{first.events.ready()};drain();verify(speech.state.ready&&speech.state.message.contains("지금 짧게 말씀"),"AS02 ready prompts speech without fabricated started")
        main{speech.listen({},{results++})};drain();verify(first.requested==1,"AS03 duplicate start rejected")
        main{first.events.started();first.events.updated("첫 문장",false)};drain()
        verify(speech.state.phase==SpeechPhase.LISTENING&&speech.state.text=="첫 문장"&&results==0,"AS04 partial displayed but not delivered as final")
        verify(speech.state.message.contains("자동으로 종료"),"AS05 recording guidance explains automatic completion")
        val old=first.events
        main{first.events.updated("첫 문장",true)};drain()
        verify(speech.busy&&speech.state.phase==SpeechPhase.PROCESSING&&speech.state.microphone==MicrophoneState.ENDING,"AS06 final alone waits for actual end")
        verify(first.stopped==1&&first.released==0&&results==0,"AS07 automatic stop once; keep service until end")
        main{first.events.updated("중복 최종문",true)};drain();verify(first.stopped==1,"AS08 duplicate final cannot duplicate stop")
        main{first.events.ended()};drain()
        verify(!speech.busy&&speech.state.phase==SpeechPhase.COMPLETE&&results==1,"AS09 final plus end completes once")
        verify(first.active==0&&first.detached==1&&first.released==0,"AS10 exact listener detached; initialized connection retained")
        repeat(2){index->start();main{old.updated("이전 세션 문장",true);first.events.started();first.events.updated("다음 문장 $index",true);first.events.ended()};drain()}
        verify(results==3&&first.requested==3&&first.initialized==1,"AS11 three automatic sessions reuse one initialized SDK")
        verify(first.maxActive==1&&first.active==0&&first.detached==3,"AS12 no duplicate listener or listener leak")
        start();main{first.events.started();first.events.ended()};drain()
        verify(speech.busy&&speech.state.microphone==MicrophoneState.ENDED,"AS13 end before final stays pending")
        val stops=first.stopped;main{first.events.updated("종료 뒤 최종문",true)};drain()
        verify(results==4&&!speech.busy&&first.stopped==stops+1,"AS14 late final accepted; stop still requested")
        start();main{first.events.started();first.events.error("STT_FIXTURE_ERROR")};drain()
        verify(speech.state.phase==SpeechPhase.FAILED&&speech.busy&&results==4,"AS15 provider error cannot fabricate end or success")
        main{first.events.ended()};drain();verify(!speech.busy&&first.released==1,"AS16 failed session ends and releases")
        start();val second=engines.last();verify(engines.size==2&&second.initialized==1,"AS17 error retry reinitializes")
        val stale=second.events;main{second.events.started();speech.stop();stale.updated("遅い結果",true);stale.ended()};drain()
        verify(!speech.busy&&results==4&&second.active==0&&second.released==1,"AS18 screen exit invalidates callbacks and releases")
        allowed=false;start();verify(engines.size==2&&!speech.busy,"AS19 restricted or unknown cannot start")
        allowed=true;start();verify(engines.size==3,"AS20 foreground restart reconnects");main{speech.stop()}

        val slow=Fake(false);var cancelledResults=0
        val preparing=main{SpeechInput(c,factory={slow},allowed={true})}
        main{preparing.listen({},{cancelledResults++});preparing.cancel();slow.ready()};drain()
        verify(slow.requested==0&&slow.released==1,"AS21 cancel initialization blocks late request")
        verify(preparing.state.phase==SpeechPhase.CANCELLED&&!preparing.busy&&cancelledResults==0,"AS22 cancellation is not failure or recognition success")
        main{preparing.stop()}
        val cancelledEngines=mutableListOf<Fake>();var originalDraft="기존 작성 내용"
        val cancelled=main{SpeechInput(c,factory={Fake().also{cancelledEngines.add(it)}},allowed={true})}
        main{cancelled.listen({},{originalDraft=it;cancelledResults++})};drain();val cancelledFirst=cancelledEngines.single();val queued=cancelledFirst.events
        main{queued.started();queued.updated("취소할 중간 문장",false);cancelled.cancel();cancelled.cancel()};drain()
        verify(cancelledFirst.stopped==1&&cancelledFirst.detached==1&&cancelledFirst.released==1,"AS23 cancel stops detaches releases exactly once")
        verify(cancelled.state.phase==SpeechPhase.CANCELLED&&cancelled.state.microphone==MicrophoneState.UNCONFIRMED&&cancelled.completions==0,"AS24 cancel never fabricates SDK ended or completion")
        main{queued.updated("취소 뒤 늦은 문장",true);queued.ended()};drain()
        verify(originalDraft=="기존 작성 내용"&&cancelledResults==0&&cancelled.state.text.isEmpty(),"AS25 cancelled and stale results cannot overwrite original draft")
        main{cancelled.listen({},{originalDraft=it;cancelledResults++})};drain();val retried=cancelledEngines.last()
        verify(cancelledEngines.size==2&&retried.initialized==1&&retried.requested==1,"AS26 retry after cancellation initializes a new service")
        main{retried.events.started();retried.events.updated("새 확정 문장",true);retried.events.ended()};drain()
        verify(cancelledResults==1&&cancelled.completions==1&&originalDraft=="새 확정 문장","AS27 actual final plus end completes after cancel retry")
        main{cancelled.cancel()};verify(cancelled.state.phase==SpeechPhase.COMPLETE&&cancelledResults==1,"AS28 cancel after completion cannot discard accepted result");main{cancelled.stop()}

        val missing=Fake();val timeout=main{SpeechInput(c,factory={missing},recognitionTimeoutMs=100,endTimeoutMs=100,finalTimeoutMs=100,allowed={true})}
        main{timeout.listen({},{results++})};Thread.sleep(450);drain()
        verify(!timeout.busy&&timeout.state.phase==SpeechPhase.FAILED&&timeout.state.microphone==MicrophoneState.UNCONFIRMED,"AS29 missing automatic end times out as failure, not cancellation or success")
        verify(missing.released==1&&missing.active==0&&results==4,"AS30 timeout frees listener without fabricated result");main{timeout.stop()}
        val noFinal=Fake();val late=main{SpeechInput(c,factory={noFinal},finalTimeoutMs=100,allowed={true})}
        main{late.listen({},{results++})};drain();main{noFinal.events.started();noFinal.events.ended()};Thread.sleep(250);drain()
        verify(!late.busy&&late.state.code=="STT_FINAL_TIMEOUT"&&results==4,"AS31 ended without final never promotes partial to final");main{late.stop()}

        val fake=Fake();lateinit var controls:SpeechControls;var uiResults=0
        val panel=main{SpeechInput(c,factory={fake},allowed={true})}
        main{controls=SpeechControls(c,CaritdaComponents(c,CaritdaPalette.of(false)),panel,{true}){panel.listen({},{uiResults++})};panel.listen({},{uiResults++})};drain()
        verify(main{controls.findViewWithTag<Button>("voice_stop")==null&&controls.findViewWithTag<Button>("voice_cancel").text.contains("취소")},"AS32 manual finish button removed; explicit cancel provided")
        main{fake.events.started();fake.events.updated("인식 중인 문장",false);fake.events.level(0.0)};drain()
        verify(main{controls.findViewWithTag<TextView>("speech_level").text.contains("0.000000")},"AS33 real zero RMS distinguished from missing")
        verify(main{controls.findViewWithTag<TextView>("speech_state").text.contains("음성 인식 중")&&controls.findViewWithTag<TextView>("speech_partial").text.contains("인식 중인 문장")},"AS34 recording and partial text visible")
        verify(main{!controls.findViewWithTag<Button>("voice_symptom").isEnabled&&controls.findViewWithTag<Button>("voice_cancel").isEnabled},"AS35 prevent duplicate start and allow cancel")
        verify(main{controls.findViewWithTag<Button>("voice_cancel").currentTextColor==CaritdaPalette.of(false).text},"AS36 cancel contrast updates when enabled")
        main{fake.events.updated("완료 대기 문장",true)};drain()
        verify(main{controls.findViewWithTag<TextView>("speech_state").text.contains("자동 인식")&&panel.busy&&uiResults==0},"AS37 automatic final pending actual ended is distinct from complete")
        main{controls.findViewWithTag<Button>("voice_cancel").performClick()};drain()
        verify(main{controls.findViewWithTag<TextView>("speech_state").text.contains("취소")&&controls.findViewWithTag<Button>("voice_symptom").text.contains("다시")&&controls.findViewWithTag<Button>("voice_symptom").isEnabled&&!controls.findViewWithTag<Button>("voice_cancel").isEnabled&&uiResults==0},"AS38 real cancel button releases pending result and enables retry")
        main{panel.stop()}
        fun luminance(color:Int):Double{
            fun channel(v:Int):Double{val n=v/255.0;return if(n<=0.04045)n/12.92 else Math.pow((n+0.055)/1.055,2.4)}
            return 0.2126*channel(android.graphics.Color.red(color))+0.7152*channel(android.graphics.Color.green(color))+0.0722*channel(android.graphics.Color.blue(color))
        }
        for(dark in listOf(false,true)){val p=CaritdaPalette.of(dark);val a=luminance(p.text);val b=luminance(p.background);verify((maxOf(a,b)+0.05)/(minOf(a,b)+0.05)>=4.5,"AS${if(dark)40 else 39} text/background contrast")}
        val offline=main{android.os.Build.VERSION.SDK_INT>=31&&android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(c)}
        val micMuted=(c.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager).isMicrophoneMute
        lines.add("RUNTIME ANDROID_ON_DEVICE_AVAILABLE=$offline MICROPHONE_MUTED=$micMuted; actual microphone ASR is a separate test")
        return lines
    }
}
