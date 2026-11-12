package com.nextdrive.pleos.voice

/** Independent foreground microphone owner. Match is delivered only AFTER actual release. */
interface HomeWakeDetector {
    val source:String
    val microphoneActive:Boolean
    val busy:Boolean
    /** Aggregate counters only; never raw audio or pre-wake transcripts. */
    val samples:Long
    val cpuNanos:Long
    val diagnostics:String
    fun start(allowed:()->Boolean,changed:()->Unit,matched:(String)->Unit,failed:(String)->Unit)
    /** Completion runs on the main thread after the recorder and decoder are released. */
    fun stop(released:()->Unit={})
    fun close()
}
