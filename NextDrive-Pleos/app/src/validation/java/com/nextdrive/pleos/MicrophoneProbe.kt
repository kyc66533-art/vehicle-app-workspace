package com.nextdrive.pleos

import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper

/** Explicit 5s local input-path check. Discards PCM immediately; no file, network or diagnosis. */
class MicrophoneProbe(private val context:Context,private val allowed:()->Boolean){
    private val main=Handler(Looper.getMainLooper());@Volatile private var generation=0
    private val lock=Any()
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
            val metrics=LocalAudioMetrics();var minRead=Int.MAX_VALUE;var maxRead=0;var silenced:Boolean?=null;var readError:Int?=null
            var initialized=false;var recording=false;var inputType:Int?=null
            try{
                val size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(size>0)
                audio=AudioRecord(inputSource,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(size,6400))
                initialized=audio.state==AudioRecord.STATE_INITIALIZED;check(initialized)
                synchronized(lock){if(run!=generation)return@Thread;recorder=audio;audio.startRecording();recording=audio.recordingState==AudioRecord.RECORDSTATE_RECORDING;check(recording)}
                val buffer=ShortArray(1600);val until=android.os.SystemClock.elapsedRealtime()+5000
                while(run==generation&&android.os.SystemClock.elapsedRealtime()<until){val n=audio.read(buffer,0,buffer.size);if(n<0){readError=n;error("Read failed")};minRead=minOf(minRead,n);maxRead=maxOf(maxRead,n);silenced=audio.activeRecordingConfiguration?.isClientSilenced;metrics.accept(buffer,n)}
                inputType=audio.routedDevice?.type
                result=if(metrics.samples==0L)"MIC_NO_SAMPLES"else if(metrics.peak==0)"MIC_INPUT_ZERO"else"MIC_INPUT_PRESENT"
            }catch(_:SecurityException){result="MIC_PERMISSION_DENIED"}catch(_:Exception){result="MIC_READ_FAILED"}
            finally{runCatching{audio?.stop()};runCatching{audio?.release()};main.post{if(run==generation){recorder=null;busy=false;lastSamples=metrics.samples;lastRms=metrics.rms;lastPeak=metrics.peak;lastMinRead=if(minRead==Int.MAX_VALUE)0 else minRead;lastMaxRead=maxRead;lastSilenced=silenced;lastReadError=readError;lastInitialized=initialized;lastRecording=recording;lastInputType=inputType;if(allowed())done(result)}}}
        },"caritda-microphone-probe").start()
    }
    fun stop(){val current=synchronized(lock){generation++;busy=false;recorder.also{recorder=null}};runCatching{current?.stop()};main.removeCallbacksAndMessages(null)}
}
