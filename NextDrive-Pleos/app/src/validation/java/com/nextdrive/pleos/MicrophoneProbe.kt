package com.nextdrive.pleos

import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper

/** Explicit 5s local input-path check. Discards PCM immediately; no file, network or diagnosis. */
class MicrophoneProbe(private val context:Context,private val allowed:()->Boolean){
    private val main=Handler(Looper.getMainLooper());@Volatile private var generation=0
    @Volatile private var recorder:AudioRecord?=null
    @Volatile var busy=false;private set
    @Volatile var lastRms:Double?=null;private set
    @Volatile var lastSamples=0L;private set
    @Volatile var lastInputType:Int?=null;private set
    @Volatile var lastPeak=0;private set
    @Volatile var lastMinRead=0;private set
    @Volatile var lastMaxRead=0;private set
    @Volatile var lastSilenced:Boolean?=null;private set
    @Volatile var lastReadError:Int?=null;private set
    @Volatile var lastInitialized=false;private set
    @Volatile var lastRecording=false;private set
    fun start(inputSource:Int=MediaRecorder.AudioSource.VOICE_RECOGNITION,done:(String)->Unit){
        if(busy||!allowed())return
        if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){done("MIC_PERMISSION_REQUIRED");return}
        busy=true;lastRms=null;lastSamples=0;lastInputType=null;lastPeak=0;lastMinRead=Int.MAX_VALUE;lastMaxRead=0;lastSilenced=null;lastReadError=null;lastInitialized=false;lastRecording=false;val run=++generation
        Thread({
            var result="MIC_READ_FAILED";var audio:AudioRecord?=null
            try{
                val size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(size>0)
                audio=AudioRecord(inputSource,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(size,6400));recorder=audio
                lastInitialized=audio.state==AudioRecord.STATE_INITIALIZED;check(lastInitialized)
                if(run!=generation)return@Thread
                audio.startRecording();lastRecording=audio.recordingState==AudioRecord.RECORDSTATE_RECORDING;check(lastRecording)
                val buffer=ShortArray(1600);var count=0L;var power=0.0;val until=android.os.SystemClock.elapsedRealtime()+5000
                while(run==generation&&android.os.SystemClock.elapsedRealtime()<until){val n=audio.read(buffer,0,buffer.size);if(n<0){lastReadError=n;error("Read failed")};lastMinRead=minOf(lastMinRead,n);lastMaxRead=maxOf(lastMaxRead,n);lastSilenced=audio.activeRecordingConfiguration?.isClientSilenced;for(i in 0 until n){power+=buffer[i].toDouble()*buffer[i];lastPeak=maxOf(lastPeak,kotlin.math.abs(buffer[i].toInt()));buffer[i]=0};count+=n}
                lastSamples=count;lastRms=if(count>0)kotlin.math.sqrt(power/count)/32768.0 else null;lastInputType=audio.routedDevice?.type
                result=if(count==0L)"MIC_NO_SAMPLES"else if(power==0.0)"MIC_INPUT_ZERO"else"MIC_INPUT_PRESENT"
            }catch(_:SecurityException){result="MIC_PERMISSION_DENIED"}catch(_:Exception){result="MIC_READ_FAILED"}
            finally{runCatching{audio?.stop()};runCatching{audio?.release()};if(run==generation){recorder=null;busy=false;main.post{if(run==generation&&allowed())done(result)}}}
        },"caritda-microphone-probe").start()
    }
    fun stop(){generation++;busy=false;runCatching{recorder?.stop()};recorder=null;main.removeCallbacksAndMessages(null)}
}
