package com.nextdrive.pleos.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.*
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.stt.listener.OnInitializeListener
import java.util.Locale

interface SpeechBackend{
    val source:String
    interface Events{fun ready();fun started();fun updated(text:String,complete:Boolean);fun ended();fun error(code:String);fun level(value:Double){};fun captureLevel(value:Double){};fun epd(){} }
    fun initialize(ready:()->Unit,failed:(String)->Unit)
    fun listen(events:Events)
    fun finish()
    fun detach()
    fun release()
}
class PleosSpeechBackend(context:Context):SpeechBackend{
    override val source="PLEOS_ON_DEVICE"
    private val sdk=SpeechToText(context.applicationContext,Mode.ON_DEVICE)
    private var listener:ResultListener?=null
    override fun initialize(ready:()->Unit,failed:(String)->Unit){sdk.initialize(object:OnInitializeListener{
        override fun onSuccess(){ready()}
        override fun onFailure(errorType:ErrorType){failed("STT_INIT_"+errorType.name)}
    })}
    override fun listen(events:SpeechBackend.Events){
        check(listener==null){"STT_LISTENER_ALREADY_REGISTERED"}
        val current=object:ResultListener{
            override fun onReady(){events.ready()}
            override fun onStartedRecognition(){events.started()}
            override fun onUpdated(stt:String,completed:Boolean){events.updated(stt,completed)}
            override fun onEndedRecognition(){events.ended()}
            override fun onError(errorType:ErrorType){events.error("STT_"+errorType.name)}
            override fun onUpdatedEpdData(on:Long,off:Long){events.epd()}
            override fun onUpdatedRms(rms:Double){events.level(rms)}
        }
        listener=current;sdk.addListener(current);sdk.setLocale(Locale.KOREA);sdk.request()
    }
    override fun finish(){sdk.stop()}
    override fun detach(){listener?.let{sdk.removeListener(it)};listener=null}
    override fun release(){detach();sdk.release()}
}
class AndroidSpeechBackend(private val context:Context):SpeechBackend{
    override val source="ANDROID_ON_DEVICE";private var recognizer:SpeechRecognizer?=null
    companion object{fun available(c:Context)=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(c)}
    override fun initialize(ready:()->Unit,failed:(String)->Unit){if(!available(context)){failed("STT_OFFLINE_UNAVAILABLE");return};if(Build.VERSION.SDK_INT>=31){recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);ready()}}
    override fun listen(events:SpeechBackend.Events){
        recognizer!!.setRecognitionListener(object:RecognitionListener{
            override fun onReadyForSpeech(params:Bundle?){events.ready();events.started()}
            override fun onBeginningOfSpeech(){}
            override fun onRmsChanged(rms:Float){}
            override fun onBufferReceived(buffer:ByteArray?){}
            override fun onEndOfSpeech(){events.ended()}
            override fun onError(error:Int){events.error("STT_ANDROID_"+error)}
            override fun onResults(results:Bundle?){events.updated(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(),true)}
            override fun onPartialResults(results:Bundle?){events.updated(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(),false)}
            override fun onEvent(type:Int,params:Bundle?){}
        })
        recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true))
    }
    override fun finish(){recognizer?.stopListening()}
    override fun detach(){}
    override fun release(){recognizer?.cancel();recognizer?.destroy();recognizer=null}
}
