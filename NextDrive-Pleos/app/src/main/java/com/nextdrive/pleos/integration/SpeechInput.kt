package com.nextdrive.pleos.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

enum class SpeechPhase { IDLE, PREPARING, LISTENING, PROCESSING, COMPLETE, CANCELLED, FAILED }
enum class MicrophoneState { NOT_STARTED, ACTIVE, ENDING, ENDED, UNCONFIRMED }
data class SpeechState(val phase:SpeechPhase=SpeechPhase.IDLE,val microphone:MicrophoneState=MicrophoneState.NOT_STARTED,
    val text:String="",val finalReceived:Boolean=false,val busy:Boolean=false,val code:String="",val ready:Boolean=false,val level:Double?=null,val levelSource:String="SDK"){
    val title get()=when(phase){SpeechPhase.IDLE->"대기 중";SpeechPhase.PREPARING->"음성 입력 준비 중";SpeechPhase.LISTENING->"● 음성 인식 중";SpeechPhase.PROCESSING->"자동 인식을 마무리하고 있어요";SpeechPhase.COMPLETE->"인식 완료";SpeechPhase.CANCELLED->"음성 입력 취소";SpeechPhase.FAILED->"인식 실패"}
    val message get()=title+" · "+when{
        phase==SpeechPhase.CANCELLED->"이번 음성 입력을 반영하지 않았어요. 다시 입력하거나 직접 적어 주세요."
        microphone==MicrophoneState.UNCONFIRMED->"종료를 확인하지 못해 서비스를 해제했어요. 다시 입력하면 새로 연결해요."
        microphone==MicrophoneState.ENDING->"자동 인식의 종료 신호를 확인하고 있어요. 잠시 기다려 주세요."
        microphone==MicrophoneState.ACTIVE->"짧게 말씀해 주세요. 말을 마치면 자동으로 종료돼요."
        phase==SpeechPhase.PROCESSING->"최종 문장을 기다리고 있어요. 잠시 기다려 주세요."
        phase==SpeechPhase.COMPLETE->"자동 인식이 끝났어요. 내용을 확인하거나 다시 입력해 주세요."
        phase==SpeechPhase.FAILED->"기존 입력은 유지돼요. 다시 입력하거나 직접 적어 주세요."
        phase==SpeechPhase.PREPARING&&ready->"기기가 준비됐어요. 지금 짧게 말씀해 주세요. 음성 시작 신호를 기다려요."
        phase==SpeechPhase.PREPARING->"기기와 연결하고 있어요. 준비 안내를 기다려 주세요."
        else->"직접 시작할 때만 마이크를 사용해요. 자동으로 저장하지 않아요."
    }
}

