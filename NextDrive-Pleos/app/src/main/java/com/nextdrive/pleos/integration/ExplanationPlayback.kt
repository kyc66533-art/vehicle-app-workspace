package com.nextdrive.pleos.integration

/** Plays only a prepared screen result through the existing on-device TTS.
 * Entire text is delivered in bounded pieces; neither cache lookup nor AI is a dependency. */
class ExplanationPlayback(private val allowed:()->Boolean,
    private val speak:(String,(Boolean)->Unit,(String)->Unit)->Unit,private val stop:()->Unit,
    private val typedSpeak:((String,(PlaybackOutcome)->Unit,(String)->Unit)->Unit)?=null) {
    private var generation=0
    var busy=false;private set
    private var completion:((PlaybackOutcome)->Unit)?=null
    fun play(text:String,changed:(String)->Unit){
        play(text,{},changed)
    }
    fun play(text:String,completed:(PlaybackOutcome)->Unit,changed:(String)->Unit){
        if(busy||!allowed()||text.isBlank()){completed(PlaybackOutcome.REJECTED);return}
        completion=completed
        // Short phrases fit the existing TTS watchdog; retain every character and prefer spaces.
        val pieces=buildList{var start=0;while(start<text.length){
            var end=(start+100).coerceAtMost(text.length)
            if(end<text.length){val boundary=text.lastIndexOfAny(charArrayOf(' ','\n','.','?','!'),end-1);if(boundary>=start+40)end=boundary+1}
            add(text.substring(start,end));start=end
        }};var index=0
        val run=++generation;busy=true
        fun next(){
            if(run!=generation||!busy)return
            if(!allowed()){cancel();return}
            if(index==pieces.size){busy=false;val cb=completion;completion=null;changed("음성 안내를 마쳤어요.");cb?.invoke(PlaybackOutcome.DONE);return}
            val part=pieces[index++];var completed=false;var feedback="음성 안내를 완료하지 못했어요."
            val finished:(PlaybackOutcome)->Unit={outcome->
                if(run==generation&&!completed){completed=true;if(outcome==PlaybackOutcome.DONE)next()else{busy=false;val cb=completion;completion=null;changed(feedback);cb?.invoke(outcome)}}
            }
            val message:(String)->Unit={message->if(run==generation){feedback=message;changed(if(message=="음성 안내를 마쳤어요."&&index<pieces.size)"다음 내용을 읽고 있어요."else message)}}
            if(typedSpeak!=null)typedSpeak.invoke(part,finished,message)else speak(part,{success->finished(if(success)PlaybackOutcome.DONE else PlaybackOutcome.FAILED)},message)
        }
        next()
    }
    fun cancel(){generation++;val cb=completion;completion=null;busy=false;stop();cb?.invoke(PlaybackOutcome.CANCELLED)}
    companion object {
        fun typed(allowed:()->Boolean,speak:(String,(PlaybackOutcome)->Unit,(String)->Unit)->Unit,stop:()->Unit)=
            ExplanationPlayback(allowed,{text,done,changed->speak(text,{done(it==PlaybackOutcome.DONE)},changed)},stop,speak)
    }
}
