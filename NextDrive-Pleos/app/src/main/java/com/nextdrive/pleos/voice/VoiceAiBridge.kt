package com.nextdrive.pleos.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.model.VehicleIssue
import java.util.concurrent.Executors

/** UI-independent adapter; all entry points retain the same device, cache and server budget. */
class VoiceAiBridge(private val context:Context,private val allowed:()->Boolean,private val current:(String)->VehicleIssue?,private val sessionProvider:()->PortableSession={FirebasePortableSession(context)}) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var generation=0
    @Volatile private var client:PortableAiClient?=null
    @Volatile private var activeStore:PortableAiStore?=null
    /** First-use registration for BOTH contracts, without a symptom or provider dispatch. */
    fun prepareConsents(done:(Result<Int>)->Unit){
        if(!allowed())return done(Result.failure(PortableFailure("AUTH_CANCELLED")))
        val run=generation;val session=sessionProvider()
        session.acquire{uid,bearer,code->
            if(run!=generation||!allowed())return@acquire
            if(uid==null||bearer==null){done(Result.failure(PortableFailure(code?:"AUTH_SIGN_IN_REQUIRED")));return@acquire}
            val subject=session.subject?:return@acquire
            worker.execute{
                val result=runCatching{
                    val store=PortableAiStore(context,"portable-issue-v1-$subject")
                    activeStore=store
                    fun valid()=run==generation&&allowed()&&session.matches(uid)
                    val issue=session.client(store,uid,bearer,{valid()},"voice-consent",null)
                    val dialogue=session.dialogueClient(store,uid,bearer,{valid()},"voice-dialogue-consent","CONSENT_ONLY")
                    for((api,scope) in listOf(issue to IssueExplanation.CONSENT,dialogue to IssueDialogueContract.CONSENT)){
                        client=api;val state=api.state();val scopes=state.optJSONArray("consents")
                        if(state.optString("consent")!=scope&&(scopes==null||(0 until scopes.length()).none{scopes.optString(it)==scope}))api.register()
                    }
                    val status=issue.status();if(status.optBoolean("uncertain_block"))throw PortableFailure("MODEL_OUTCOME_UNKNOWN")
                    if(!status.optBoolean("model_enabled"))throw PortableFailure("MODEL_DISABLED")
                    status.getInt("remaining")
                }
                main.post{if(run==generation&&allowed()&&session.matches(uid))done(result)}
            }
        }
    }
    fun request(issue:VehicleIssue,turn:String,question:String?,contextTurns:List<IssueDialogueContract.Turn>,done:(Result<VoiceConversationController.Reply>)->Unit,recoveryOnly:Boolean=false){
        if(!allowed())return done(Result.failure(PortableFailure("AUTH_CANCELLED")))
        val run=generation;val session=sessionProvider()
        session.acquire{uid,bearer,code->
            if(run!=generation||!allowed())return@acquire
            if(uid==null||bearer==null){done(Result.failure(PortableFailure(code?:"AUTH_SIGN_IN_REQUIRED")));return@acquire}
            val subject=session.subject?:return@acquire
            worker.execute{
                val result=runCatching{
                    fun valid()=run==generation&&allowed()&&session.matches(uid)&&current(issue.id)?.let{IssueExplanation.jobKey(it)==IssueExplanation.jobKey(issue)}==true
                    val store=PortableAiStore(context,"portable-issue-v1-$subject")
                    activeStore=store
                    val api=if(question==null)session.client(store,uid,bearer,{valid()},IssueExplanation.jobKey(issue),issue.id)
                        else session.dialogueClient(store,uid,bearer,{valid()},"dialogue:$turn",issue.id)
                    client=api
                    if(recoveryOnly&&!api.jobState().has("pending")&&!api.jobState().has("last"))throw PortableFailure("NO_PENDING_OPERATION")
                    // This registration is only reached after the separate automation cloud opt-in.
                    // Existing registration adds a scope; it neither replaces the key nor resets usage.
                    val consent=if(question==null)IssueExplanation.CONSENT else IssueDialogueContract.CONSENT
                    val root=api.state();val scopes=root.optJSONArray("consents");if(root.optString("consent")!=consent&&(scopes==null||(0 until scopes.length()).none{scopes.optString(it)==consent}))api.register()
                    fun perform():VoiceConversationController.Reply=if(question==null){
                        val r=IssueAnalysisService.explain(api,issue,issue.situation,false){if(valid())current(issue.id)else null}
                        val answer=api.jobState().getJSONObject("last").getJSONObject("answer")
                        VoiceConversationController.Reply(r.title+"\n"+r.text,when(r.origin){IssueAnalysisService.Origin.NEW->"GEMINI_NEW";IssueAnalysisService.Origin.SAVED->"GEMINI_SAVED";IssueAnalysisService.Origin.RECOVERED->"GEMINI_RECOVERED"},answer.getString("operation_id"),answer.getString("model"))
                    }else {
                        val previous=api.jobState();val origin=if(previous.has("last"))"GEMINI_SAVED"else if(previous.has("pending"))"GEMINI_RECOVERED"else"GEMINI_NEW"
                        val text=IssueDialogueService.answer(api,issue,question,contextTurns){if(valid())current(issue.id)else null}
                        val answer=api.jobState().getJSONObject("last").getJSONObject("answer")
                        VoiceConversationController.Reply(text,origin,answer.getString("operation_id"),answer.getString("model"),answer.getLong("generated_at"))
                    }
                    try{perform()}catch(e:PortableFailure){
                        if(recoveryOnly||!VoiceRateWindow.mayWait(e.code,api.jobState().optJSONObject("pending")))throw e
                        VoiceRateWindow.awaitNextWindow{valid()};perform()
                    }
                }
                main.post{if(run==generation&&allowed()&&session.matches(uid))done(result)}
            }
        }
    }
    fun cancel(){generation++;client?.cancel();client=null;activeStore?.invalidate();activeStore=null}
    fun close(){cancel();worker.shutdown()}
}
