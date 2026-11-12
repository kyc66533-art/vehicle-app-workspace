package com.nextdrive.pleos.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.nextdrive.pleos.integration.VoiceAudioOwner
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** Optional validation experiment: actual Korean wake trials failed in this emulator.
 * Excluded from the default/shipping build; no wake accuracy or availability claim.
 * Own offline Korean recognition stream for the wake phrase. No Pleos/Android STT restart,
 * network, audio recording file or model request. A match only opens the normal Pleos session.
 * Confidence is a conservative local filter, not a measured accuracy/safety score. */
class VoskHomeWakeDetector(private val context:Context):HomeWakeDetector {
    override val source="VOSK_KO_0_22_GRAMMAR_ON_DEVICE"
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var cancelled=true
    @Volatile private var workerActive=false
    override val busy get()=workerActive
    @Volatile private var recorder:AudioRecord?=null
    private var generation=0
    @Volatile override var microphoneActive=false;private set
    @Volatile var segments=0;private set
    @Volatile override var cpuNanos=0L;private set
    @Volatile var audioSamples=0L;private set
    override val samples get()=audioSamples
    @Volatile private var nonzeroSamples=0L
    @Volatile private var rejectedExact=0
    override val diagnostics get()="source=$source samples=$audioSamples nonzero=$nonzeroSamples segments=$segments low_confidence_wakes=$rejectedExact cpu_ns_including_setup=$cpuNanos"
    private var closed=false
    private val releaseCallbacks=mutableListOf<()->Unit>()
    private fun modelDirectory():File {
        val expected=context.assets.open("home-wake-model.sha256").bufferedReader().use{it.readText().trim()}
        check(Regex("[a-f0-9]{64}").matches(expected)){"MODEL_DIGEST_INVALID"}
        val parent=File(context.codeCacheDir,"caritda-home-wake").apply{mkdirs()}
        val target=File(parent,expected.take(24))
        if(File(target,"verified.sha256").takeIf{it.isFile}?.readText()==expected&&File(target,"am/final.mdl").isFile)return target
        val temporary=File(parent,"unpack-"+UUID.randomUUID()).apply{check(mkdir())}
        try {
            var total=0L
            context.assets.open("vosk-model-small-ko-0.22.zip").use{input->
                ZipInputStream(input).use{zip->
                    var entry=zip.nextEntry
                    while(entry!=null){
                        check(!cancelled){"WAKE_CANCELLED"}
                        val relative=entry.name.removePrefix("vosk-model-small-ko-0.22/")
                        if(relative.isNotEmpty()&&relative!=entry.name){
                            val file=File(temporary,relative);check(file.canonicalPath.startsWith(temporary.canonicalPath+File.separator)){"MODEL_PATH_INVALID"}
                            if(entry.isDirectory)file.mkdirs()else{
                                file.parentFile?.mkdirs();file.outputStream().use{out->val buffer=ByteArray(65536)
                                    while(true){check(!cancelled){"WAKE_CANCELLED"};val n=zip.read(buffer);if(n<0)break;total+=n;check(total<=512L*1024*1024){"MODEL_SIZE_LIMIT"};out.write(buffer,0,n)}
                                }
                            }
                        }
                        zip.closeEntry();entry=zip.nextEntry
                    }
                }
            }
            // Hash the entire compressed asset, including its central directory.
            val full=context.assets.open("vosk-model-small-ko-0.22.zip").use{input->val sha=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){check(!cancelled){"WAKE_CANCELLED"};val n=input.read(b);if(n<0)break;sha.update(b,0,n)};sha.digest().joinToString(""){"%02x".format(it)}}
            check(full==expected&&File(temporary,"am/final.mdl").isFile&&File(temporary,"conf/mfcc.conf").isFile){"MODEL_INTEGRITY_FAILED"}
            check(!target.exists()&&temporary.renameTo(target)){"MODEL_CACHE_CONFLICT"}
            File(target,"verified.sha256").writeText(expected);return target
        }finally{
            // Only our incomplete, disposable model extraction; never journal/cache/auth files.
            if(temporary.canonicalPath.startsWith(parent.canonicalPath+File.separator)&&temporary.name.startsWith("unpack-"))temporary.deleteRecursively()
        }
    }
    @SuppressLint("MissingPermission")
    override fun start(allowed:()->Boolean,changed:()->Unit,matched:(String)->Unit,failed:(String)->Unit){
        check(Looper.myLooper()==Looper.getMainLooper())
        if(closed||!cancelled||!allowed())return
        if(workerActive){failed("WAKE_STOPPING");return}
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){failed("WAKE_MIC_PERMISSION");return}
        if(!VoiceAudioOwner.claim(this)){failed("AUDIO_BUSY");return}
        generation++;val run=generation;cancelled=false;workerActive=true;microphoneActive=false;segments=0;cpuNanos=0;audioSamples=0;nonzeroSamples=0;rejectedExact=0;changed()
        worker.execute{
            var model:Model?=null;var decoder:Recognizer?=null;var record:AudioRecord?=null
            var result:String?=null;var code:String?=null;var callback:AudioManager.AudioRecordingCallback?=null
            val began=android.os.Debug.threadCpuTimeNanos()
            try{
                val directory=modelDirectory();check(!cancelled){"WAKE_CANCELLED"}
                model=Model(directory.absolutePath)
                // The official Korean graph has 카/잇다/있다 but no 카잇다 symbol.
                // Keep its actual <UNK> symbol as a rejection path for unrelated input.
                // No arbitrary phonetic aliases and no relaxed confidence threshold.
                decoder=Recognizer(model,16000f,"""["카 잇다","카 있다","마이크 꺼 줘","음성 대기 꺼 줘","그만 들어","<UNK>"]""")
                decoder.setWords(true)
                val size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(size>0){"WAKE_AUDIO_FORMAT"}
                record=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size.coerceAtLeast(32000));recorder=record
                check(record.state==AudioRecord.STATE_INITIALIZED){"WAKE_AUDIO_INIT"}
                if(Build.VERSION.SDK_INT>=29){
                    val own=record
                    callback=object:AudioManager.AudioRecordingCallback(){override fun onRecordingConfigChanged(configs:MutableList<AudioRecordingConfiguration>){
                        if(!cancelled&&recorder===own&&configs.any{it.clientAudioSessionId==own.audioSessionId&&it.isClientSilenced}){code="WAKE_AUDIO_SILENCED";cancelled=true;runCatching{own.stop()}}
                    }}
                    context.getSystemService(AudioManager::class.java).registerAudioRecordingCallback(callback,main)
                }
                check(!cancelled){"WAKE_CANCELLED"};record.startRecording();check(record.recordingState==AudioRecord.RECORDSTATE_RECORDING){"WAKE_AUDIO_START"}
                val samples=ShortArray(3200)
                while(!cancelled){
                    val n=record.read(samples,0,samples.size,AudioRecord.READ_BLOCKING)
                    if(n<0){code="WAKE_AUDIO_READ";break};if(n==0)continue
                    audioSamples+=n;nonzeroSamples+=(0 until n).count{samples[it].toInt()!=0};val first=!microphoneActive;microphoneActive=true;cpuNanos=android.os.Debug.threadCpuTimeNanos()-began
                    if(first)main.post{if(run==generation&&!cancelled)changed()}
                    if(decoder.acceptWaveForm(samples,n)){
                        segments++;val value=JSONObject(decoder.result);val text=value.optString("text").trim()
                        val words=value.optJSONArray("result")
                        val flat=text.filter{!it.isWhitespace()&&it !in ",.!?。"}
                        val confident=words!=null&&words.length()>0&&(0 until words.length()).all{words.getJSONObject(it).optDouble("conf",0.0)>=0.85}
                        if(flat in setOf("카잇다","카있다")&&!confident)rejectedExact++
                        if(confident){
                            if(flat in setOf("카잇다","카있다")){result="카잇다";break}
                            if(VoiceCommandRouter.route(text,false,false)==VoiceCommand.StopListening){result=text;break}
                        }
                    }
                }
            }catch(e:Exception){if(!cancelled)code=if(e is SecurityException)"WAKE_MIC_PERMISSION"else e.message?.takeIf{Regex("[A-Z_]{1,64}").matches(it)}?:"WAKE_ENGINE_FAILED"}
            catch(_:LinkageError){code="WAKE_ENGINE_UNAVAILABLE"}
            finally{
                microphoneActive=false
                runCatching{record?.stop()};record?.release();recorder=null
                if(callback!=null)context.getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(callback)
                runCatching{decoder?.close()};runCatching{model?.close()};VoiceAudioOwner.release(this)
                val accepted=result;val error=code;cancelled=true;workerActive=false
                main.post{
                    val callbacks=releaseCallbacks.toList();releaseCallbacks.clear();callbacks.forEach{it()}
                    changed();if(run==generation){if(accepted!=null&&allowed())matched(accepted)else if(error!=null)failed(error)}
                }
            }
        }
    }
    override fun stop(released:()->Unit){
        check(Looper.myLooper()==Looper.getMainLooper())
        generation++;cancelled=true
        if(workerActive){releaseCallbacks.add(released);runCatching{recorder?.stop()}}
        else released()
    }
    override fun close(){closed=true;stop();worker.shutdown()}
}
