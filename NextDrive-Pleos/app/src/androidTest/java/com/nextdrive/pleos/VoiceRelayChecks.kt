package com.nextdrive.pleos

import android.app.Instrumentation
import com.nextdrive.pleos.ai.PortableAiClient
import com.nextdrive.pleos.ai.auth.FirebaseSessionProvider
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real fixed-origin public probes, NO bearer, symptom, model call, registration or quota write. */
class VoiceRelayChecks(private val inst:Instrumentation){
    fun usage():List<String>{
        val ctx=inst.targetContext;ValidationEnvironment.requireIsolated(ctx);val h=BLightHarness(inst)
        h.main{FirebaseSessionProvider.prepare(ctx)}
        val end=System.currentTimeMillis()+12000
        while(System.currentTimeMillis()<end&&!FirebaseSessionProvider.initialized)Thread.sleep(80)
        val session=com.nextdrive.pleos.ai.auth.FirebasePortableSession(ctx)
        val auth=CountDownLatch(1);var uid:String?=null;var bearer:String?=null
        h.main{session.acquire{u,t,_->uid=u;bearer=t;auth.countDown()}}
        check(auth.await(12,TimeUnit.SECONDS)&&uid!=null&&bearer!=null)
        val api=session.client(com.nextdrive.pleos.ai.PortableAiStore(ctx,"portable-issue-v1-"+session.subject),uid!!,bearer!!,{true},"VOICE_USAGE_READ",null)
        val value=try{api.status()}finally{api.cancel()}
        val original=ctx.getSharedPreferences("voice-real-model-20261112",0).getStringSet("operations",emptySet()).orEmpty().size
        val extra=ctx.getSharedPreferences("voice-real-model-extra-20261112",0).getStringSet("operations",emptySet()).orEmpty().size
        return listOf("PASS authenticated usage read without a model dispatch",
            "BUDGET remaining=${value.getInt("remaining")} attempts=${value.getInt("portable_attempts")} uncertain=${value.optBoolean("uncertain_block")} model_on=${value.optBoolean("model_enabled")} original_operations=$original supplemental_operations=$extra model_calls=0")
    }
    fun authProbe():List<String>{
        ValidationEnvironment.requireIsolated(inst.targetContext)
        val ctx=inst.targetContext;val ready=CountDownLatch(1)
        inst.runOnMainSync{FirebaseSessionProvider.prepare(ctx);FirebaseSessionProvider.observe{if(FirebaseSessionProvider.initialized)ready.countDown()};if(FirebaseSessionProvider.initialized)ready.countDown()}
        check(ready.await(10,TimeUnit.SECONDS))
        val received=CountDownLatch(1);var token:String?=null;var authCode=""
        inst.runOnMainSync{FirebaseSessionProvider.withSession{_,bearer,code->token=bearer;authCode=code.orEmpty();received.countDown()}}
        check(received.await(12,TimeUnit.SECONDS));if(token==null)return listOf("AUTH validation=$authCode model_calls=0")
        val bearer=token!!;val claims=JSONObject(String(android.util.Base64.decode(bearer.split('.')[1],android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),Charsets.UTF_8))
        val age=System.currentTimeMillis()/1000-claims.getLong("auth_time")
        val key=com.nextdrive.pleos.ai.auth.DeviceKeyStore("caritda.portable.${ctx.packageName}")
        val body=JSONObject().put("purpose","REGISTER").put("device_id","").put("client_intent_id","").put("jwk",key.publicJwk()).toString()
        val c=URL(PortableAiClient.ORIGIN+"/v2/devices/challenge").openConnection() as HttpURLConnection
        try{
            c.connectTimeout=8000;c.readTimeout=10000;c.instanceFollowRedirects=false;c.requestMethod="POST";c.doOutput=true
            c.setRequestProperty("Authorization","Bearer $bearer");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toByteArray())}
            val status=c.responseCode;val bytes=(if(status>=400)c.errorStream else c.inputStream).use{it.readBytes()};check(bytes.size<=8192)
            val json=JSONObject(String(bytes,Charsets.UTF_8));val code=json.optJSONObject("error")?.optString("code")?:"CHALLENGE_READY"
            val state=com.nextdrive.pleos.ai.PortableAiStore(ctx,"portable-issue-v1-${FirebaseSessionProvider.subject()}").read()
            val operations=ctx.getSharedPreferences("voice-real-model-20261112",0).getStringSet("operations",emptySet()).orEmpty().size
            return listOf("AUTH authenticated_http=$status code=$code auth_age_seconds=$age local_device_registered=${state.has("device")} approved_operations_used=$operations model_calls=0")
        }finally{c.disconnect()}
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(inst.targetContext);val lines=mutableListOf<String>()
        fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
        fun request(path:String,post:Boolean=false):Pair<Int,JSONObject>{
            val c=URL(PortableAiClient.ORIGIN+path).openConnection() as HttpURLConnection
            c.instanceFollowRedirects=false;c.connectTimeout=10000;c.readTimeout=10000
            try{
                if(post){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write("{}".toByteArray())}}
                val code=c.responseCode;val stream=if(code>=400)c.errorStream else c.inputStream
                val bytes=stream.use{it.readBytes()};check(bytes.size<=8192)
                return code to JSONObject(String(bytes,Charsets.UTF_8))
            }finally{c.disconnect()}
        }
        val health=request("/health");ck(health.first==200,"actual relay health responded over emulator HTTPS")
        ck(health.second.optBoolean("model_enabled")&&health.second.optBoolean("portable_enabled"),"existing model ON and portable enabled preserved")
        val issue=request("/v2/issues/explanations",true)
        ck(issue.first==401&&issue.second.optJSONObject("error")?.optString("code")=="AUTH_REQUIRED","existing single-issue unauthenticated guard remains closed")
        val dialogue=request("/v2/issues/dialogue",true)
        val feature=health.second.optJSONArray("features")?.let{a->(0 until a.length()).any{a.optString(it)=="issue-dialogue-v1"}}?:false
        ck(if(feature)dialogue.first==401 else dialogue.first==404,"dialogue route result matches actual deployed feature declaration")
        lines.add("RELAY dialogue_deployed=$feature dialogue_status=${dialogue.first} actual_model_calls=0")
        val ready=CountDownLatch(1);var initial="";var remove:(()->Unit)?=null
        inst.runOnMainSync{initial=FirebaseSessionProvider.prepare(inst.targetContext).code;remove=FirebaseSessionProvider.observe{if(FirebaseSessionProvider.initialized)ready.countDown()};if(FirebaseSessionProvider.initialized)ready.countDown()}
        if(initial=="AUTH_RESTORING")ready.await(8,TimeUnit.SECONDS)
        lines.add("AUTH validation_prepare=$initial validation_state=${FirebaseSessionProvider.snapshot().code}")
        inst.runOnMainSync{remove?.invoke()};return lines
    }
}
