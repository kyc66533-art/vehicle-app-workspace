package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import java.util.concurrent.TimeUnit

/** Real SDK request readiness/cancel/reinitialize through actual Android View click listeners.
 * Polls on main without UiAutomator idle waits (RMS updates intentionally keep UI changing).
 * No user utterance, raw PCM, default DB preparation, synthetic issue or external request. */
class SpeechAutomaticUiChecks(private val inst:Instrumentation){
    private fun <T> main(block:()->T):T{var v:T?=null;inst.runOnMainSync{v=block()};@Suppress("UNCHECKED_CAST")return v as T}
    private fun await(check:()->Boolean){val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<until){if(main(check))return;Thread.sleep(100)};error("AUTOMATIC_UI_STATE_NOT_OBSERVED")}
    fun run():List<String>{
        val c=inst.targetContext;ValidationEnvironment.requireIsolated(c)
        check(c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED){"AUTOMATIC_UI_PERMISSION_REQUIRED"}
        val previous=c.getSharedPreferences("integration_diagnostics",0).getInt("stt_completed_sessions",0)
        val activity=inst.startActivitySync(Intent().setClassName(c,"com.nextdrive.pleos.IntegrationDiagnosticActivity").putExtra("use_app_mic",false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        val lines=mutableListOf<String>()
        try{
            val root=activity.window.decorView
            val begin=main{root.findViewWithTag<Button>("voice_symptom")};val cancel=main{root.findViewWithTag<Button>("voice_cancel")};val state=main{root.findViewWithTag<TextView>("speech_state")}
            check(main{root.findViewWithTag<Button>("voice_stop")==null});lines.add("PASS AU01 manual finish action absent in real diagnostic screen")
            await{begin.isEnabled}
            repeat(2){trial->
                main{check(begin.isEnabled);check(begin.performClick())}
                await{state.text.contains("기기가 준비됐어요. 지금 짧게 말씀")||state.text.contains("● 음성 인식 중")}
                lines.add("PASS AU${2+trial*2} actual SDK ready for request ${trial+1}")
                main{check(cancel.isEnabled);check(cancel.performClick())}
                await{state.text.contains("이번 음성 입력을 반영하지 않았어요.")&&begin.isEnabled&&!cancel.isEnabled}
                lines.add("PASS AU${3+trial*2} actual cancel and retry available ${trial+1}")
            }
            check(c.getSharedPreferences("integration_diagnostics",0).getInt("stt_completed_sessions",0)==previous)
            lines.add("PASS AU06 cancellation does not overwrite previous recognized-result metadata or deliver a success")
        }finally{main{activity.finish()}}
        return lines
    }
}
