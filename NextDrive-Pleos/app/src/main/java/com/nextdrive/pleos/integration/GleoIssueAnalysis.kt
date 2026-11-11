package com.nextdrive.pleos.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.analytics.VehicleAnalytics
import com.nextdrive.pleos.model.VehicleIssue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Reuses the relay/consent/key/cache. Deadline is for this Action, not the whole conversation.
 * A timeout never substitutes cached output or starts a second model request. */
class GleoIssueAnalysis(private val context:Context,private val allowed:()->Boolean) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var generation=0
    @Volatile private var active=false
    private var api:PortableAiClient?=null
    private var store:PortableAiStore?=null
    private var ownsLease=false
    fun run(issue:VehicleIssue,fresh:Boolean,remainingMs:Long,current:()->VehicleIssue?,done:(CaritdaActions.Reply)->Unit) {
        if(active||!allowed())return
        val started=SystemClock.elapsedRealtime()
        fun failure(code:String)=CaritdaActions.Reply(if(code in setOf("CONSENT_REQUIRED","AUTH_SIGN_IN_REQUIRED","REQUEST_PENDING","NO_SAVED_EXPLANATION"))"asking"else"fail",
            "The requested AI explanation is not available. Please check CARITDA while parked.",
            when(code){"NO_SAVED_EXPLANATION"->"저장된 AI 설명이 없어요. 새 분석은 앱에서 요청해 주세요.";"ACTION_TIMEOUT"->"음성 응답 대기 시간이 지났어요. 앱에서 요청 상태를 확인해 주세요. 자동으로 다시 요청하지 않아요.";else->PortableFailure(code).displayMessage()},
            JSONArray().put(JSONObject().put("result_code",code).put("new_model_success",false)))
        if(remainingMs<250){done(failure("ACTION_TIMEOUT"));return}
        FirebaseSessionProvider.prepare(context)
        val subject=FirebaseSessionProvider.subject()
        if(subject==null){done(failure("AUTH_SIGN_IN_REQUIRED"));return}
        try {
            val local=PortableAiStore(context,"portable-issue-v1-$subject")
            val root=local.read();val job=root.optJSONObject("jobs")?.optJSONObject(IssueExplanation.jobKey(issue))?:JSONObject()
            if(!fresh){
                val last=job.optJSONObject("last")?:run{done(failure("NO_SAVED_EXPLANATION"));return}
                done(answer(last.getJSONObject("answer"),last.getString("payload"),false,SystemClock.elapsedRealtime()-started));return
            }
            preflight(root,job)?.let{done(failure(it));return}
            IssueExplanation.payload(issue) // category/situation only, never observations.
            if(!lease.compareAndSet(false,true)){done(failure("REQUEST_PENDING"));return}
            ownsLease=true;active=true;val run=++generation
            val timer=Runnable{if(run==generation&&active){cancel();done(failure("ACTION_TIMEOUT"))}}
            main.postDelayed(timer,remainingMs)
            FirebaseSessionProvider.withSession {uid,token,error->
                if(run!=generation||!active||!allowed())return@withSession
                if(error!=null||uid==null||token==null){cancel();done(failure(error?:"AUTH_SIGN_IN_REQUIRED"));return@withSession}
                val st=PortableAiStore(context,"portable-issue-v1-${VehicleAnalytics.id(uid)}");store=st
                val client=PortableAiClient(st,DeviceKeyStore("caritda.portable.${context.packageName}"),uid,token,
                    allowed={run==generation&&active&&allowed()&&FirebaseSessionProvider.matches(uid)},issueJob=IssueExplanation.jobKey(issue),issueRecord=issue.id)
                api=client
                worker.execute {
                    val result=runCatching{
                        val latest=current()?:throw PortableFailure("ISSUE_UNAVAILABLE")
                        if(IssueExplanation.jobKey(latest)!=IssueExplanation.jobKey(issue))throw PortableFailure("ISSUE_CHANGED")
                        if(client.hasPending())throw PortableFailure("REQUEST_PENDING")
                        IssueAnalysisService.explain(client,issue,issue.situation,fresh=true,current=current)
                        val last=client.jobState().getJSONObject("last")
                        answer(last.getJSONObject("answer"),last.getString("payload"),true,SystemClock.elapsedRealtime()-started)
                    }
                    main.post {
                        if(run==generation&&active&&allowed()){
                            main.removeCallbacks(timer);active=false;api=null;store=null;releaseLease()
                            done(result.getOrElse{failure((it as? PortableFailure)?.code?:"AI_REQUEST_FAILED")})
                        }
                    }
                }
            }
        }catch(e:Exception){cancel();done(failure((e as? PortableFailure)?.code?:"AI_REQUEST_FAILED"))}
    }
    private fun answer(value:JSONObject,payload:String,fresh:Boolean,latency:Long):CaritdaActions.Reply {
        val text=IssueExplanation.validate(value,payload,value.getString("operation_id"))
        val data=JSONObject().put("explanation_source",if(fresh)"NEW_GEMINI_MODEL"else"SAVED_GEMINI_MODEL")
            .put("possible_causes",value.getString("possible_causes")).put("checks",value.getString("checks"))
            .put("inspection",value.getString("inspection")).put("diagnosis_confirmed",false).put("elapsed_ms",latency)
        return CaritdaActions.Reply("success",if(fresh)"A new Gemini explanation was generated from the selected symptom; it is not a confirmed diagnosis."else"A previously saved Gemini explanation was read without a new model request.",
            (if(fresh)"새 Gemini 분석\n"else"이전에 저장된 Gemini 설명 · 새 요청 없음\n")+text,JSONArray().put(data))
    }
    private fun releaseLease(){if(ownsLease){ownsLease=false;lease.set(false)}}
    fun cancel(){generation++;active=false;main.removeCallbacksAndMessages(null);api?.cancel();api=null;store?.invalidate();store=null;releaseLease()}
    fun close(){cancel();worker.shutdown()}
    companion object {
        private val lease=AtomicBoolean(false)
        /** Cached policy is a conservative stop only. The existing client checks the server
         * again before any new explanation; absent cached usage is never an approval. */
        fun preflight(root:JSONObject,job:JSONObject):String? {
            if(root.optString("consent")!=IssueExplanation.CONSENT)return "CONSENT_REQUIRED"
            if(job.has("pending"))return "REQUEST_PENDING"
            val usage=root.optJSONObject("usage_status")?:return null
            if(usage.optBoolean("uncertain_block"))return "MODEL_OUTCOME_UNKNOWN"
            if(!usage.optBoolean("model_enabled"))return "MODEL_DISABLED"
            if(!usage.has("remaining")||usage.optInt("remaining")<=0)return "TOTAL_LIMIT"
            return null
        }
    }
}
