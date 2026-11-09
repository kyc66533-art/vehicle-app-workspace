package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.nextdrive.pleos.ai.auth.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** No sign-in credentials, Firebase network requests, Worker requests, or default DB setup. */
class PortableAuthChecks(private val inst:Instrumentation) {
    private val c=inst.targetContext
    private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(f:()->T):T {var value:T?=null;var failure:Throwable?=null;inst.runOnMainSync{try{value=f()}catch(t:Throwable){failure=t}};failure?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    private fun tag(a:AuthCompatibilityActivity,id:String):View?=a.window.decorView.findViewWithTag(id)
    fun run():List<String> {ValidationEnvironment.requireIsolated(c)
        verify(Class.forName("com.google.firebase.auth.FirebaseAuth").name.endsWith("FirebaseAuth"),"PA03 actual Firebase Auth class is packaged, not an SDK stub")
        var a=inst.startActivitySync(Intent().setClassName(c,"com.nextdrive.pleos.AuthCompatibilityActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AuthCompatibilityActivity
        verify(main{a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0},"PA12 login screen blocks screenshots")
        verify(main{val p=tag(a,"auth_password") as EditText;!p.isSaveEnabled&&p.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD!=0},"PA12 password is masked and excluded from saved state")
        verify(main{!(tag(a,"auth_email") as EditText).isSaveEnabled},"PA12 email input is not copied to saved state")
        val state=c.getString(com.nextdrive.pleos.R.string.caritda_firebase_state)
        verify(state in setOf("MISSING","READY","PACKAGE_MISMATCH"),"PA03 public configuration has an explicit state without invented project defaults")
        if(state!="READY")verify(main{!(tag(a,"auth_login") as Button).isEnabled&&!(tag(a,"auth_inspect") as Button).isEnabled},"PA03 missing or mismatched project fails closed without network login")
        verify(FirebaseSessionProvider.error(IllegalStateException("PRIVATE_PASSWORD_OR_TOKEN"))=="AUTH_FAILED","PA12 auth errors never echo exception text")
        main{(tag(a,"auth_password") as EditText).setText("LOCAL_NOT_A_REAL_PASSWORD")}
        val monitor=inst.addMonitor(AuthCompatibilityActivity::class.java.name,null,false)
        main{a.recreate()};a=monitor.waitForActivityWithTimeout(10000) as? AuthCompatibilityActivity?:error("Auth activity recreation unavailable");inst.removeMonitor(monitor)
        verify(main{(tag(a,"auth_password") as EditText).text.isEmpty()},"PA24 activity recreation never restores a password or automatically signs in")
        main{a.finish()}
        val alias="caritda.validation.fixture."+UUID.randomUUID()
        val key=DeviceKeyStore(alias);key.ensure();val thumb=key.thumbprint();key.ensure()
        verify(DeviceKeyStore(alias).thumbprint()==thumb,"PA12 repeated key setup preserves the existing key")
        val jwk=key.publicJwk()
        verify(jwk.getString("x").length==43&&jwk.getString("y").length==43&&!jwk.has("d"),"PA12 P-256 public JWK exports coordinates only")
        verify(thumb.length==43,"PA12 RFC7638 thumbprint is base64url SHA256")
        val fields=listOf("CARITDA-KEY-PROOF-V1","EXPLAIN","TEST-uid-카잇다","fixture-device",thumb,"POST","https://caritda-ai-relay-dev.kyc66533.workers.dev","/v2/explanations","o2.fixture","fixture-payload-digest",DeviceKeyStore.b64(ByteArray(32){it.toByte()}),"2000000000")
        val proof=DeviceKeyStore.proof(fields);val signature=key.sign(proof)
        verify(signature.size==64&&key.verify(proof,signature),"PA08 actual Pleos Keystore signature round-trips DER/P1363")
        verify(!key.verify(DeviceKeyStore.proof(fields.toMutableList().apply{set(7,"/v2/devices/register")}),signature),"PA08 changed path cannot reuse a device proof")
        verify(!key.verify(DeviceKeyStore.proof(fields.toMutableList().apply{set(1,"REGISTER")}),signature),"PA08 changed purpose cannot reuse a device proof")
        val input=java.io.DataInputStream(proof.inputStream());val decoded=fields.map{val n=input.readInt();ByteArray(n).also{input.readFully(it)}.toString(Charsets.UTF_8)}
        verify(decoded==fields&&input.available()==0,"PA08 proof is twelve UTF-8 byte-length-prefixed fields, including Korean")
        val high=ByteArray(64).apply{this[0]=0x80.toByte();this[31]=1;this[63]=1}
        verify(DeviceKeyStore.derToP1363(DeviceKeyStore.p1363ToDer(high)).contentEquals(high),"PA08 DER leading sign zero and short scalar normalize correctly")
        verify(runCatching{DeviceKeyStore.p1363ToDer(ByteArray(64))}.isFailure,"PA08 zero ECDSA scalar rejected")
        verify(runCatching{DeviceKeyStore.derToP1363(byteArrayOf(0x30,6,2,1,0x80.toByte(),2,1,1))}.isFailure,"PA08 negative DER integer rejected")
        verify(runCatching{DeviceKeyStore.derToP1363(byteArrayOf(0x30,7,2,2,0,1,2,1,1))}.isFailure,"PA08 redundant DER sign padding rejected")
        verify(runCatching{DeviceKeyStore.derToP1363(DeviceKeyStore.p1363ToDer(high)+byteArrayOf(0))}.isFailure,"PA08 trailing DER bytes rejected")
        verify(runCatching{DeviceKeyStore.proof(fields.dropLast(1))}.isFailure,"PA08 incomplete proof contract rejected")
        val vector=JSONObject().put("fixture",true).put("alias",alias).put("thumbprint",thumb).put("jwk",jwk).put("fields",JSONArray(fields)).put("proof",DeviceKeyStore.b64(proof)).put("signature",DeviceKeyStore.b64(signature))
        c.openFileOutput("portable-key-vector.json",0).use{it.write(vector.toString().toByteArray())}
        lines.add("NOTE Firebase sign-in/session refresh and actual Worker verification require real project configuration; not passed by local checks")
        return lines
    }
    fun cold():List<String>{ValidationEnvironment.requireIsolated(c)
        val v=JSONObject(c.getFileStreamPath("portable-key-vector.json").readText());val alias=v.getString("alias")
        check(v.getBoolean("fixture")&&alias.startsWith("caritda.validation.fixture."))
        val key=DeviceKeyStore(alias)
        verify(key.thumbprint()==v.getString("thumbprint"),"PA12 existing validation fixture key restores after process restart")
        fun bytes(k:String)=android.util.Base64.decode(v.getString(k),android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        verify(key.verify(bytes("proof"),bytes("signature")),"PA08 previous signature verifies after process restart without generating a replacement key")
        return lines
    }
    /** Human enters credentials in the protected TEST screen; instrumentation never reads them. */
    fun interactive():List<String>{ValidationEnvironment.requireIsolated(c)
        check(c.getString(com.nextdrive.pleos.R.string.caritda_firebase_state)=="READY"){"AUTH_PROJECT_REQUIRED"}
        val a=inst.startActivitySync(Intent().setClassName(c,"com.nextdrive.pleos.AuthCompatibilityActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AuthCompatibilityActivity
        val end=System.currentTimeMillis()+300000
        try {
            while(System.currentTimeMillis()<end&&!main{FirebaseSessionProvider.snapshot().signedIn&&!FirebaseSessionProvider.busy})Thread.sleep(300)
            verify(main{FirebaseSessionProvider.snapshot().signedIn&&!FirebaseSessionProvider.busy},"PA03 real Firebase sign-in completed (SDK idle), not just an intermediate user event")
            val ready=java.util.concurrent.CountDownLatch(1);val outcome=java.util.concurrent.atomic.AtomicReference<FirebaseSessionProvider.Result>()
            main{FirebaseSessionProvider.inspectToken{outcome.set(it);ready.countDown()}}
            check(ready.await(35,java.util.concurrent.TimeUnit.SECONDS)){"AUTH_RESULT_TIMEOUT"}
            verify(outcome.get().tokenAvailable,"PA03 Firebase SDK ID token obtained [${outcome.get().code}]; token not exported")
        } finally {main{a.finish()};inst.waitForIdleSync();Thread.sleep(1000)}
        return lines
    }
    /** Run only after the user manually signed in to this TEST app. Never accepts credentials. */
    fun signedIn():List<String>{ValidationEnvironment.requireIsolated(c)
        val state=main{FirebaseSessionProvider.prepare(c)}
        val deadline=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<deadline&&!main{FirebaseSessionProvider.initialized&&!FirebaseSessionProvider.busy})Thread.sleep(100)
        val restored=main{FirebaseSessionProvider.snapshot()}
        verify(restored.signedIn,"PA03 Firebase currentUser restores after initialization [${state.code}/${restored.code}]")
        fun inspect(force:Boolean):FirebaseSessionProvider.Result {
            val ready=java.util.concurrent.CountDownLatch(1);val value=java.util.concurrent.atomic.AtomicReference<FirebaseSessionProvider.Result>()
            main{FirebaseSessionProvider.inspectToken(force){value.set(it);ready.countDown()}}
            check(ready.await(35,java.util.concurrent.TimeUnit.SECONDS)){"AUTH_RESULT_TIMEOUT"};return value.get()
        }
        val cached=inspect(false)
        verify(cached.tokenAvailable&&(cached.expiresAtSeconds?:0)>System.currentTimeMillis()/1000,"PA03 current ID token [${cached.code}]; raw value not exported")
        val renewed=inspect(true)
        verify(renewed.tokenAvailable&&(renewed.expiresAtSeconds?:0)>System.currentTimeMillis()/1000&&(renewed.issuedAtSeconds?:0)>=(cached.issuedAtSeconds?:0),"PA10 explicit compatibility refresh [${renewed.code}]; normal requests retain cached-token flow")
        verify(main{FirebaseSessionProvider.snapshot().signedIn},"PA03 session remains signed in after token inspection and refresh")
        return lines
    }
}
