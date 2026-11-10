package com.nextdrive.pleos

import android.app.Instrumentation
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.stt.listener.OnInitializeListener
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest

/** B-only engine isolation: hash-pinned public PCM or local offline synthetic TTS.
 * Neither path uses user microphone audio. Diagnostics do not imply live-microphone success. */
class SpeechFixtureChecks(private val inst:Instrumentation){
    private val c=inst.targetContext
    private fun main(block:()->Unit){inst.runOnMainSync(block)}
    private fun pcm(file:File):ByteArray{
        val raw=file.readBytes();check(raw.size in 44..2000000&&String(raw,0,4)=="RIFF"&&String(raw,8,4)=="WAVE")
        val b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);var rate=0;var channels=0;var format=0;var bits=0;var data=byteArrayOf();var at=12
        while(at+8<=raw.size){val key=String(raw,at,4);val n=b.getInt(at+4);check(n>=0&&at+8L+n<=raw.size)
            if(key=="fmt "){format=b.getShort(at+8).toInt();channels=b.getShort(at+10).toInt();rate=b.getInt(at+12);bits=b.getShort(at+22).toInt()}
            if(key=="data")data=raw.copyOfRange(at+8,at+8+n)
            at+=8+n+(n%2)
        }
        check(format==1&&bits==16&&channels in 1..2&&rate in 8000..48000&&data.isNotEmpty()){"Unsupported offline fixture format"}
        val input=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);val frames=data.size/(2*channels)
        val mono=DoubleArray(frames){var value=0.0;repeat(channels){value+=input.short};value/channels}
        val output=ByteBuffer.allocate((frames.toLong()*16000/rate).toInt()*2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(output.capacity()/2){i->val x=i*rate/16000.0;val a=x.toInt().coerceIn(0,frames-1);val z=minOf(a+1,frames-1);output.putShort((mono[a]*(1-(x-a))+mono[z]*(x-a)).toInt().coerceIn(-32768,32767).toShort())}
        check(output.array().any{it.toInt()!=0}){"Silent fixture"};return output.array()
    }
    fun run(stopComparison:Boolean=false):List<String>{
        ValidationEnvironment.requireIsolated(c)
        val known=File(c.filesDir,"stt-public-goforward-350b76a92902e0580728fe675ee50753a383ffae.pcm")
        if(known.exists()){
            val bytes=known.readBytes();val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
            check(bytes.size==89160&&hash=="f15c60ec54059d8b66e410d0064945a0b0a04ea56e1ddca1958e493c0cf70e71"){"Public fixture checksum mismatch"}
            return engine(List(3){bytes},List(3){"forward"},false,mutableListOf("FIXTURE source=CMUSPHINX_PUBLIC_TEST_NOT_USER language=en format=PCM_S16LE_16000_MONO microphone=false bytes=89160 sha256=$hash"),stopComparison)
        }
        if(stopComparison)return listOf("BLOCKED PUBLIC_PCM_REQUIRED_FOR_STOP_COMPARISON; no microphone request")
        val lines=mutableListOf<String>();val ready=CountDownLatch(1);var tts:TextToSpeech?=null;var ttsStatus=-1
        main{tts=TextToSpeech(c){status->ttsStatus=status;ready.countDown()}}
        if(!ready.await(8,TimeUnit.SECONDS)||ttsStatus!=TextToSpeech.SUCCESS){main{tts?.shutdown()};return listOf("BLOCKED OFFLINE_TTS_INITIALIZATION; no fixture/no STT request")}
        var ko=false;var voiceFound=false
        main{val voices=tts!!.voices.orEmpty().filter{!it.isNetworkConnectionRequired};val selected=voices.firstOrNull{it.locale.language=="ko"}?:voices.firstOrNull{it.locale.language=="en"};if(selected!=null){tts!!.voice=selected;ko=selected.locale.language=="ko";voiceFound=true}}
        if(!voiceFound){main{tts!!.shutdown()};return listOf("BLOCKED OFFLINE_KO_OR_EN_VOICE_UNAVAILABLE; no cloud synthesis/no STT request")}
        val texts=if(ko)listOf("차에서 소음이 들립니다.","핸들이 진동합니다.","차량 점검이 필요합니다.")else listOf("There is a noise in the front.","I feel a vibration.","Please check the vehicle.")
        val words=if(ko)listOf("소음","진동","점검")else listOf("noise","vibration","vehicle")
        val fixtures=mutableListOf<ByteArray>();val fixtureId=UUID.randomUUID().toString()
        try{for((index,text)in texts.withIndex()){
            val done=CountDownLatch(1);var success=false;val file=File(c.filesDir,"stt-synthetic-$fixtureId-$index.wav")
            main{tts!!.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                override fun onStart(id:String?){}
                override fun onDone(id:String?){success=true;done.countDown()}
                @Deprecated("Platform callback")override fun onError(id:String?){done.countDown()}
            });check(tts!!.synthesizeToFile(text,Bundle(),file,"fixture-$index")!=TextToSpeech.ERROR)}
            if(!done.await(12,TimeUnit.SECONDS)||!success)return listOf("BLOCKED OFFLINE_FIXTURE_SYNTHESIS; no STT request")
            fixtures.add(pcm(file))
        }}finally{main{tts!!.shutdown()}}
        lines.add("FIXTURE source=OFFLINE_SYNTHETIC_TTS language="+(if(ko)"ko"else"en")+" format=PCM_S16LE_16000_MONO microphone=false bytes="+fixtures.joinToString(","){it.size.toString()})
        return engine(fixtures,words,ko,lines)
    }
    private fun engine(fixtures:List<ByteArray>,words:List<String>,ko:Boolean,lines:MutableList<String>,stopComparison:Boolean=false):List<String>{
        val init=CountDownLatch(1);var sdk:SpeechToText?=null;var connected=false;var initCode=""
        main{sdk=SpeechToText(c,Mode.ON_DEVICE);sdk!!.initialize(object:OnInitializeListener{
            override fun onSuccess(){connected=true;init.countDown()}
            override fun onFailure(errorType:ErrorType){initCode=errorType.name;init.countDown()}
        })}
        if(!init.await(8,TimeUnit.SECONDS)||!connected){main{sdk?.release()};return lines+"BLOCKED PLEOS_INIT_$initCode"}
        try{main{sdk!!.setLocale(if(ko)Locale.KOREA else Locale.US)}
            for((index,bytes)in fixtures.withIndex()){
                val done=CountDownLatch(1);val answer=AtomicReference<String>();var code="";var readyCount=0;var startCount=0;var endCount=0;var rmsCount=0;var epdCount=0
                val listener=object:ResultListener{
                    override fun onReady(){readyCount++}
                    override fun onStartedRecognition(){startCount++}
                    override fun onEndedRecognition(){endCount++}
                    override fun onUpdated(stt:String,completed:Boolean){if(completed){answer.set(stt);done.countDown()}}
                    override fun onError(errorType:ErrorType){code=errorType.name;done.countDown()}
                    override fun onUpdatedRms(rms:Double){rmsCount++}
                    override fun onUpdatedEpdData(on:Long,off:Long){epdCount++}
                }
                main{sdk!!.addListener(listener)}
                val started=android.os.SystemClock.elapsedRealtime()
                // No request(): this is the independent, documented app-PCM route.
                val submitted=if(stopComparison)bytes.copyOfRange(0,minOf(38400,bytes.size))else bytes
                for(at in submitted.indices step 3200){sdk!!.sendAudio(submitted.copyOfRange(at,minOf(at+3200,submitted.size)));Thread.sleep(100)}
                val finalBeforeClose=answer.get()!=null;val endedBeforeClose=endCount>0
                val closeMode=if(stopComparison)listOf("STOP_ONLY","EOF_THEN_STOP","STOP_THEN_EOF")[index]else"EOF_ONLY"
                when(closeMode){
                    "STOP_ONLY"->sdk!!.stop()
                    "EOF_THEN_STOP"->{sdk!!.completeAudioSend();sdk!!.stop()}
                    "STOP_THEN_EOF"->{sdk!!.stop();sdk!!.completeAudioSend()}
                    else->sdk!!.completeAudioSend()
                }
                done.await(if(stopComparison)8 else 30,TimeUnit.SECONDS)
                main{sdk!!.removeListener(listener)}
                val text=answer.get().orEmpty();val expected=text.lowercase().replace(" ","").contains(words[index])
                val hash=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
                lines.add("FIXTURE_RESULT index=$index close_mode=$closeMode submitted_bytes=${submitted.size} final_before_close=$finalBeforeClose ended_before_close=$endedBeforeClose final_received=${text.isNotBlank()} expected_word=$expected chars=${text.length} sha256=$hash code=${code.ifEmpty{if(text.isBlank())"NO_FINAL_CALLBACK"else"NONE"}} latency_ms="+(android.os.SystemClock.elapsedRealtime()-started)+" ready=$readyCount started=$startCount ended=$endCount rms=$rmsCount epd=$epdCount")
                if(!stopComparison&&(text.isBlank()||code.isNotBlank()))break
            }
        }finally{main{sdk!!.release()}}
        return lines
    }
}
