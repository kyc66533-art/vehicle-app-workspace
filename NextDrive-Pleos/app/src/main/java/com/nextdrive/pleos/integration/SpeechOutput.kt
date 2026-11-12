package com.nextdrive.pleos.integration

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech as AndroidTts
import android.speech.tts.UtteranceProgressListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

/** On-device speech. Only actual engine onDone is DONE; timers only fail. */
class SpeechOutput(private val context:Context,private val allowed:()->Boolean){
    private val main=Handler(Looper.getMainLooper())
    private val audio=context.getSystemService(AudioManager::class.java)
    private var focus:AudioFocusRequest?=null
    private var sdk:TextToSpeech?=null
    private var sdkListener:EventListener?=null
    private var android:AndroidTts?=null
    private var generation=0
    private var terminal:((PlaybackOutcome)->Unit)?=null
    private var report:(String)->Unit={}
    var busy=false;private set
    var source="";private set
    var started=false;private set
    fun speak(text:String,changed:(String)->Unit)=speak(text,{},changed)
    fun speak(text:String,completed:(Boolean)->Unit,changed:(String)->Unit)=speakOutcome(text,{completed(it==PlaybackOutcome.DONE)},changed)
    fun speakOutcome(text:String,completed:(PlaybackOutcome)->Unit,changed:(String)->Unit){
        check(Looper.myLooper()==Looper.getMainLooper())
        if(busy||!allowed()||text.isBlank()||text.length>600||!VoiceAudioOwner.claim(this)){completed(PlaybackOutcome.REJECTED);return}
        generation++;val run=generation;busy=true;started=false;report=changed;terminal=completed
        fun post(action:()->Unit){main.post{if(run==generation&&busy){if(allowed())action()else finish(PlaybackOutcome.CANCELLED)}}}
        val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false).setOnAudioFocusChangeListener({change->if(change<0)post{finish(PlaybackOutcome.CANCELLED)}},main).build()
        focus=request
        if(runCatching{audio.requestAudioFocus(request)}.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){finish(PlaybackOutcome.REJECTED);return}
        fun androidFallback(){
            releaseSdk(false);source="ANDROID_ON_DEVICE"
            try{android=AndroidTts(context.applicationContext){status->post{
                val engine=android
                val voice=engine?.voices?.firstOrNull{it.locale.language=="ko"&&!it.isNetworkConnectionRequired}
                if(status!=AndroidTts.SUCCESS||engine==null||voice==null){finish(PlaybackOutcome.FAILED);return@post}
                engine.voice=voice;engine.setAudioAttributes(attributes)
                engine.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                    override fun onStart(id:String?){post{started=true;report("음성으로 안내하고 있어요.")}}
                    override fun onDone(id:String?){post{finish(PlaybackOutcome.DONE)}}
                    @Deprecated("Platform callback") override fun onError(id:String?){post{finish(PlaybackOutcome.FAILED)}}
                    override fun onStop(id:String?,interrupted:Boolean){post{finish(PlaybackOutcome.CANCELLED)}}
                })
                if(engine.speak(text,AndroidTts.QUEUE_FLUSH,null,"caritda-$run")==AndroidTts.ERROR)finish(PlaybackOutcome.FAILED)
            }}}catch(_:Exception){finish(PlaybackOutcome.FAILED)}
        }
        source="PLEOS_ON_DEVICE"
        val listener=object:EventListener{
            override fun onStart(){post{started=true;report("음성으로 안내하고 있어요.")}}
            override fun onReady(){}
            override fun onDone(){post{finish(PlaybackOutcome.DONE)}}
            override fun onError(errorType:ErrorType){post{finish(PlaybackOutcome.FAILED)}}
            override fun onStop(){post{finish(PlaybackOutcome.CANCELLED)}}
            override fun onUpdatedRms(value:Double){}
        }
        try{
            val t=TextToSpeech(context.applicationContext,Mode.ON_DEVICE);sdk=t
            t.initialize(object:OnInitializeListener{
                override fun onSuccess(){post{try{t.addEventListener(listener);sdkListener=listener;t.setLocale(Locale.KOREA);t.speak(text,true)}catch(_:Exception){finish(PlaybackOutcome.FAILED)}}}
                override fun onFailure(errorType:ErrorType){post{androidFallback()}}
            })
        }catch(_:Exception){androidFallback()}catch(_:LinkageError){androidFallback()}
        main.postDelayed({if(run==generation&&busy)finish(PlaybackOutcome.FAILED)},20000)
    }
    private fun finish(outcome:PlaybackOutcome){
        if(!busy)return
        val callback=terminal;terminal=null;val changed=report;report={};busy=false;generation++;main.removeCallbacksAndMessages(null)
        releaseSdk(outcome!=PlaybackOutcome.DONE)
        val engine=android;android=null
        if(outcome!=PlaybackOutcome.DONE)runCatching{engine?.stop()};runCatching{engine?.shutdown()}
        focus?.let{runCatching{audio.abandonAudioFocusRequest(it)}};focus=null;VoiceAudioOwner.release(this)
        changed(when(outcome){PlaybackOutcome.DONE->"음성 안내를 마쳤어요.";PlaybackOutcome.CANCELLED->"음성 안내를 멈췄어요.";PlaybackOutcome.FAILED->"음성 안내를 완료하지 못했어요.";PlaybackOutcome.REJECTED->"다른 음성 서비스가 사용 중이거나 음성을 사용할 수 없어요."})
        callback?.invoke(outcome)
    }
    private fun releaseSdk(interrupt:Boolean){
        val engine=sdk;sdk=null;val listener=sdkListener;sdkListener=null
        if(engine!=null){listener?.let{runCatching{engine.removeEventListener(it)}};if(interrupt)runCatching{engine.stop()};runCatching{engine.release()}}
    }
    fun stop(){if(busy)finish(PlaybackOutcome.CANCELLED)else{generation++;main.removeCallbacksAndMessages(null);releaseSdk(false);VoiceAudioOwner.release(this)}}
}
