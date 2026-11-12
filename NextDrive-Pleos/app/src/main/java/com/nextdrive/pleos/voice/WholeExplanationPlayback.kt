package com.nextdrive.pleos.voice

import com.nextdrive.pleos.integration.PlaybackOutcome
import com.nextdrive.pleos.integration.ExplanationPlayback

/** Full text, typed terminal propagation. Used by voice sessions; manual playback remains intact. */
class WholeExplanationPlayback(private val allowed:()->Boolean,
    private val speak:(String,(PlaybackOutcome)->Unit)->Unit,private val stop:()->Unit) {
    private val player=ExplanationPlayback.typed(allowed,{text,done,_->speak(text,done)},stop)
    fun play(text:String,done:(PlaybackOutcome)->Unit)=player.play(text,done){ }
    fun cancel()=player.cancel()
}
