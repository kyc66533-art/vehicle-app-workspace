package com.nextdrive.pleos

import android.app.Instrumentation
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.voice.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real SDK/TTS, no transcript injection and no paid model. Unique observations only. */
class VoiceAutomationLiveChecks(private val inst:Instrumentation){
    private val ctx=inst.targetContext;private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(action:()->T):T {var value:T?=null;var error:Throwable?=null;inst.runOnMainSync{try{value=action()}catch(t:Throwable){error=t}};error?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(ctx)
        val raw=ObservationStore(ctx,"voice-live-${UUID.randomUUID()}.db");var gate:CarSafetyGate?=null;var observer:PleosAttentionObserver?=null;var speaker:SpeechOutput?=null
        val safe=CountDownLatch(1);val reads=CountDownLatch(4);val seen=mutableSetOf<String>()
        try{
            main{gate=CarSafetyGate(ctx){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()}
            ck(safe.await(12,TimeUnit.SECONDS),"real parked CarSafetyGate allowed")
            main{observer=PleosAttentionObserver(ctx,raw,{gate!!.state==EditingState.ALLOWED},{r->if(seen.add(r.value.kind))reads.countDown()});observer!!.start()}
            ck(reads.await(22,TimeUnit.SECONDS),"four real Safety capability/GET attempts reached terminal outcomes")
            val rows=raw.latest(100)
            ck(rows.all{it.source==ObservationSource.PLEOS_EMULATOR_SDK},"Safety outcomes have actual emulator SDK provenance")
            rows.sortedBy{it.kind}.forEach{lines.add("SDK ${it.kind} ${it.status} raw=${it.raw?:"-"} detail=${it.detail}")}
            main{lines.add("SDK registrations=${observer!!.registered.sorted().joinToString(",")} actual_callbacks=${observer!!.callbackCounts.values.sum()}");observer!!.stop()}
            ck(main{!observer!!.running&&observer!!.registered.isEmpty()},"all Safety listeners unregistered on stop")
            val done=CountDownLatch(1);var outcome:PlaybackOutcome?=null;var text=""
            main{speaker=SpeechOutput(ctx){gate!!.state==EditingState.ALLOWED};val playback=WholeExplanationPlayback({gate!!.state==EditingState.ALLOWED},{part,cb->text+=part;speaker!!.speakOutcome(part,cb){}},{speaker!!.stop()});playback.play("배터리 안내와 음성 대화의 연결을 확인하고 있어요. 이 안내는 고장 진단이 아니에요."){outcome=it;done.countDown()}}
            ck(done.await(35,TimeUnit.SECONDS)&&outcome==PlaybackOutcome.DONE,"real full on-device TTS DONE callback")
            ck(text.contains("배터리 안내")&&main{speaker!!.started},"entire prepared explanation delivered to actual speech engine")
            val cancelled=CountDownLatch(1);var cancellation:PlaybackOutcome?=null
            main{speaker!!.speakOutcome("이 음성은 중단 상태를 확인하는 검증 안내예요.",{cancellation=it;cancelled.countDown()}){};speaker!!.stop()}
            ck(cancelled.await(5,TimeUnit.SECONDS)&&cancellation==PlaybackOutcome.CANCELLED,"real TTS stop returns typed CANCELLED")
            if(Build.VERSION.SDK_INT>=29){val role=ctx.getSystemService(RoleManager::class.java);lines.add("PLATFORM assistant_role_available=${role.isRoleAvailable(RoleManager.ROLE_ASSISTANT)} assistant_role_held=${role.isRoleHeld(RoleManager.ROLE_ASSISTANT)}")}
            lines.add("PLATFORM microphone_granted=${ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED} system_hotword=false external_gleo=UNVERIFIED real_vehicle=UNVERIFIED real_model_calls=0")
            return lines
        } finally {main{speaker?.stop();observer?.stop();gate?.stop()};raw.close()}
    }
}
