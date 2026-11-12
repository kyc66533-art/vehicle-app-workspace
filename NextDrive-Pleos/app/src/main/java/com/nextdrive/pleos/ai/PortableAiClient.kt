package com.nextdrive.pleos.ai

import com.nextdrive.pleos.ai.auth.DeviceKeyStore
import com.nextdrive.pleos.analytics.VehicleAnalytics
import org.json.JSONObject
import java.net.URL
import java.util.UUID

/** Blocking operations run on the Activity's worker. Credential lifetime is one user request. */
class PortableAiClient(private val store:PortableAiStore,private val key:DeviceKeyStore,private val uid:String,private val bearer:String,private val allowed:()->Boolean,
    private val testTransport:((String,JSONObject,String,Int)->JSONObject)?=null,
    private val issueJob:String?=null, private val issueRecord:String?=null,private val dialogue:Boolean=false) {
    private val http=AiRelayClient(URL(ORIGIN)){null}
    fun cancel(){http.cancel()}
    private val subject=VehicleAnalytics.id(uid)
    fun state():JSONObject {val s=store.read();if(s.length()>0&&s.optString("subject")!=subject)throw PortableFailure("CONSENT_REQUIRED");return s}
    private val consent get()=if(dialogue)IssueDialogueContract.CONSENT else if(issueJob==null)PortableAnalysis.CONSENT else IssueExplanation.CONSENT
    private val endpoint get()=if(dialogue)"/v2/issues/dialogue"else if(issueJob==null)"/v2/explanations"else"/v2/issues/explanations"
    private fun job(s:JSONObject):JSONObject {if(issueJob==null)return s;val jobs=s.optJSONObject("jobs")?:JSONObject().also{s.put("jobs",it)};return jobs.optJSONObject(issueJob)?:JSONObject().also{jobs.put(issueJob,it)}}
    fun jobState()=job(state())
    fun previousResults():List<JSONObject> {val root=state();val jobs=root.optJSONObject("jobs")?:return emptyList();return jobs.keys().asSequence().filter{it!=issueJob}.mapNotNull{jobs.optJSONObject(it)}.filter{issueRecord!=null&&it.optString("record")==issueRecord}.mapNotNull{it.optJSONObject("last")}.filter{it.optJSONObject("answer")?.optString("contract")==IssueExplanation.CONTRACT}.toList()}
    fun hasPending()=jobState().has("pending")
    private fun post(path:String,body:JSONObject,device:String="",version:Int=1):JSONObject {if(!allowed())throw PortableFailure("AUTH_CANCELLED");return try{testTransport?.invoke(path,body,device,version)?:JSONObject(http.postPortable(path,bearer,body.toString(),device,version))}catch(e:PortableFailure){throw e}catch(e:RelayFailure){throw PortableFailure(e.code)}catch(_:Exception){throw PortableFailure("APP_NETWORK")}}
    private fun challenge(purpose:String,device:String="",operation:String="")=post("/v2/devices/challenge",JSONObject().put("purpose",purpose).put("device_id",device).put("client_intent_id",operation).put("jwk",if(purpose=="REGISTER")key.publicJwk()else JSONObject.NULL),device)
    private fun signed(c:JSONObject,purpose:String,path:String,payload:String=""):JSONObject {
        if(c.getLong("expires_at")*1000<System.currentTimeMillis())throw PortableFailure("CHALLENGE_EXPIRED")
        val proof=DeviceKeyStore.proof(listOf("CARITDA-KEY-PROOF-V1",purpose,uid,c.getString("binding_id"),key.thumbprint(),"POST",ORIGIN,path,c.getString("operation_id"),VehicleAnalytics.id(payload),c.getString("nonce"),c.getLong("expires_at").toString()))
        return JSONObject().put("challenge_id",c.getString("challenge_id")).put("signature",DeviceKeyStore.b64(key.sign(proof)))
    }
    private fun validate(a:JSONObject,p:String,op:String):String=if(dialogue)IssueDialogueContract.validate(a,p,op)else if(issueJob==null)PortableAnalysis.validate(a,p,op)else IssueExplanation.validate(a,p,op)
    fun register():JSONObject=PortableOperationLock.run{registerLocked()}
    private fun registerLocked():JSONObject {
        val old=store.read();if(old.length()>0&&old.optString("subject")!=subject)throw PortableFailure("CONSENT_REQUIRED")
        if(old.has("device"))check(old.getString("thumb")==key.thumbprint())else key.ensure()
        val c=challenge("REGISTER");val payload=JSONObject().put("jwk",key.publicJwk()).put("consent_version",consent).toString()
        val result=post("/v2/devices/register",signed(c,"REGISTER","/v2/devices/register",payload).put("registration_payload",payload))
        val scopes=(old.optJSONArray("consents")?:org.json.JSONArray()).let{(0 until it.length()).map{index->it.getString(index)}}+listOf(old.optString("consent"),consent)
        val previousConsent=old.optString("consent")
        val s=old.put("consents",org.json.JSONArray(scopes.filter{it.isNotBlank()}.distinct())).put("subject",subject).put("device",result.getString("device_id")).put("version",result.getInt("key_version")).put("thumb",key.thumbprint()).put("consent",if(dialogue&&previousConsent.isNotBlank())previousConsent else consent).put("model",PortableAnalysis.MODEL)
        if(!allowed())throw PortableFailure("AUTH_CANCELLED");store.write(s);return s
    }
    fun explain(payload:String,fresh:Boolean=false):JSONObject=PortableOperationLock.run{explainLocked(payload,fresh)}
    private fun explainLocked(payload:String,fresh:Boolean):JSONObject {
        if(jobState().optJSONObject("pending")?.optString("state")=="PRIVACY_WITHDRAWN")throw PortableFailure("HISTORY_WITHDRAWN")
        if(issueJob!=null&&!fresh){val cached=jobState().optJSONObject("last");if(cached!=null){val answer=cached.getJSONObject("answer");validate(answer,cached.getString("payload"),answer.getString("operation_id"));return answer}}
        val s=state();if((s.optString("consent")!=consent&&!(s.optJSONArray("consents")?.let{a->(0 until a.length()).any{a.optString(it)==consent}}?:false))||s.optString("model")!=PortableAnalysis.MODEL)throw PortableFailure("CONSENT_REQUIRED")
        if(s.getString("thumb")!=key.thumbprint())throw PortableFailure("DEVICE_KEY_MISSING")
        val device=s.getString("device");val version=s.getInt("version")
        // Persist an intent BEFORE network. Recover unknown/unfinished work instead of rebilling.
        val job=job(s)
        if(issueRecord!=null)job.put("record",issueRecord)
        var pending=job.optJSONObject("pending")
        var checked=false
        fun checkIssuePermit(){if(issueJob!=null){val permit=status();if(!permit.getBoolean("model_enabled"))throw PortableFailure("MODEL_DISABLED");if(permit.optBoolean("uncertain_block"))throw PortableFailure("MODEL_OUTCOME_UNKNOWN");s.put("usage_status",permit);if(permit.getInt("remaining")<=0)throw PortableFailure("TOTAL_LIMIT");checked=true}}
        // Authenticate/check model permission without sending any symptom body first.
        if(pending==null)checkIssuePermit()
        if(pending==null){pending=JSONObject().put("intent",UUID.randomUUID().toString()).put("payload",payload).put("created",System.currentTimeMillis()).put("state","NEW");job.put("pending",pending);store.write(s)}
        val frozen=pending.getString("payload")
        if(pending.has("operation")){
            val result=read(s,pending,device,version,allowIssued=true)
            if(result.optString("state")!="ISSUED")return result
        }
        if(!checked)checkIssuePermit()
        val frozenAge=if(issueJob==null)JSONObject(frozen).getJSONObject("window").getLong("latest_age_ms")+System.currentTimeMillis()-pending.getLong("created")else 0L
        if(frozenAge !in 0 until 600000){job(s).remove("pending");store.write(s);throw PortableFailure("STALE_RECEIPT")}
        val c=challenge("EXPLAIN",device,pending.getString("intent"));pending.put("operation",c.getString("operation_id"));store.write(s)
        s.optJSONObject("usage_status")?.put("needs_refresh",true);store.write(s)
        val result=post(endpoint,signed(c,"EXPLAIN",endpoint,frozen).put("analysis_payload",frozen),device,version)
        pending.put("state",result.optString("state"));store.write(s)
        return read(s,pending,device,version)
    }
    private fun read(s:JSONObject,pending:JSONObject,device:String,version:Int,allowIssued:Boolean=false):JSONObject {
        val operation=pending.getString("operation");val c=challenge("READ",device,operation)
        val result=post("/v2/operations/$operation",signed(c,"READ","/v2/operations/$operation").put("operation_id",operation),device,version)
        val status=result.getString("state");pending.put("state",status).put("code",PortableFailure(result.optString("code","REQUEST_PENDING")).code)
        if(status=="ISSUED"&&allowIssued){store.write(s);return result}
        if(status=="SUCCEEDED"){
            val answer=result.getJSONObject("result");validate(answer,pending.getString("payload"),operation)
            val previous=job(s).optJSONObject("last");if(previous!=null){val history=job(s).optJSONArray("history")?:org.json.JSONArray();history.put(previous);job(s).put("history",history)}
            job(s).put("last",JSONObject().put("answer",answer).put("payload",pending.getString("payload")).put("saved_at",System.currentTimeMillis()));job(s).remove("pending");if(!allowed())throw PortableFailure("AUTH_CANCELLED");store.write(s);if(issueJob!=null)runCatching{status()};return answer
        }
        if(status=="FAILED"){job(s).put("last_failure",JSONObject().put("at",System.currentTimeMillis()).put("code",PortableFailure(result.optString("code","MODEL_FAILED")).code));job(s).remove("pending");store.write(s);if(issueJob!=null)runCatching{status()};throw PortableFailure(result.optString("code","MODEL_FAILED"))}
        store.write(s);throw PortableFailure(if(status=="UNKNOWN")"MODEL_OUTCOME_UNKNOWN"else"REQUEST_PENDING")
    }
    fun revoke()=PortableOperationLock.run{val s=state();val device=s.getString("device");val c=challenge("REVOKE",device);val path=if(dialogue)"/v2/issues/dialogue/consent/revoke"else if(issueJob==null)"/v2/devices/revoke"else"/v2/issues/consent/revoke";post(path,signed(c,"REVOKE",path).put("operation_id","-"),device,s.getInt("version"));store.withdraw()}
    fun withdrawLocal(){store.withdraw()}
    fun status():JSONObject=PortableOperationLock.run{val s=state();val device=s.getString("device");val c=challenge("READ",device);val result=post("/v2/account/status",signed(c,"READ","/v2/account/status").put("operation_id","-"),device,s.getInt("version"));result.put("checked_at",System.currentTimeMillis());if(!allowed())throw PortableFailure("AUTH_CANCELLED");val latest=state();latest.put("usage_status",result);store.write(latest);result}
    companion object {const val ORIGIN="https://caritda-ai-relay-dev.kyc66533.workers.dev"}
}
