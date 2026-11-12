package com.nextdrive.pleos.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

interface VoiceListeningFocus { fun acquire(lost:()->Unit):Boolean;fun release() }

/** Short foreground capture only. Loss never restarts the microphone on a later gain. */
class AndroidVoiceListeningFocus(context:Context):VoiceListeningFocus {
    private val audio=context.getSystemService(AudioManager::class.java)
    private val main=Handler(Looper.getMainLooper())
    private var request:AudioFocusRequest?=null
    private var generation=0
    override fun acquire(lost:()->Unit):Boolean {
        release();val run=generation
        val next=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({change->if(change<=AudioManager.AUDIOFOCUS_LOSS&&run==generation){release();lost()}},main).build()
        request=next
        if(audio.requestAudioFocus(next)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return true
        release();return false
    }
    override fun release(){generation++;val old=request;request=null;if(old!=null)audio.abandonAudioFocusRequest(old)}
}
