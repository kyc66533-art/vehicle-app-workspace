package com.nextdrive.pleos

import android.Manifest
import android.app.Instrumentation
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import com.nextdrive.pleos.integration.AndroidSpeechBackend
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.voice.HomeWakeDetectorFactory
import java.util.UUID

/** Public, read-only platform queries and a one-second foreground PCM probe; no audio saved. */
class HomeVoicePlatformChecks(private val inst:Instrumentation){
    fun run():List<String>{
        val ctx=inst.targetContext;ValidationEnvironment.requireIsolated(ctx);val h=BLightHarness(inst);val lines=mutableListOf<String>()
        fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
        val prefix="gleo-ui-"+UUID.randomUUID();val a=inst.startActivitySync(Intent(ctx,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var recorder:AudioRecord?=null
        try{
            val end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end&&h.main{(h.get(a,"safety") as CarSafetyGate).state!=EditingState.ALLOWED})Thread.sleep(70)
            ck(h.main{(h.get(a,"safety") as CarSafetyGate).state==EditingState.ALLOWED},"actual parked gate permits foreground microphone probe")
            val granted=ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
            ck(granted,"validation microphone runtime permission is granted")
            lines.add("PLATFORM android_on_device_recognizer=${h.main{AndroidSpeechBackend.available(ctx)}}")
            val role=ctx.getSystemService(RoleManager::class.java);lines.add("PLATFORM assistant_available=${role.isRoleAvailable(RoleManager.ROLE_ASSISTANT)} held=${role.isRoleHeld(RoleManager.ROLE_ASSISTANT)}")
            val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            ck(min>0,"public AudioRecord 16kHz mono PCM is supported")
            recorder=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,min.coerceAtLeast(32000))
            ck(recorder.state==AudioRecord.STATE_INITIALIZED,"actual AudioRecord initialized in visible app")
            recorder.startRecording();ck(recorder.recordingState==AudioRecord.RECORDSTATE_RECORDING,"actual microphone recording state acknowledged")
            val buffer=ShortArray(16000);val read=recorder.read(buffer,0,buffer.size)
            ck(read>0,"actual PCM input returned samples without storing raw audio")
            lines.add("AUDIO samples=$read nonzero=${buffer.take(read.coerceAtLeast(0)).count{it.toInt()!=0}} raw_saved=false speech_recognition=NOT_TESTED")
            recorder.stop();recorder.release();recorder=null
            val detector=h.main{HomeWakeDetectorFactory.create(a)}
            if(detector!=null){
                var failure:String?=null;var matched=false
                try{
                    h.main{detector.start({(h.get(a,"safety") as CarSafetyGate).state==EditingState.ALLOWED},{},{matched=true},{failure=it})}
                    val ready=System.currentTimeMillis()+180000
                    while(System.currentTimeMillis()<ready&&!detector.microphoneActive&&failure==null)Thread.sleep(100)
                    ck(detector.microphoneActive&&failure==null,"actual offline Korean model and native x86_64 microphone stream started")
                    val captureEnd=System.currentTimeMillis()+30000
                    while(System.currentTimeMillis()<captureEnd&&detector.samples<32000&&failure==null&&!matched)Thread.sleep(100)
                    ck(detector.samples>=32000||matched,"offline wake PCM samples=${detector.samples} matched=$matched failure=$failure")
                    lines.add("AUDIO wake_source=${detector.source} samples=${detector.samples} cpu_ns_including_setup=${detector.cpuNanos} raw_saved=false")
                    val released=java.util.concurrent.CountDownLatch(1)
                    h.main{detector.stop{released.countDown()}}
                    ck(released.await(15,java.util.concurrent.TimeUnit.SECONDS)&&!detector.microphoneActive&&!detector.busy,"offline detector releases recorder before completion")
                }finally{h.main{detector.close()}}
            }else lines.add("PLATFORM independent_engine=NOT_PACKAGED bounded_stt_fallback=true")
            val wake=ai.pleos.playground.stt.api.AmbientAiApi::class.java.methods.first{it.name=="setWakeUpWordConfig"}
            ck(wake.annotations.any{it.annotationClass.java.simpleName=="PrivilegedApi"},"AAR marks wake configuration privileged; it was not invoked")
            lines.add("BOUNDARIES independent_keyword_match=NOT_TESTED actual_user_utterance=NOT_TESTED external_gleo=NOT_TESTED ptt=NOT_TESTED")
        }finally{runCatching{recorder?.stop()};recorder?.release();h.main{a.finish()}}
        return lines
    }
}
