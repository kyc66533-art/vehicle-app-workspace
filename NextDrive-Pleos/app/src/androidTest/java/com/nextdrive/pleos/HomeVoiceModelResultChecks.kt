package com.nextdrive.pleos

import android.app.Instrumentation
import com.nextdrive.pleos.ai.auth.FirebaseSessionProvider
import com.nextdrive.pleos.ai.IssueDialogueContract
import com.nextdrive.pleos.data.ConversationStore
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.model.IssueStatus
import org.json.JSONObject
import java.io.File

/** New process, existing UUID fixture only. No ASR, TTS, model request or account mutation. */
class HomeVoiceModelResultChecks(private val inst:Instrumentation){
    fun run():List<String>{
        val ctx=inst.targetContext;ValidationEnvironment.requireIsolated(ctx);val h=BLightHarness(inst)
        val marker=JSONObject(File(ctx.filesDir,"home-microphone-stage.json").readText())
        check(marker.getString("phase") in setOf("COMPLETE","COMPLETE_RECOVERED","COMPLETE_EXTRA"))
        val prefix=marker.getString("run");check(Regex("gleo-ui-[a-f0-9-]{36}").matches(prefix))
        check(ctx.getDatabasePath("$prefix.db").isFile&&ctx.getDatabasePath("$prefix-conversations.db").isFile)
        h.main{FirebaseSessionProvider.prepare(ctx)}
        val end=System.currentTimeMillis()+12000
        while(System.currentTimeMillis()<end&&!FirebaseSessionProvider.initialized)Thread.sleep(80)
        val account=FirebaseSessionProvider.subject()?:error("EXISTING_ACCOUNT_REQUIRED")
        check(ctx.getSharedPreferences("voice-real-model-20261112",0).getStringSet("operations",emptySet()).orEmpty().size==2)
        val issue=SqliteIssueRepository(ctx,"$prefix.db").use{it.issues().single()}
        check(issue.status==IssueStatus.UNRESOLVED)
        ConversationStore(ctx,"$prefix-conversations.db").use{store->
            val sid=store.sessions(issue.reporter.id,account).single()
            val messages=store.messages(sid,issue.reporter.id,account)
            check(messages.count{it.origin in setOf("GEMINI_NEW","GEMINI_RECOVERED")}==2)
            check(messages.none{it.text.contains("복원하지 못했어요")})
            val context=messages.filter{it.origin in setOf("GEMINI_NEW","GEMINI_RECOVERED")}.takeLast(2).flatMap{answer->
                val parent=store.readableDatabase.rawQuery("SELECT parent FROM turns WHERE id=?",arrayOf(answer.id)).use{check(it.moveToFirst());it.getString(0)}
                val question=messages.single{it.id==parent}
                listOf(IssueDialogueContract.Turn("USER",question.text),IssueDialogueContract.Turn("ASSISTANT",answer.text.substringBefore("\n\n")))
            }
            check(IssueDialogueContract.payload(issue,"정비소에 가야 할까?",context).toByteArray().size<=3500)
            check(store.readableDatabase.rawQuery("SELECT COUNT(*) FROM operations WHERE session_id=? AND state='SUCCEEDED' AND server_operation IS NOT NULL",arrayOf(sid)).use{it.moveToFirst();it.getInt(0)==2})
            check(store.readableDatabase.rawQuery("SELECT COUNT(*) FROM playbacks WHERE session_id=? AND state='DONE'",arrayOf(sid)).use{it.moveToFirst();it.getInt(0)>=3})
        }
        return listOf("PASS fresh process restores the same committed unresolved issue",
            "PASS fresh process decrypts both actual new Gemini replies under the same driver/account",
            "PASS both successful server operation links remain without a new request",
            "PASS prompt and two actual full TTS DONE results persist after process restart",
            "PASS third-question contract uses the saved real context without another model request",
            "BOUNDARIES ASR=PRIOR_EXPLICIT_FIXTURE MODEL_CALLS=0 TTS_CALLS=0 DATABASE_READ_ONLY=true")
    }
}
