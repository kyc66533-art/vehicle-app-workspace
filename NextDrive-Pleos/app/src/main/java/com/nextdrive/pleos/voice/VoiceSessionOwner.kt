package com.nextdrive.pleos.voice

/** One automated session per app process, including the interval waiting for AI. */
object VoiceSessionOwner {
    private var session:String?=null
    @Synchronized fun current()=session
    @Synchronized fun claim(id:String):Boolean {if(session!=null)return false;session=id;return true}
    @Synchronized fun release(id:String?){if(session==id)session=null}
}