/** Final text and microphone-ended are independent events. Timers can fail, never succeed. */
class SpeechInput(private val context:Context,private val factory:(()->SpeechBackend)?=null,
    private val recognitionTimeoutMs:Long=30000,private val endTimeoutMs:Long=5000,private val finalTimeoutMs:Long=4000,
    private val allowed:()->Boolean){
    private val main=Handler(Looper.getMainLooper());private var backend:SpeechBackend?=null;private var initialized=false
    private var generation=0;private var stopRequested=false;private var ended=false;private var requested=false;private var finalText:String?=null;private var failureCode=""
    private var lastLevelAt=0L
    private var result:(String)->Unit={};private var progress:(String)->Unit={}
    var state=SpeechState();private set
    var observer:(SpeechState)->Unit={}
    val busy get()=state.busy
    val source get()=backend?.source.orEmpty()
    var requests=0;private set
    var completions=0;private set
    private fun publish(next:SpeechState){state=next;observer(next);progress(next.message)}
    private fun event(code:String){Log.i("CaritdaSpeech","session=$generation event=$code requests=$requests completions=$completions source=$source")}
    private fun later(run:Int,action:()->Unit){main.post{if(run==generation&&busy&&allowed())action()}}
    fun listen(progress:(String)->Unit,result:(String)->Unit){
        check(Looper.myLooper()==Looper.getMainLooper())
        if(busy||!allowed()){event("START_REJECTED");return}
        generation++;val run=generation;main.removeCallbacksAndMessages(null)
        this.progress=progress;this.result=result;stopRequested=false;ended=false;requested=false;finalText=null;failureCode="";lastLevelAt=0L
        publish(SpeechState(phase=SpeechPhase.PREPARING,busy=true));event("PREPARING")
        if(initialized&&backend!=null)request(run)else initialize(run,false)
        main.postDelayed({if(run==generation&&busy){event("RECOGNITION_TIMEOUT");fail("STT_RECOGNITION_TIMEOUT")}},recognitionTimeoutMs)
    }
    private fun initialize(run:Int,fallback:Boolean){
        try{
            val next=if(factory!=null)factory.invoke()else if(fallback)AndroidSpeechBackend(context)else PleosSpeechBackend(context)
            backend=next
            next.initialize({later(run){initialized=true;event("INITIALIZED");request(run)}},{code->later(run){
                dispose(false)
                if(!fallback&&factory==null&&AndroidSpeechBackend.available(context)){event("OFFLINE_FALLBACK");initialize(run,true)}else fail(code)
            }})
        }catch(_:Exception){dispose(false);if(!fallback&&factory==null&&AndroidSpeechBackend.available(context))initialize(run,true)else fail("STT_INITIALIZATION_FAILED")}
        catch(_:LinkageError){dispose(false);fail("STT_SDK_UNAVAILABLE")}
    }
    private fun request(run:Int){
        if(requested||!busy)return
        requested=true;requests++;event("REQUEST")
        try{backend!!.listen(object:SpeechBackend.Events{
            private fun inputLevel(value:Double,label:String){later(run){val now=android.os.SystemClock.elapsedRealtime();if(value.isFinite()&&now-lastLevelAt>=500){lastLevelAt=now;event(if(label=="SDK")"SDK_RMS"else"PCM_LEVEL");publish(state.copy(level=value,levelSource=label))}}}
            override fun level(value:Double){inputLevel(value,"SDK")}
            override fun captureLevel(value:Double){inputLevel(value,"기기 마이크")}
            override fun epd(){later(run){event("SDK_EPD_DATA")}}
            override fun ready(){later(run){event("READY");publish(state.copy(ready=true))}}
            override fun started(){later(run){if(ended)return@later;event("STARTED");publish(state.copy(phase=if(stopRequested)SpeechPhase.PROCESSING else SpeechPhase.LISTENING,microphone=if(stopRequested)MicrophoneState.ENDING else MicrophoneState.ACTIVE))}}
            override fun updated(text:String,complete:Boolean){later(run){
                if(finalText!=null)return@later
                val clean=text.trim().take(800)
                if(complete){finalText=clean;event("FINAL");publish(state.copy(phase=SpeechPhase.PROCESSING,text=clean,finalReceived=true));requestEnd()}
                else{event("PARTIAL");publish(state.copy(text=clean))}
            }}
            override fun ended(){later(run){if(ended)return@later;ended=true;event("ENDED");publish(state.copy(microphone=MicrophoneState.ENDED,phase=if(failureCode.isNotEmpty())SpeechPhase.FAILED else SpeechPhase.PROCESSING))
                if(failureCode.isNotEmpty()||finalText!=null)finish()else main.postDelayed({if(run==generation&&busy&&ended){failureCode="STT_FINAL_TIMEOUT";finish()}},finalTimeoutMs)
            }}
            override fun error(code:String){later(run){event("ERROR");fail(code)}}
        })}catch(_:SecurityException){fail("STT_PERMISSION_DENIED")}catch(_:Exception){fail("STT_REQUEST_FAILED")}
    }
    private fun requestEnd(){
        if(stopRequested){if(ended)finish();return}
        stopRequested=true;publish(state.copy(phase=if(failureCode.isEmpty())SpeechPhase.PROCESSING else SpeechPhase.FAILED,microphone=MicrophoneState.ENDING))
        val run=generation
        try{backend?.finish()}catch(_:Exception){failureCode="STT_STOP_FAILED"}
        if(ended){finish();return}
        main.postDelayed({if(run==generation&&busy&&!ended){
            event("END_UNCONFIRMED");failureCode=if(failureCode.isBlank())"STT_END_TIMEOUT"else failureCode
            dispose(true);generation++;main.removeCallbacksAndMessages(null)
            publish(state.copy(phase=SpeechPhase.FAILED,microphone=MicrophoneState.UNCONFIRMED,busy=false,code=failureCode))
        }},endTimeoutMs)
    }
    private fun fail(code:String){
        if(!busy)return
        failureCode=code.take(80);publish(state.copy(phase=SpeechPhase.FAILED,code=failureCode))
        if(!requested){dispose(false);main.removeCallbacksAndMessages(null);publish(state.copy(busy=false));return}
        if(ended)finish()else requestEnd()
    }
    private fun finish(){
        if(!busy||!ended)return
        main.removeCallbacksAndMessages(null)
        // Keep the initialized service; detach exactly this listener only after actual end.
        runCatching{backend?.detach()}.onFailure{dispose(false)}
        val accepted=finalText?.takeIf{it.isNotBlank()&&failureCode.isEmpty()}
        if(accepted==null&&failureCode.isEmpty())failureCode="STT_NO_FINAL_RESULT"
        publish(state.copy(phase=if(accepted!=null)SpeechPhase.COMPLETE else SpeechPhase.FAILED,microphone=MicrophoneState.ENDED,busy=false,code=failureCode))
        event(if(accepted!=null)"COMPLETE"else"FAILED_ENDED")
        if(accepted!=null){completions++;result(accepted)}else dispose(false)
    }
    private fun dispose(stop:Boolean){initialized=false;val old=backend;backend=null;runCatching{if(stop&&!stopRequested)old?.finish()};runCatching{old?.detach()};runCatching{old?.release()}}
    /** Explicit cancellation is never recognition success; original form/draft is untouched. */
    fun cancel(){
        check(Looper.myLooper()==Looper.getMainLooper())
        if(!busy)return
        generation++;main.removeCallbacksAndMessages(null)
        val microphone=if(ended)MicrophoneState.ENDED else if(requested)MicrophoneState.UNCONFIRMED else MicrophoneState.NOT_STARTED
        dispose(requested);result={}
        publish(SpeechState(phase=SpeechPhase.CANCELLED,microphone=microphone))
        progress={};event("CANCELLED")
    }
    /** Screen exit invalidates queued events, releases resources, and never reports success. */
    fun stop(){generation++;main.removeCallbacksAndMessages(null);val active=busy&&requested;dispose(active);progress={};result={};state=SpeechState(microphone=if(active&&!ended)MicrophoneState.UNCONFIRMED else MicrophoneState.NOT_STARTED);event("DISPOSED");observer(state)}
}
