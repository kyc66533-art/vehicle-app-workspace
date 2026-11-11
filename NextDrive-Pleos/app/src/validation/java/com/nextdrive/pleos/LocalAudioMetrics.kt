package com.nextdrive.pleos

/** Uncalibrated digital PCM amplitude only. Never a sound-pressure level or fault classifier.
 * Samples are reduced in memory and wiped as they are consumed. No audio is retained. */
class LocalAudioMetrics {
    var samples=0L;private set
    var peak=0;private set
    private var power=0.0
    val rms:Double? get()=if(samples==0L)null else kotlin.math.sqrt(power/samples)/32768.0
    fun accept(buffer:ShortArray,count:Int){
        require(count in 0..buffer.size)
        for(i in 0 until count){val value=buffer[i].toInt();power+=value.toDouble()*value;peak=maxOf(peak,kotlin.math.abs(value));buffer[i]=0}
        samples+=count
    }
}
