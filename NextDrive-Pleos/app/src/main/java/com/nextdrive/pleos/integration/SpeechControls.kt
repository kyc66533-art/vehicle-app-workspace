package com.nextdrive.pleos.integration

import android.view.View
import android.widget.LinearLayout
import com.nextdrive.pleos.ui.*

/** Updates only this small panel; partial recognition never redraws the form or saves a draft. */
class SpeechControls(context:android.content.Context,private val ui:CaritdaComponents,private val input:SpeechInput,
    private val allowed:()->Boolean,private val start:()->Unit):LinearLayout(context){
    init{
        orientation=VERTICAL
        val status=ui.text("",24,true).apply{tag="speech_state";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        val partial=ui.text("",22).apply{tag="speech_partial"}
        val level=ui.text("SDK 입력 크기: 미수신",20).apply{tag="speech_level"}
        val begin=ui.action("음성으로 증상 입력","voice_symptom",ActionStyle.SECONDARY,allowed()){start()}
        val end=ui.action("음성 입력 취소","voice_cancel",ActionStyle.QUIET,false){input.cancel()}
        addView(status);addView(partial);if(context.packageName.endsWith(".validation"))addView(level);addView(begin);addView(end)
        val update:(SpeechState)->Unit={s->
            status.text=s.message+(if(context.packageName.endsWith(".validation")&&s.code.isNotEmpty())"\n확인 코드: "+s.code else "")
            partial.text=if(s.text.isBlank())"인식한 문장이 여기에 보여요."else(if(s.finalReceived)"인식한 문장 · "else"인식 중 · ")+s.text
            level.text=s.level?.let{s.levelSource+" 입력 크기: "+String.format(java.util.Locale.ROOT,"%.6f",it)+" · 상대값, dB 아님"}?:"SDK 입력 크기: 미수신 (0이라는 뜻이 아니에요)"
            begin.isEnabled=allowed()&&!s.busy
            begin.setTextColor(if(begin.isEnabled)ui.colors.text else ui.colors.secondary)
            begin.text=if(s.phase in setOf(SpeechPhase.COMPLETE,SpeechPhase.CANCELLED,SpeechPhase.FAILED))"다시 음성 입력"else"음성으로 증상 입력"
            end.isEnabled=allowed()&&s.busy
            end.setTextColor(if(end.isEnabled)ui.colors.text else ui.colors.secondary)
        }
        input.observer=update;update(input.state)
    }
}
