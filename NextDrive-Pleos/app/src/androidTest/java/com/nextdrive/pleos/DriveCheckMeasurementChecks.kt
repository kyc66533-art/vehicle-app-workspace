package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Context
import android.media.AudioManager

/** Synthetic amplitude fixtures and actual device inventory. Never starts the microphone. */
class DriveCheckMeasurementChecks(private val inst:Instrumentation){
    fun run():List<String>{
        val c=inst.targetContext;ValidationEnvironment.requireIsolated(c);val lines=mutableListOf<String>()
        fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
        val m=LocalAudioMetrics()
        verify(m.rms==null&&m.samples==0L,"DM01 no samples never becomes a normal/zero measurement")
        val pcm=shortArrayOf(16384,-16384,16384,-16384);m.accept(pcm,4)
        verify(m.samples==4L&&m.rms==0.5&&m.peak==16384,"DM02 synthetic PCM yields normalized RMS/peak without a decibel or fault claim")
        verify(pcm.all{it==0.toShort()},"DM03 consumed samples are wiped immediately without a recording file")
        val extrema=LocalAudioMetrics();extrema.accept(shortArrayOf(Short.MIN_VALUE,Short.MAX_VALUE),2)
        verify(extrema.peak==32768&&extrema.rms!!<=1.0,"DM04 signed PCM extremes remain finite without integer overflow")
        val zero=LocalAudioMetrics();zero.accept(ShortArray(16),16)
        verify(zero.rms==0.0&&zero.samples==16L,"DM05 actual zero samples are distinct from absent input and never a vehicle health result")
        verify(runCatching{zero.accept(ShortArray(2),3)}.isFailure,"DM06 invalid sample lengths are rejected")
        var callback=false;val probe=MicrophoneProbe(c){false}
        inst.runOnMainSync{probe.start{callback=true};probe.stop();probe.stop()}
        verify(!probe.busy&&!callback&&probe.lastSamples==0L,"DM07 unsafe/invisible start and repeated cancellation never start capture or create a success")
        val devices=(c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_INPUTS)
        lines.add("RUNTIME AUDIO_INPUT_TYPES "+devices.map{it.type}.distinct().sorted().joinToString(",")+"; inventory is not capture success; live microphone not automatically started")
        return lines
    }
}
