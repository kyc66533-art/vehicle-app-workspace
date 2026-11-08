package com.nextdrive.pleos.vehicle

/** One foreground entry, retained through configuration changes; never called from render. */
class EntryReadState {
    var requested=false
        private set
    fun claim(visible:Boolean,allowed:Boolean,guideFinished:Boolean,busy:Boolean):Boolean {
        if(!visible||!allowed||!guideFinished||busy||requested)return false
        requested=true
        return true
    }
    fun markRequested(){requested=true}
}
