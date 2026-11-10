package com.nextdrive.pleos.integration

import android.content.Context
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

/** Explicit, short on-device playback. Never registers a cloud app or repeats on redraw. */
class SpeechOutput(private val context:Context,private val allowed:()->Boolean){
    private val main=Handler(Looper.getMainLooper())
    private var sdk:TextToSpeech?=null
    private var android:AndroidTts?=null
    private var generation=0
    var busy=false;private set
    var source="";private set
    var started=false;private set
    private var report:(String)->Unit={}
    fun speak(text:String,changed:(String)->Unit){
        if(busy||!allowed())return
        require(text.isNotBlank()&&text.length<=600)
        stop();busy=true;started=false;report=changed
        val run=generation;val phrase=text
        fun post(action:()->Unit){main.post{if(run==generation&&allowed())action()}}
        fun complete(message:String){busy=false;report(message)}
        fun androidFallback(){
            runCatching{sdk?.release()};sdk=null;source="ANDROID_ON_DEVICE"
            try{android=AndroidTts(context.applicationContext){status->post{
                val engine=android
                val voice=engine?.voices?.firstOrNull{it.locale.language=="ko"&&!it.isNetworkConnectionRequired}
                if(status!=AndroidTts.SUCCESS||engine==null||voice==null){complete("한국어 기기 내 음성을 사용할 수 없어요. 화면 내용은 그대로 확인할 수 있어요.");return@post}
                engine.voice=voice
                engine.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                    override fun onStart(id:String?){post{started=true;report("음성으로 안내하고 있어요.")}}
                    override fun onDone(id:String?){post{complete("음성 안내를 마쳤어요.")}}
                    @Deprecated("Platform callback")override fun onError(id:String?){post{complete("음성을 재생하지 못했어요.")}}
                })
                if(engine.speak(phrase,AndroidTts.QUEUE_FLUSH,null,"caritda-$run")==AndroidTts.ERROR)complete("음성을 재생하지 못했어요.")
            }}}catch(_:Exception){complete("음성 서비스에 연결하지 못했어요.")}
        }
        source="PLEOS_ON_DEVICE"
        val listener=object:EventListener{
            override fun onStart(){post{started=true;report("음성으로 안내하고 있어요.")}}
            override fun onReady(){}
            override fun onDone(){post{complete("음성 안내를 마쳤어요.")}}
            override fun onError(errorType:ErrorType){post{complete("음성 안내를 완료하지 못했어요. 확인 코드: "+errorType.name)}}
            override fun onStop(){post{complete("음성 안내를 멈췄어요.")}}
            override fun onUpdatedRms(value:Double){}
        }
        try{
            val t=TextToSpeech(context.applicationContext,Mode.ON_DEVICE);sdk=t
            t.initialize(object:OnInitializeListener{
                override fun onSuccess(){post{try{t.addEventListener(listener);t.setLocale(Locale.KOREA);t.speak(phrase,true)}catch(_:Exception){complete("음성 요청을 보내지 못했어요.")}}}
                override fun onFailure(errorType:ErrorType){post{androidFallback()}}
            })
        }catch(_:Exception){androidFallback()}catch(_:LinkageError){androidFallback()}
        main.postDelayed({if(run==generation&&busy){stop();changed("음성 서비스 응답이 늦어 중단했어요.")}},20000)
    }
    fun stop(){generation++;main.removeCallbacksAndMessages(null);runCatching{sdk?.stop();sdk?.release()};sdk=null;runCatching{android?.stop();android?.shutdown()};android=null;busy=false}
}
