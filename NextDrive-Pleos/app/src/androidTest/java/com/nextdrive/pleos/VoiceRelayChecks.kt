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
