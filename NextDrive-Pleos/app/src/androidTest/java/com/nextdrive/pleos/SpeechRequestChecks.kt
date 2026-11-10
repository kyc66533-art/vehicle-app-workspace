package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import com.nextdrive.pleos.safety.*
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/** A-only direct SDK request/stop diagnosis, independent of SpeechInput/AudioRecord/sendAudio.
 * A bounded quiet probe is not a speech-recognition success test. No PCM/text persisted. */
class SpeechRequestChecks(private val inst:Instrumentation){
    fun run():List<String>{
        val c=inst.targetContext;ValidationEnvironment.requireIsolated(c)
        val activity=inst.startActivitySync(Intent().setClassName(c,"com.nextdrive.pleos.IntegrationDiagnosticActivity").putExtra("use_app_mic",false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val events=CopyOnWriteArrayList<String>();var gate:CarSafetyGate?=null;var sdk:SpeechToText?=null
        val safe=CountDownLatch(1);val initialized=CountDownLatch(1);val ready=CountDownLatch(1);val ended=CountDownLatch(1)
        val levels=CopyOnWriteArrayList<Double>();val finalLengths=CopyOnWriteArrayList<Int>();val errors=CopyOnWriteArrayList<String>()
        val listener=object:ResultListener{
            override fun onReady(){events.add("READY");ready.countDown()}
            override fun onStartedRecognition(){events.add("STARTED")}
            override fun onUpdated(stt:String,completed:Boolean){events.add(if(completed)"FINAL"else"PARTIAL");if(completed)finalLengths.add(stt.length)}
            override fun onEndedRecognition(){events.add("ENDED");ended.countDown()}
            override fun onUpdatedRms(rms:Double){if(rms.isFinite())levels.add(rms)}
            override fun onUpdatedEpdData(on:Long,off:Long){events.add("EPD")}
            override fun onError(errorType:ErrorType){errors.add(errorType.name);events.add("SDK_ERROR")}
        }
        try{
            inst.runOnMainSync{gate=CarSafetyGate(activity){if(it==EditingState.ALLOWED)safe.countDown()else if(sdk!=null){events.add("SAFETY_STOP");sdk?.stop()}};gate!!.start()}
            check(safe.await(10,TimeUnit.SECONDS)){"REQUEST_SAFETY_UNCONFIRMED"}
            check(activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED){"REQUEST_PERMISSION_REQUIRED"}
            inst.runOnMainSync{sdk=SpeechToText(c,Mode.ON_DEVICE);sdk!!.initialize(object:OnInitializeListener{
                override fun onSuccess(){events.add("INITIALIZED");initialized.countDown()}
                override fun onFailure(errorType:ErrorType){errors.add("INIT_"+errorType.name);initialized.countDown()}
            })}
            check(initialized.await(8,TimeUnit.SECONDS)&&events.contains("INITIALIZED")){"REQUEST_INITIALIZATION_FAILED"}
            inst.runOnMainSync{check(gate?.state==EditingState.ALLOWED){"REQUEST_SAFETY_CHANGED"};sdk!!.addListener(listener);events.add("LISTENER_ADDED");sdk!!.setLocale(Locale.KOREA);events.add("REQUEST");sdk!!.request()}
            ready.await(3,TimeUnit.SECONDS);Thread.sleep(5000)
            inst.runOnMainSync{events.add("STOP");sdk!!.stop()}
            ended.await(5,TimeUnit.SECONDS)
            return listOf("REQUEST_DIAGNOSTIC mode=ON_DEVICE locale=ko-KR sendAudio=false events="+events.take(40).joinToString(",")+
                " rms_callbacks="+levels.size+" rms_min="+levels.minOrNull()+" rms_max="+levels.maxOrNull()+
                " final_received="+finalLengths.any{it>0}+" ended="+(ended.count==0L)+" sdk_errors="+errors.joinToString(",").ifEmpty{"NONE"}+
                " no_speech_success_claim=true")
        }finally{inst.runOnMainSync{sdk?.removeListener(listener);sdk?.release();gate?.stop();activity.finish()}}
    }
}
