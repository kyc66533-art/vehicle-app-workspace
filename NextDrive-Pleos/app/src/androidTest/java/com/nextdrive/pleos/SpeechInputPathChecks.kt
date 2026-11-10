package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.media.*
import com.nextdrive.pleos.safety.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual, foreground, permitted capture-level check. No stored PCM/transcript/model requests. */
class SpeechInputPathChecks(private val inst:Instrumentation){
    fun run():List<String>{
        val c=inst.targetContext;ValidationEnvironment.requireIsolated(c)
        val activity=inst.startActivitySync(Intent().setClassName(c,"com.nextdrive.pleos.IntegrationDiagnosticActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val lines=mutableListOf<String>();var gate:CarSafetyGate?=null;val safe=CountDownLatch(1)
        inst.runOnMainSync{gate=CarSafetyGate(activity){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()}
        try{
            check(safe.await(10,TimeUnit.SECONDS)){"INPUT_PATH_SAFETY_UNCONFIRMED"};lines.add("PASS IP01 actual foreground parked safety allowed")
            check(activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED){"INPUT_PATH_PERMISSION_REQUIRED"};lines.add("PASS IP02 actual microphone permission granted")
            val audio=c.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            lines.add("RUNTIME AUDIO_INPUT_DEVICE_TYPES="+audio.getDevices(AudioManager.GET_DEVICES_INPUTS).map{it.type}.distinct().joinToString(",")+" MICROPHONE_MUTED="+audio.isMicrophoneMute)
            for((source,label) in listOf(MediaRecorder.AudioSource.MIC to "MIC",MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")){
                val done=CountDownLatch(1);var probe:MicrophoneProbe?=null;var code="PROBE_TIMEOUT"
                inst.runOnMainSync{probe=MicrophoneProbe(activity){gate?.state==EditingState.ALLOWED};probe!!.start(source){code=it;done.countDown()}}
                check(done.await(8,TimeUnit.SECONDS)){"Input probe did not terminate"}
                lines.add("RUNTIME INPUT_PATH source=$label code=$code initialized="+probe!!.lastInitialized+" recording="+probe!!.lastRecording+" samples="+probe!!.lastSamples+" bytes="+(probe!!.lastSamples*2)+" min_read="+probe!!.lastMinRead+" max_read="+probe!!.lastMaxRead+" read_error="+probe!!.lastReadError+" rms="+probe!!.lastRms+" peak_s16="+probe!!.lastPeak+" client_silenced="+probe!!.lastSilenced+" routed_device_type="+probe!!.lastInputType)
                inst.runOnMainSync{probe!!.stop()}
            }
            lines.add("PASS IP03 both bounded captures stopped; PCM discarded/no API call")
        }finally{inst.runOnMainSync{gate?.stop();activity.finish()}}
        return lines
    }
}
