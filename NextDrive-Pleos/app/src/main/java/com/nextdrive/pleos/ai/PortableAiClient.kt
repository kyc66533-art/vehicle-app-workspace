package com.nextdrive.pleos.ai

import com.nextdrive.pleos.ai.auth.DeviceKeyStore
import com.nextdrive.pleos.analytics.VehicleAnalytics
import org.json.JSONObject
import java.net.URL
import java.util.UUID

/** Blocking operations run on the Activity's worker. Credential lifetime is one user request. */
class PortableAiClient(private val store:PortableAiStore,private val key:DeviceKeyStore,private val uid:String,private val bearer:String,private val allowed:()->Boolean,
    private val testTransport:((String,JSONObject,String,Int)->JSONObject)?=null) {
    private val http=AiRelayClient(URL(ORIGIN)){null}
    fun cancel(){http.cancel()}
    private val subject=VehicleAnalytics.id(uid)
    fun state():JSONObject {val s=store.read();if(s.length()>0&&s.optString("subject")!=subject)throw PortableFailure("CONSENT_REQUIRED");return s}
    fun hasPending()=state().has("pending")
    private fun post(path:String,body:JSONObject,device:String="",version:Int=1):JSONObject {if(!allowed())throw PortableFailure("AUTH_CANCELLED");return try{testTransport?.invoke(path,body,device,version)?:JSONObject(http.postPortable(path,bearer,body.toString(),device,version))}catch(e:PortableFailure){throw e}catch(e:RelayFailure){throw PortableFailure(e.code)}catch(_:Exception){throw PortableFailure("APP_NETWORK")}}
    private fun challenge(purpose:String,device:String="",operation:String="")=post("/v2/devices/challenge",JSONObject().put("purpose",purpose).put("device_id",device).put("client_intent_id",operation).put("jwk",if(purpose=="REGISTER")key.publicJwk()else JSONObject.NULL),device)
    private fun signed(c:JSONObject,purpose:String,path:String,payload:String=""):JSONObject {
        if(c.getLong("expires_at")*1000<System.currentTimeMillis())throw PortableFailure("CHALLENGE_EXPIRED")
        val proof=DeviceKeyStore.proof(listOf("CARITDA-KEY-PROOF-V1",purpose,uid,c.getString("binding_id"),key.thumbprint(),"POST",ORIGIN,path,c.getString("operation_id"),VehicleAnalytics.id(payload),c.getString("nonce"),c.getLong("expires_at").toString()))
        return JSONObject().put("challenge_id",c.getString("challenge_id")).put("signature",DeviceKeyStore.b64(key.sign(proof)))
    }
    fun register():JSONObject {
        val old=store.read();if(old.length()>0&&old.optString("subject")!=subject)throw PortableFailure("CONSENT_REQUIRED")
        if(old.has("device"))check(old.getString("thumb")==key.thumbprint())else key.ensure()
        val c=challenge("REGISTER");val payload=JSONObject().put("jwk",key.publicJwk()).put("consent_version",PortableAnalysis.CONSENT).toString()
        val result=post("/v2/devices/register",signed(c,"REGISTER","/v2/devices/register",payload).put("registration_payload",payload))
        val s=JSONObject().put("subject",subject).put("device",result.getString("device_id")).put("version",result.getInt("key_version")).put("thumb",key.thumbprint()).put("consent",PortableAnalysis.CONSENT).put("model",PortableAnalysis.MODEL)
        if(!allowed())throw PortableFailure("AUTH_CANCELLED");store.write(s);return s
    }
    fun explain(payload:String):JSONObject {
        val s=state();if(s.optString("consent")!=PortableAnalysis.CONSENT||s.optString("model")!=PortableAnalysis.MODEL)throw PortableFailure("CONSENT_REQUIRED")
        if(s.getString("thumb")!=key.thumbprint())throw PortableFailure("DEVICE_KEY_MISSING")
        val device=s.getString("device");val version=s.getInt("version")
        // Persist an intent BEFORE network. Recover unknown/unfinished work instead of rebilling.
        var pending=s.optJSONObject("pending")
        if(pending==null){pending=JSONObject().put("intent",UUID.randomUUID().toString()).put("payload",payload).put("created",System.currentTimeMillis()).put("state","NEW");s.put("pending",pending);store.write(s)}
        val frozen=pending.getString("payload")
        if(pending.has("operation")){
            val result=read(s,pending,device,version,allowIssued=true)
            if(result.optString("state")!="ISSUED")return result
        }
        val frozenAge=JSONObject(frozen).getJSONObject("window").getLong("latest_age_ms")+System.currentTimeMillis()-pending.getLong("created")
        if(frozenAge !in 0 until 600000){s.remove("pending");store.write(s);throw PortableFailure("STALE_RECEIPT")}
        val c=challenge("EXPLAIN",device,pending.getString("intent"));pending.put("operation",c.getString("operation_id"));store.write(s)
        val result=post("/v2/explanations",signed(c,"EXPLAIN","/v2/explanations",frozen).put("analysis_payload",frozen),device,version)
        pending.put("state",result.optString("state"));store.write(s)
        return read(s,pending,device,version)
    }
    private fun read(s:JSONObject,pending:JSONObject,device:String,version:Int,allowIssued:Boolean=false):JSONObject {
        val operation=pending.getString("operation");val c=challenge("READ",device,operation)
        val result=post("/v2/operations/$operation",signed(c,"READ","/v2/operations/$operation").put("operation_id",operation),device,version)
        val status=result.getString("state");pending.put("state",status)
        if(status=="ISSUED"&&allowIssued){store.write(s);return result}
        if(status=="SUCCEEDED"){
            val answer=result.getJSONObject("result");PortableAnalysis.validate(answer,pending.getString("payload"),operation)
            s.put("last",JSONObject().put("answer",answer).put("payload",pending.getString("payload")).put("saved_at",System.currentTimeMillis()));s.remove("pending");if(!allowed())throw PortableFailure("AUTH_CANCELLED");store.write(s);return answer
        }
        if(status=="FAILED"){s.remove("pending");store.write(s);throw PortableFailure(result.optString("code","MODEL_FAILED"))}
        store.write(s);throw PortableFailure(if(status=="UNKNOWN")"MODEL_OUTCOME_UNKNOWN"else"REQUEST_PENDING")
    }
    fun revoke(){val s=state();val device=s.getString("device");val c=challenge("REVOKE",device);post("/v2/devices/revoke",signed(c,"REVOKE","/v2/devices/revoke").put("operation_id","-"),device,s.getInt("version"));store.withdraw()}
    fun withdrawLocal(){store.withdraw()}
    fun status():JSONObject{val s=state();val device=s.getString("device");val c=challenge("READ",device);return post("/v2/account/status",signed(c,"READ","/v2/account/status").put("operation_id","-"),device,s.getInt("version"))}
    companion object {const val ORIGIN="https://caritda-ai-relay-dev.kyc66533.workers.dev"}
}
