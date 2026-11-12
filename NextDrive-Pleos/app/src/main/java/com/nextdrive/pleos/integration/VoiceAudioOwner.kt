package com.nextdrive.pleos.integration

/** App-wide half-duplex ownership shared by manual and automated sessions. */
object VoiceAudioOwner {
    private var owner:Any?=null
    @Synchronized fun claim(candidate:Any):Boolean {if(owner!=null&&owner!==candidate)return false;owner=candidate;return true}
    @Synchronized fun release(candidate:Any){if(owner===candidate)owner=null}
}
enum class PlaybackOutcome { DONE, CANCELLED, FAILED, REJECTED }
