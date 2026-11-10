package com.nextdrive.pleos

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.stt.listener.OnInitializeListener
import com.nextdrive.pleos.integration.SpeechBackend
import java.util.Locale

/** Official ON_DEVICE sendAudio/completeAudioSend alternative; real microphone, no synthetic PCM.
 * Validation only until end-to-end proven. Never writes/exports audio or calls request() together
 * with app-supplied PCM. Physical capture stop is observed from owned AudioRecord, not fabricated
 * as an SDK onEndedRecognition callback. */
class PleosPcmSpeechBackend(private val context:Context):SpeechBackend{
    override val source="PLEOS_PCM_ON_DEVICE"
    private val sdk=SpeechToText(context.applicationContext,Mode.ON_DEVICE)
    private val main=Handler(Looper.getMainLooper());@Volatile private var generation=0
    @Volatile private var audio:AudioRecord?=null;@Volatile private var ending=false
    private var listener:ResultListener?=null
    override fun initialize(ready:()->Unit,failed:(String)->Unit){sdk.initialize(object:OnInitializeListener{
        override fun onSuccess(){ready()}
        override fun onFailure(errorType:ErrorType){failed("STT_PCM_INIT_"+errorType.name)}
    })}
    override fun listen(events:SpeechBackend.Events){
        check(listener==null);ending=false;val run=++generation
        val current=object:ResultListener{
            override fun onReady(){events.ready()}
            override fun onStartedRecognition(){Log.i("CaritdaSpeech","event=SDK_EPD_STARTED source=$source")}
            override fun onEndedRecognition(){Log.i("CaritdaSpeech","event=SDK_EPD_ENDED source=$source")}
            override fun onUpdated(stt:String,completed:Boolean){events.updated(stt,completed)}
            override fun onError(errorType:ErrorType){events.error("STT_PCM_"+errorType.name)}
            override fun onUpdatedEpdData(on:Long,off:Long){events.epd()}
            override fun onUpdatedRms(rms:Double){events.level(rms)}
        }
        listener=current;sdk.addListener(current);sdk.setLocale(Locale.KOREA)
        Thread({
            var recorder:AudioRecord?=null;var failure:String?=null;var inputEnded=false;var count=0L;var nonzero=false
            var sumSquares=0.0;var peak=0;var minRead=Int.MAX_VALUE;var maxRead=0;var readErrors=0;var inputType:Int?=null
            try{
                val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(minimum>0)
                recorder=AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,6400));audio=recorder
                check(recorder.state==AudioRecord.STATE_INITIALIZED)
                if(run!=generation)return@Thread
                recorder.startRecording();check(recorder.recordingState==AudioRecord.RECORDSTATE_RECORDING)
                main.post{if(run==generation){Log.i("CaritdaSpeech","event=PCM_CAPTURE_STARTED source=$source");events.started()}}
                val bytes=ByteArray(3200);val until=android.os.SystemClock.elapsedRealtime()+15000
                while(run==generation&&!ending&&android.os.SystemClock.elapsedRealtime()<until){
                    val n=recorder.read(bytes,0,bytes.size);if(n<0){if(ending)break else{readErrors++;error("PCM read failed")}};if(n==0)continue
                    minRead=minOf(minRead,n);maxRead=maxOf(maxRead,n);inputType=recorder.routedDevice?.type
                    var squares=0.0;for(i in 0 until n-1 step 2){val sample=((bytes[i].toInt() and 255) or (bytes[i+1].toInt() shl 8)).toShort().toDouble();squares+=sample*sample;peak=maxOf(peak,kotlin.math.abs(sample.toInt()))}
                    sumSquares+=squares;nonzero=nonzero||squares>0;count+=n
                    val rms=kotlin.math.sqrt(squares/maxOf(1,n/2))/32768.0
                    main.post{if(run==generation)events.captureLevel(rms)}
                    if(run==generation&&!ending)sdk.sendAudio(bytes.copyOf(n))
                    bytes.fill(0)
                }
            }catch(_:SecurityException){failure="STT_PCM_PERMISSION_DENIED"}catch(_:Exception){if(!ending)failure="STT_PCM_CAPTURE_FAILED"}
            finally{
                runCatching{recorder?.stop()}
                inputEnded=recorder?.recordingState==AudioRecord.RECORDSTATE_STOPPED
                runCatching{recorder?.release()};if(run==generation){audio=null
                    val totalRms=if(count>0)kotlin.math.sqrt(sumSquares/(count/2))/32768.0 else 0.0
                    Log.i("CaritdaSpeech","event=PCM_INPUT_STATS bytes=$count nonzero=$nonzero min_read=$minRead max_read=$maxRead read_errors=$readErrors rms=$totalRms peak_s16=$peak routed_type=$inputType source=$source")
                    context.getSharedPreferences("integration_diagnostics",Context.MODE_PRIVATE).edit().putLong("pcm_bytes",count).putString("pcm_rms",totalRms.toString()).putInt("pcm_peak_s16",peak).putInt("pcm_min_read",minRead).putInt("pcm_max_read",maxRead).putInt("pcm_read_errors",readErrors).apply()
                    // EOF is required by the public SDK contract; no raw audio remains in app files.
                    val sent=runCatching{sdk.completeAudioSend()}.isSuccess
                    main.post{if(run==generation){
                        failure?.let{events.error(it)}
                        if(!sent)events.error("STT_PCM_EOF_FAILED")
                        if(inputEnded){Log.i("CaritdaSpeech","event=PCM_CAPTURE_STOPPED source=$source");events.ended()}
                        else events.error("STT_PCM_STOP_UNCONFIRMED")
                    }}
                }
            }
        },"caritda-pleos-pcm").start()
    }
    override fun finish(){ending=true;runCatching{audio?.stop()}}
    override fun detach(){listener?.let{sdk.removeListener(it)};listener=null}
    override fun release(){generation++;ending=true;runCatching{audio?.stop()};audio=null;detach();sdk.release();main.removeCallbacksAndMessages(null)}
}
