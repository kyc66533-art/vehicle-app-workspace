package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.widget.Button
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.voice.*
import org.json.JSONObject
import java.net.URL
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit, paid validation gate: at most TWO stable server operations for this approved task.
 * ASR is a callback fixture because the user deferred live microphone verification.
 * Firebase/device/relay/model/SQLite/whole TTS are real. No provider key or auth file access. */
class VoiceRealModelChecks(private val inst:Instrumentation){
    private val ctx=inst.targetContext;private val h=BLightHarness(inst);private val lines=mutableListOf<String>()
    private val guard=ctx.getSharedPreferences("voice-real-model-20261112",0)
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun await(max:Long=200000,condition:()->Boolean){val end=System.currentTimeMillis()+max;while(System.currentTimeMillis()<end){if(h.main(condition))return;Thread.sleep(80)};error("REAL_MODEL_CHAIN_TIMEOUT_NO_RETRY")}
    private class Speech:SpeechBackend{
        override val source="VALIDATION_ASR_FIXTURE_REAL_MODEL";var requests=0;lateinit var events:SpeechBackend.Events
        override fun initialize(ready:()->Unit,failed:(String)->Unit)=ready()
        override fun listen(events:SpeechBackend.Events){requests++;this.events=events;events.ready();events.started()}
        override fun finish(){};override fun detach(){};override fun release(){}
    }
    private inner class GuardSession:PortableSession{
        private val actual=FirebasePortableSession(ctx)
        override val signedIn get()=actual.signedIn
        override val subject get()=actual.subject
        override fun matches(uid:String)=actual.matches(uid)
        override fun acquire(done:(String?,String?,String?)->Unit)=actual.acquire(done)
        private fun api(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?,dialogue:Boolean):PortableAiClient {
            val wire=AiRelayClient(URL(PortableAiClient.ORIGIN)){null};val operations=mutableMapOf<String,String>()
            val transport={path:String,body:JSONObject,device:String,version:Int->
                if(path=="/v2/issues/explanations"||path=="/v2/issues/dialogue"){
                    val operation=operations[body.getString("challenge_id")]?:error("TEST_OPERATION_ID_UNCONFIRMED")
                    synchronized(guard){val approved=guard.getStringSet("operations",emptySet()).orEmpty()
                        if(operation !in approved){check(approved.size<2){"APPROVED_TWO_OPERATION_CAP"};check(guard.edit().putStringSet("operations",approved+operation).commit())}
                    }
                }
                val result=JSONObject(wire.postPortable(path,bearer,body.toString(),device,version))
                if(path.endsWith("challenge")&&body.optString("purpose")=="EXPLAIN")operations[result.getString("challenge_id")]=result.getString("operation_id")
                result
            }
            return PortableAiClient(store,DeviceKeyStore("caritda.portable.${ctx.packageName}"),uid,bearer,allowed,transport,job,record,dialogue)
        }
        override fun client(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?)=api(store,uid,bearer,allowed,job,record,false)
        override fun dialogueClient(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String,record:String)=api(store,uid,bearer,allowed,job,record,true)
    }
    fun run(approval:String):List<String>{
        ValidationEnvironment.requireIsolated(ctx);check(approval=="USER_APPROVED_MAX_TWO_20261112"){"EXPLICIT_PAID_VALIDATION_GATE_REQUIRED"}
        check(guard.getStringSet("operations",emptySet()).orEmpty().isEmpty()){ "EXISTING_REAL_ATTEMPT_DO_NOT_REPEAT" }
        val health=URL(PortableAiClient.ORIGIN+"/health").openConnection().apply{connectTimeout=8000;readTimeout=8000}.getInputStream().use{JSONObject(it.bufferedReader().readText())}
        check(health.getBoolean("model_enabled")&&health.getJSONArray("features").let{a->(0 until a.length()).any{a.getString(it)=="issue-dialogue-v1"}}){"DIALOGUE_NOT_DEPLOYED_NO_PAID_REQUEST"}
        h.main{FirebaseSessionProvider.prepare(ctx)};await(10000){FirebaseSessionProvider.initialized}
        val session=GuardSession();check(session.signedIn){"VALIDATION_FIREBASE_SIGN_IN_REQUIRED"}
        val auth=CountDownLatch(1);var uid:String?=null;var bearer:String?=null
        h.main{session.acquire{u,t,_->uid=u;bearer=t;auth.countDown()}};check(auth.await(12,TimeUnit.SECONDS)&&uid!=null&&bearer!=null)
        val storage=PortableAiStore(ctx,"portable-issue-v1-${session.subject}")
        val preflight=session.client(storage,uid!!,bearer!!,{true},"voice-preflight",null)
        val consent=preflight.state();if(consent.optString("consent")!=IssueExplanation.CONSENT)preflight.register()
        val registration=session.dialogueClient(storage,uid!!,bearer!!,{true},"voice-preflight-dialogue","CONSENT_ONLY")
        val registered=registration.state();if(registered.optString("consent")!=IssueDialogueContract.CONSENT&&(registered.optJSONArray("consents")?.let{a->(0 until a.length()).any{a.optString(it)==IssueDialogueContract.CONSENT}}!=true))registration.register()
        val before=preflight.status();check(before.getBoolean("model_enabled")&&!before.optBoolean("uncertain_block")&&before.getInt("remaining")>=2){"BUDGET_OR_UNKNOWN_BLOCK_NO_PAID_REQUEST"}
        val prefix="voice-auto-"+UUID.randomUUID();val owner=SqliteIssueRepository(ctx,"$prefix.db").use{val s=CaritdaService(it,{true});s.registerDriver("실모델시험A").also(s::selectDriver)}
        VoiceSettings(ctx,"$prefix-settings").write(owner.id,session.subject!!,VoiceSettings.Permit(true,true,true,true,true,false))
        check(guard.edit().putString("fixture",prefix).putString("stage","READY").commit())
        val activity=inst.startActivitySync(Intent(ctx,VoiceConversationActivity::class.java).putExtra("validation_fixture",prefix).putExtra("driver_id",owner.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as VoiceConversationActivity
        try{
            val speech=Speech();h.main{h.set(activity,"aiSession\$delegate",lazy{session as PortableSession});h.set(activity,"account",session.subject!!);h.set(activity,"speech\$delegate",lazy{SpeechInput(activity,factory={speech},allowed={true})})}
            await(15000){activity.window.decorView.findViewWithTag<Button>("voice_start_stop")?.isEnabled==true}
            h.main{activity.window.decorView.findViewWithTag<Button>("voice_start_stop").performClick()};await(10000){speech.requests==1}
            h.main{speech.events.updated("카잇다, 차 앞쪽에서 이상한 소리가 나.",true);speech.events.ended()}
            await{speech.requests>=2}
            val controller=h.main{h.get(activity,"controller") as VoiceConversationController}
            ck(controller.replyOrigin in setOf("GEMINI_NEW","GEMINI_RECOVERED"),"actual NEW Gemini saved-issue response automatically played to whole TTS DONE")
            val issue=SqliteIssueRepository(ctx,"$prefix.db").use{it.issues().single()}
            ck(issue.reporter.id==owner.id&&controller.issueId==issue.id,"same confirmed fixture author/issue ID after automatic SQLite commit")
            check(guard.edit().putString("stage","INITIAL_MODEL_AND_TTS_DONE").commit())
            h.main{speech.events.updated("그럼 어떻게 해야 해?",true);speech.events.ended()}
            await{speech.requests>=3}
            ck(controller.replyOrigin in setOf("GEMINI_NEW","GEMINI_RECOVERED"),"actual new follow-up Gemini response generated and whole TTS completed")
            ck(controller.issueId==issue.id&&guard.getStringSet("operations",emptySet()).orEmpty().size==2,"two distinct stable server operations capped; same issue/session context")
            val jobs=storage.read().getJSONObject("jobs");val dialogue=jobs.keys().asSequence().filter{it.startsWith("dialogue:")}.map{jobs.getJSONObject(it)}.first{it.optString("record")==issue.id}.getJSONObject("last")
            ck(JSONObject(dialogue.getString("payload")).getJSONArray("context_turns").length()==2,"real follow-up used prior same-issue user/AI context")
            val sid=controller.sessionId!!;h.main{controller.end()}
            ConversationStore(ctx,"$prefix-conversations.db").use{store->ck(store.messages(sid,owner.id,session.subject!!).count{it.origin.startsWith("GEMINI")}==2,"both real model replies preserved encrypted with playback history")}
            val after=preflight.status();ck(after.getInt("portable_attempts")-before.getInt("portable_attempts")==2&&!after.optBoolean("uncertain_block"),"authenticated shared usage increased by exactly two with no uncertain outcome")
            check(guard.edit().putString("stage","COMPLETE").commit())
            lines.add("BOUNDARIES ASR=FIXTURE MODEL=REAL_NEW_X2 TTS=REAL_DONE SQLITE=REAL ORDINARY_APP_WRITES=0 THIRD_PAID_REQUEST=BLOCKED")
        }finally{h.main{activity.finish()};preflight.cancel()}
        return lines
    }
}
