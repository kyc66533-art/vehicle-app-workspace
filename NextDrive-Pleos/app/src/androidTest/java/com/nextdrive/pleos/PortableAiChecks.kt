package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.analytics.*
import com.nextdrive.pleos.vehicle.*
import org.json.JSONObject
import java.util.UUID

/** Isolated fixtures, real Keystore/AES and View checks. Never invokes Firebase/Gemini/Worker. */
class PortableAiChecks(private val inst:Instrumentation){
    private val c=inst.targetContext
    private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T{var value:T?=null;var problem:Throwable?=null;inst.runOnMainSync{try{value=block()}catch(t:Throwable){problem=t}};problem?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    fun run():List<String>{ValidationEnvironment.requireIsolated(c)
        val now=System.currentTimeMillis();val storeId=UUID.randomUUID().toString()
        fun ref(n:Double,at:Long,row:Long,source:ObservationSource=ObservationSource.PLEOS_EMULATOR_SDK)=ObservationRef(storeId,row,VehicleObservation("EV_SOC",n.toString(),"%",at,source,true,true,"fixture",ObservationStatus.RECEIVED,"isolated-fixture",at))
        val a=VehicleAnalytics.analyze("EV_SOC",listOf(ref(80.0,now-2000,1),ref(60.0,now-1000,2),ref(18.0,now,3)),now-3000,now)
        val payload=PortableAnalysis.payload(a,now);val p=JSONObject(payload);val op="o2.$now.${UUID.randomUUID()}"
        verify(p.getJSONObject("facts").getDouble("delta_last_comparable")==-42.0,"PA18 minimized existing analyzer comparable delta")
        verify(p.getJSONObject("window").getInt("sample_count")==3,"PA18 receipt population retained")
        verify(listOf("fixture","driver","VIN","row_id","session","first_received_at","last_received_at",storeId).none{payload.contains(it)},"PA18 excludes identity/raw refs/absolute times")
        verify(runCatching{PortableAnalysis.payload(a,now+600000)}.isFailure,"PA18 stale checked at click time")
        val real=VehicleAnalytics.analyze("EV_SOC",listOf(ref(18.0,now,1,ObservationSource.PLEOS_VEHICLE_SDK)),now-3000,now)
        verify(runCatching{PortableAnalysis.payload(real,now)}.isFailure,"PA18 physical vehicle transfer requires separate approval")
        verify(PortableAnalysis.equal(JSONObject("{\"b\":{\"x\":18},\"a\":true}"),JSONObject("{\"a\":true,\"b\":{\"x\":18.0}}")),"PA19 recursive numeric equivalence and unordered objects")
        verify(!PortableAnalysis.equal(JSONObject("{\"x\":18}"),JSONObject("{\"x\":\"18\"}")),"PA19 numeric string type rejected")
        fun answer()=JSONObject().put("contract",PortableAnalysis.CONTRACT).put("operation_id",op).put("origin","MODEL").put("model",PortableAnalysis.MODEL).put("evidence_digest",VehicleAnalytics.id(payload)).put("facts",JSONObject(payload)).put("explanation","최근 충전량 기록을 확인했어요. 차량 표시와 함께 확인해 주세요.").put("action","CHECK_VEHICLE_DISPLAY")
        verify(PortableAnalysis.validate(answer(),payload,op).isNotBlank(),"PA19 fixture response accepted, not a real AI call")
        for((label,edit)in listOf<Pair<String,(JSONObject)->Unit>>(
            "Mock origin" to {it.put("origin","MOCK")},"other model" to {it.put("model","unknown")},"other evidence" to {it.put("evidence_digest","0".repeat(64))},"other operation" to {it.put("operation_id","other")},"invented number" to {it.put("explanation","충전량은 999%예요.")},"repair judgment" to {it.put("explanation","수리가 완료되어 안전해요.")},"wrong action" to {it.put("action","CHECK_CHARGING_PLAN")},"mismatched latest" to {it.getJSONObject("facts").getJSONObject("facts").put("latest",19)})){
            verify(runCatching{PortableAnalysis.validate(answer().also(edit),payload,op)}.isFailure,"PA19 rejects $label")
        }
        val name="portable-fixture-${UUID.randomUUID()}";val storage=PortableAiStore(c,name)
        storage.write(JSONObject().put("marker","fixture-private-summary").put("last",answer()))
        verify(PortableAiStore(c,name).read().getString("marker")=="fixture-private-summary","PA12 encrypted sidecar reopen")
        val disk=c.getSharedPreferences(name,0).getString("state","")!!
        verify(!disk.contains("fixture-private-summary")&&!disk.contains("explanation"),"PA12 result is ciphertext outside SDK credential store")
        storage.invalidate();verify(runCatching{storage.write(JSONObject())}.isFailure,"PA12 stopped session cannot write a late response")
        val next=PortableAiStore(c,name);next.withdraw();verify(next.read().length()==0,"PA13 withdrawal removes only fixture AI sidecar")
        val fixtureName="portable-client-fixture-${UUID.randomUUID()}";val st=PortableAiStore(c,fixtureName);val key=DeviceKeyStore("caritda.validation.fixture.${UUID.randomUUID()}");var posts=0;var operationState="ISSUED";var drop=true
        val transport={path:String,b:JSONObject,_:String,_:Int->
            if(path.endsWith("challenge"))JSONObject().put("challenge_id",UUID.randomUUID().toString()).put("binding_id",if(b.getString("purpose")=="REGISTER")UUID.randomUUID().toString()else"fixture-device").put("operation_id",if(b.getString("purpose")=="REGISTER")"-"else op).put("nonce",DeviceKeyStore.b64(ByteArray(32){1})).put("expires_at",System.currentTimeMillis()/1000+60).put("key_version",1)
            else if(path.endsWith("register"))JSONObject().put("device_id","fixture-device").put("key_version",1)
            else if(path.endsWith("explanations")){posts++;if(drop){drop=false;throw PortableFailure("APP_NETWORK")};operationState="SUCCEEDED";JSONObject().put("state",operationState)}
            else JSONObject().put("state",operationState).put("result",if(operationState=="SUCCEEDED")answer()else JSONObject.NULL)
        }
        val api=PortableAiClient(st,key,"fixture-subject","fixture-not-bearer",{true},transport)
        api.register();verify(st.read().optString("consent")==PortableAnalysis.CONSENT,"PA13 persistent purpose/model-scoped fixture consent")
        verify(runCatching{api.explain(payload)}.isFailure&&st.read().has("pending"),"PA21 network failure preserves pending intent before dispatch")
        val intent=st.read().getJSONObject("pending").getString("intent")
        verify(api.explain(payload).getString("origin")=="MODEL"&&posts==2,"PA21 ISSUED recovery retries delivery (fixture provider not invoked)")
        verify(!st.read().has("pending")&&st.read().getJSONObject("last").getString("payload")==payload,"PA21 successful result binds exact original evidence")
        verify(!st.read().toString().contains("fixture-not-bearer")&&!st.read().toString().contains("fixture-subject"),"PA12 raw UID and bearer not persisted")
        verify(intent.isNotBlank(),"PA21 stable pre-network idempotency intent exists")
        return lines
    }
    fun ui():List<String>{ValidationEnvironment.requireIsolated(c)
        val a=inst.startActivitySync(Intent().setClassName(c,"com.nextdrive.pleos.PortableAiActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PortableAiActivity
        verify(main{a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0},"PA12 portable account/result screen blocks screenshots")
        Thread.sleep(1500)
        verify(main{a.window.decorView.findViewWithTag<View>("portable_status")!=null},"PA01 actual portable native View renders on Pleos")
        fun views(v:View):List<View> = if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else listOf(v)
        verify(main{views(a.window.decorView).filterIsInstance<TextView>().none{it.text.contains("43자리")||it.text.contains("API 키 입력")}},"PA01 no common token/provider key entry")
        verify(main{views(a.window.decorView).filterIsInstance<Button>().all{it.minHeight>= (76*c.resources.displayMetrics.density).toInt()}},"PA01 existing 76dp action targets")
        main{a.finish()};return lines
    }
}
