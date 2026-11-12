package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.voice.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Original mode resumes only the issued operation. An optional supplemental mode requires
 * a DIFFERENT explicit approval and separate one-operation ledger; the old two stay intact. */
class HomeVoiceRecoveryChecks(private val inst:Instrumentation){
    fun run(approval:String,extra:Boolean=false):List<String>{
        val ctx=inst.targetContext;ValidationEnvironment.requireIsolated(ctx)
        check(approval==if(extra)"USER_APPROVED_ONE_EXTRA_FOLLOWUP_20261112"else"USER_APPROVED_MAX_TWO_20261112"){"EXPLICIT_APPROVAL_FOR_THIS_OPERATION_REQUIRED"}
        val h=BLightHarness(inst);val lines=mutableListOf<String>()
        fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
        val guard=ctx.getSharedPreferences("voice-real-model-20261112",0)
        val approved=guard.getStringSet("operations",emptySet()).orEmpty();check(approved.size==2)
        val supplemental=ctx.getSharedPreferences("voice-real-model-extra-20261112",0)
        if(extra)check(supplemental.getStringSet("operations",emptySet()).orEmpty().isEmpty()){"EXISTING_SUPPLEMENTAL_ATTEMPT_NO_NEW_REQUEST"}
        val marker=File(ctx.filesDir,"home-microphone-stage.json")
        val prefix=JSONObject(marker.readText()).getString("run")
        check(Regex("gleo-ui-[a-f0-9-]{36}").matches(prefix)&&ctx.getDatabasePath("$prefix.db").isFile)
        h.main{FirebaseSessionProvider.prepare(ctx)}
        val ready=System.currentTimeMillis()+12000
        while(System.currentTimeMillis()<ready&&!FirebaseSessionProvider.initialized)Thread.sleep(80)
        val session=VoiceRealModelChecks(inst).GuardSession(extra)
        val account=session.subject?:error("EXISTING_ACCOUNT_REQUIRED")
        val repo=SqliteIssueRepository(ctx,"$prefix.db");val issue=repo.issues().single()
        val owner=issue.reporter.id;val settings=VoiceSettings(ctx,"$prefix-settings")
        val permit=settings.read(owner,account)
        check(permit.cloud&&permit.speak&&permit.history)
        settings.write(owner,account,permit.copy(autoWait=false))
        val store=ConversationStore(ctx,"$prefix-conversations.db")
        val sid=store.sessions(owner,account).single()
        val local=store.readableDatabase.rawQuery("SELECT id,turn_id,code FROM operations WHERE session_id=? AND kind='DIALOGUE' ORDER BY at LIMIT 1",arrayOf(sid)).use{
            check(it.moveToFirst());Triple(it.getString(0),it.getString(1),it.getString(2))
        }
        check(local.third=="CONSENT_REQUIRED")
        val storage=PortableAiStore(ctx,"portable-issue-v1-$account")
        val jobs=storage.read().getJSONObject("jobs")
        val originalJob=jobs.keys().asSequence().single{it.startsWith("dialogue:")&&jobs.getJSONObject(it).optString("record")==issue.id&&(if(extra)jobs.getJSONObject(it).optJSONObject("last_failure")?.optString("code")=="MODEL_CONTEXT_INVALID"else jobs.getJSONObject(it).has("pending"))}
        val pending=if(extra)null else jobs.getJSONObject(originalJob).getJSONObject("pending")
        val operation=pending?.getString("operation").orEmpty()
        val frozen=if(extra){
            val messages=store.messages(sid,owner,account)
            val initialIndex=messages.indexOfFirst{it.origin=="GEMINI_NEW"};check(initialIndex>0)
            val user=messages.take(initialIndex).last{it.role=="USER"&&it.origin=="ASR"}
            val previous=messages[initialIndex]
            val question=messages.single{it.id==local.second}.text
            IssueDialogueContract.payload(issue,question,listOf(IssueDialogueContract.Turn("USER",user.text),IssueDialogueContract.Turn("ASSISTANT",previous.text.substringBefore("\n\n"))))
        }else pending!!.getString("payload")
        check((extra||operation in approved)&&JSONObject(frozen).getJSONObject("selected_record").getString("situation")==issue.situation)
        val jobKey=if(extra)originalJob+":explicit-retry"else originalJob
        val localAttempt=if(extra)local.first+":explicit-retry"else local.first
        if(extra){
            val firstServer=store.readableDatabase.rawQuery("SELECT server_operation FROM operations WHERE session_id=? AND kind='ISSUE' AND state='SUCCEEDED'",arrayOf(sid)).use{check(it.moveToFirst());it.getString(0)}
            val failedServer=approved.single{it!=firstServer}
            store.operation(local.first+":provider-failure",sid,local.second,"DIALOGUE","FAILED",code="MODEL_CONTEXT_INVALID",server=failedServer)
            store.appendOnce(local.second+":provider-failure",sid,"ASSISTANT","LOCAL_RULE","앞서 받은 답변의 대화 연결 정보를 확인하지 못해 표시하지 않았어요. 저장한 문제와 기존 설명은 남아 있어요.",issue.revision,local.second)
            store.operation(localAttempt,sid,local.second,"DIALOGUE","PREPARED")
        }
        val a=inst.startActivitySync(Intent(ctx,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var api:PortableAiClient?=null;var output:SpeechOutput?=null
        try{
            h.click(a,"driver_$owner");h.click(a,"start_driving")
            fun valid()=h.main{h.get(a,"visible")==true&&(h.get(a,"safety") as CarSafetyGate).state==EditingState.ALLOWED&&(h.get(a,"service") as CaritdaService).driver().id==owner&&repo.issue(issue.id)?.revision==issue.revision&&FirebaseSessionProvider.subject()==account}
            ck(h.main{!(h.get(a,"homeVoice") as HomeVoiceRuntime).active},"recovery keeps standby OFF after the user deferred microphone testing")
            val auth=CountDownLatch(1);var uid:String?=null;var bearer:String?=null
            h.main{session.acquire{u,t,_->uid=u;bearer=t;auth.countDown()}}
            check(auth.await(12,TimeUnit.SECONDS)&&uid!=null&&bearer!=null)
            api=session.dialogueClient(storage,uid!!,bearer!!,{valid()},jobKey,issue.id)
            val client=checkNotNull(api)
            val before=client.status();check(!before.optBoolean("uncertain_block")&&before.getInt("remaining")>=1)
            val request=JSONObject(frozen)
            val context=request.getJSONArray("context_turns").let{v->(0 until v.length()).map{v.getJSONObject(it).let{t->IssueDialogueContract.Turn(t.getString("role"),t.getString("text"))}}}
            ck(IssueDialogueContract.payload(issue,request.getString("question"),context)==frozen,if(extra)"explicit extra request uses the same issue/question and recorded prior AI, without sensor facts"else"recovery uses byte-identical approved symptom/question/prior AI context")
            val text=IssueDialogueService.answer(client,issue,request.getString("question"),context){if(valid())repo.issue(issue.id)else null}
            val last=client.jobState().getJSONObject("last");val answer=last.getJSONObject("answer")
            val actualOperation=answer.getString("operation_id")
            ck(guard.getStringSet("operations",emptySet())==approved&&(if(extra)actualOperation !in approved&&supplemental.getStringSet("operations",emptySet())==setOf(actualOperation)else actualOperation==operation),if(extra)"one explicitly approved supplemental operation; original two-operation ledger unchanged"else"actual new dialogue answer uses the ORIGINAL operation, never a third intent")
            ck(answer.getLong("generated_at")>0,"new actual Gemini follow-up response has model metadata and original context")
            store.operation(localAttempt,sid,local.second,"DIALOGUE","SUCCEEDED",code=if(extra)"EXPLICIT_SUPPLEMENTAL_APPROVAL"else"RECOVERED_FROM_CONSENT_REQUIRED",server=actualOperation,model=answer.getString("model"),generatedAt=answer.getLong("generated_at"))
            val message=local.second+if(extra)":explicit-retry-reply"else":recovered";store.appendOnce(message,sid,"ASSISTANT",if(extra)"GEMINI_NEW"else"GEMINI_RECOVERED",text,issue.revision,local.second)
            val delivery=message+":auto";store.playback(delivery,sid,message,"STARTED")
            val spoken=CountDownLatch(1);var outcome:PlaybackOutcome?=null
            h.main{
                output=SpeechOutput(a){valid()}
                WholeExplanationPlayback({valid()},{part,done->output!!.speakOutcome(part,done){}},{output?.stop()}).play(text){
                    outcome=it;store.playback(delivery,sid,message,it.name);spoken.countDown()
                }
            }
            check(spoken.await(300,TimeUnit.SECONDS)){"ORIGINAL_DIALOGUE_TTS_NOT_COMPLETED"}
            ck(outcome==PlaybackOutcome.DONE,if(extra)"entire actual NEW supplemental Gemini follow-up finishes real TTS DONE"else"entire actual recovered Gemini follow-up finishes real TTS DONE")
            val after=client.status()
            ck(after.getInt("portable_attempts")-before.getInt("portable_attempts")==1&&!after.optBoolean("uncertain_block"),"exactly one remaining model attempt consumed, with no uncertain result")
            ck(guard.getStringSet("operations",emptySet())==approved&&repo.issues().size==1,"two-operation cap and the single original issue are unchanged")
            ck(store.messages(sid,owner,account).count{it.origin in setOf("GEMINI_NEW","GEMINI_RECOVERED")}==2,"initial analysis and new recovered follow-up persist in the same encrypted session")
            marker.writeText(JSONObject().put("phase",if(extra)"COMPLETE_EXTRA"else"COMPLETE_RECOVERED").put("run",prefix).toString())
            lines.add("BUDGET recovery_before_remaining=${before.getInt("remaining")} after_remaining=${after.getInt("remaining")} attempts_before=${before.getInt("portable_attempts")} attempts_after=${after.getInt("portable_attempts")} original_operations=2 supplemental_operations=${if(extra)1 else 0}")
            lines.add("BOUNDARIES ASR=PRIOR_EXPLICIT_FIXTURE MODEL=REAL_NEW_FOLLOWUP TTS=REAL_DONE NEW_ISSUES=0 NEW_INTENTS="+if(extra)"ONE_EXPLICITLY_APPROVED"else"0")
        }finally{api?.cancel();h.main{output?.stop();a.finish()};store.close();repo.close()}
        return lines
    }
}
