package com.nextdrive.pleos.integration

/** Plays only a prepared screen result through the existing on-device TTS.
 * Entire text is delivered in bounded pieces; neither cache lookup nor AI is a dependency. */
class ExplanationPlayback(private val allowed:()->Boolean,
    private val speak:(String,(Boolean)->Unit,(String)->Unit)->Unit,private val stop:()->Unit) {
    private var generation=0
    var busy=false;private set
    fun play(text:String,changed:(String)->Unit){
        if(busy||!allowed()||text.isBlank())return
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
            if(index==pieces.size){busy=false;changed("음성 안내를 마쳤어요.");return}
            val part=pieces[index++];var completed=false;var feedback="음성 안내를 완료하지 못했어요."
            speak(part,{success->
                if(run==generation&&!completed){completed=true;if(success)next()else{busy=false;changed(feedback)}}
            },{message->if(run==generation){feedback=message;changed(if(message=="음성 안내를 마쳤어요."&&index<pieces.size)"다음 내용을 읽고 있어요."else message)}})
        }
        next()
    }
    fun cancel(){generation++;busy=false;stop()}
}
