package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.widget.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class SocPocChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,msg:String){check(ok){msg};lines.add("PASS $msg")}
    private fun <T> main(f:()->T):T{var v:T?=null;var e:Throwable?=null;inst.runOnMainSync{try{v=f()}catch(t:Throwable){e=t}};e?.let{throw it};@Suppress("UNCHECKED_CAST")return v as T}
    private fun await(msg:String,seconds:Int=30,f:()->Boolean){val end=System.currentTimeMillis()+seconds*1000;while(System.currentTimeMillis()<end){if(f())return;Thread.sleep(100)};error(msg)}
    private fun tag(a:MainActivity,k:String):View?=a.window.decorView.findViewWithTag(k)
    private fun click(a:MainActivity,k:String){ValidationUiActions.reveal(inst,a,k);main{val v=tag(a,k)?:error("Missing $k");check(v.isEnabled);v.performClick()};inst.waitForIdleSync()}
    private fun stage(phase:String,raw:ObservationStore,notices:LowSocStore){c.openFileOutput("soc-live-stage.json",0).use{it.write(JSONObject().put("phase",phase).put("raw",raw.refs().size).put("changes",notices.changeCount()).put("notices",notices.notices().size).put("at",System.currentTimeMillis()).toString().toByteArray())}}
    fun live():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val raw=ObservationStore(c);val notices=LowSocStore(c);val repo=SqliteIssueRepository(c)
        val a=inst.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(c,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        await("parked UI allowed"){main{tag(a,"driver_A")?.isEnabled==true}}
        click(a,"driver_A");click(a,"start_driving")
        await("entry SDK batch"){raw.latest(3).size==3}
        verify(raw.mostRecent("EV_SOC",true)?.raw=="80","LIVE Console 80 percent is actually received by SDK GET")
        val issueRows=repo.issues();click(a,"nav_VEHICLE");click(a,"soc_notices")
        main{(tag(a,"soc_threshold") as EditText).setText("20")};click(a,"enable_soc")
        await("observer initial SDK get"){raw.refs().any{it.value.raw=="80"&&it.value.detail.contains("SOC GET")}}
        verify(notices.notices().isEmpty(),"LIVE initial normal SOC creates no low warning")
        stage("READY_80",raw,notices)
        await("Console 18 callback did not arrive",120){raw.refs().any{it.value.raw=="18"&&it.value.detail.contains("SOC CALLBACK")}}
        await("low card derived"){notices.notices().size==1}
        val notice=notices.notices().single();val callback=raw.refs().first{it.value.raw=="18"&&it.value.detail.contains("SOC CALLBACK")}
        verify(notice.phase=="LOW"&&notice.threshold==20&&notice.store==callback.storeId&&notice.row==callback.rowId,"LIVE actual 18 callback creates one evidence-linked user-threshold episode")
        verify(callback.value.source==ObservationSource.PLEOS_EMULATOR_SDK&&callback.value.unit=="%"&&callback.value.receivedAt!=null,"LIVE callback retains actual source unit receipt time and immutable raw row")
        stage("LOW_18",raw,notices)
        Thread.sleep(1500)
        click(a,"nav_VEHICLE");await("manual request ready"){main{tag(a,"read_vehicle")?.isEnabled==true}};click(a,"read_vehicle")
        await("repeated SDK18 get completes"){main{tag(a,"read_vehicle")?.isEnabled==true}}
        verify(raw.mostRecent("EV_SOC",true)?.raw=="18"&&notices.notices().single().id==notice.id&&notices.changeCount()==1,"LIVE repeated actual SDK18 GET preserves one episode without duplicate changes")
        click(a,"soc_notices");stage("REPEAT_DONE",raw,notices)
        await("Console45 callback did not arrive",120){notices.notices().singleOrNull()?.phase=="EXITED"}
        val exit=notices.notices().single()
        verify(exit.id==notice.id&&exit.value=="45"&&notices.changeCount()==2&&raw.refs().any{it.rowId==exit.row&&it.value.raw=="45"&&it.value.detail.contains("CALLBACK")},"LIVE actual45 callback records condition exit on same episode, never repair completion")
        verify(repo.issues()==issueRows,"LIVE automatic rules never change manual issues or their four states")
        c.getSharedPreferences("soc_live_cold",0).edit().putString("episode",exit.id).putInt("changes",notices.changeCount()).commit()
        val image=inst.uiAutomation.takeScreenshot();if(image!=null)c.openFileOutput("soc-live.png",0).use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        main{a.finish()};inst.waitForIdleSync();val count=raw.refs().size
        stage("STOPPED",raw,notices);Thread.sleep(2000)
        verify(raw.refs().size==count&&notices.readableDatabase.rawQuery("SELECT count(*) FROM gaps",null).use{it.moveToFirst();it.getInt(0)>0},"LIVE onStop unregisters callback and records an observation gap")
        raw.close();notices.close();repo.close();return lines
    }
    fun rules():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val fixture=java.util.UUID.randomUUID().toString()
        val raw=ObservationStore(c,"soc-fixture-raw-$fixture.db");val notices=LowSocStore(c,"soc-fixture-notices-$fixture.db")
        val now=System.currentTimeMillis()
        fun value(n:Int)=VehicleObservation("EV_SOC",n.toString(),"%",now,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"SYNTHETIC_VALIDATION_ONLY",ObservationStatus.RECEIVED,"validation fixture only",now)
        raw.append(value(15));notices.configure(true,20,raw);notices.reconcile(raw,now)
        verify(notices.notices().isEmpty(),"RULE enabling never replays a historical low into a new episode")
        raw.append(value(80));notices.reconcile(raw,now);verify(notices.notices().isEmpty(),"RULE normal80 is not a fault or low notice")
        raw.append(value(18));notices.reconcile(raw,now);val id=notices.notices().single().id
        repeat(8){raw.append(value(18));notices.reconcile(raw,now)}
        verify(notices.notices().size==1&&notices.changeCount()==1,"RULE repeated18 and replay produce one episode and one entry")
        notices.configure(true,21,raw);raw.append(value(18));notices.reconcile(raw,now)
        verify(notices.notices().single().id==id&&notices.changeCount()==1&&notices.notices().single().threshold==21,"RULE policy version change preserves same active concern instead of rearming")
        notices.configure(true,20,raw)
        raw.append(value(20));notices.reconcile(raw,now);verify(notices.notices().single().phase=="LOW","RULE strict less-than20 and explicit2-point exit margin avoid boundary chatter")
        raw.append(value(45));notices.reconcile(raw,now);verify(notices.notices().single().phase=="EXITED"&&notices.changeCount()==2,"RULE45 exits the condition without resolving an issue")
        verify(SocRule.assess(value(101),20,false,now)==SocAssessment.INVALID_DATA&&SocRule.assess(value(18).copy(receivedAt=now-600000),20,false,now)==SocAssessment.STALE_OR_INSUFFICIENT,"RULE invalid and stale values cannot create a low event")
        raw.append(value(18));notices.reconcile(raw,now);val active=notices.notices().first().id
        val failure=VehicleObservation("EV_SOC",null,null,null,ObservationSource.PLEOS_EMULATOR_SDK,null,false,"SYNTHETIC_VALIDATION_ONLY",ObservationStatus.CONNECTION_FAILED,"fixture failure",now)
        raw.append(failure);notices.reconcile(raw,now);verify(notices.notices().first().id==active&&notices.notices().first().phase=="LOW","RULE connection failure is not condition exit")
        val before=notices.changeCount();raw.append(value(45));val failed=runCatching{notices.reconcile(raw,now){error("fixture transaction failure")}}
        verify(failed.isFailure&&notices.changeCount()==before&&raw.mostRecent("EV_SOC",true)?.raw=="45","RULE derived transaction failure retains original raw and rolls back event")
        notices.reconcile(raw,now);verify(notices.changeCount()==before+1,"RULE same raw reference retries exactly once after rollback")
        val input=ExplanationInput.from(notices.notices().first());val body=JSONObject().put("request_id",input.requestId).put("origin","MOCK").put("model","validation-mock").put("text","검증용 응답입니다. 실제 AI 설명이 아닙니다.").put("informational_only",true)
        verify(ExplanationContract.parse(body.toString(),input,12,true).origin=="MOCK"&&runCatching{ExplanationContract.parse(body.toString(),input,12)}.isFailure,"AI Mock stays explicitly separate and production parser refuses Mock")
        verify(listOf("운전자","VIN","latitude","store_id","row_id").none{input.json().contains(it)},"AI minimized input excludes driver vehicle ID location and local database references")
        verify(runCatching{ExplanationContract.parse(body.put("origin","MODEL").put("text","수리가 완료되었습니다. 운행이 안전합니다.").toString(),input,12)}.isFailure,"AI confirmed repair and safe-driving text is rejected by validation guard")
        val started=android.os.SystemClock.elapsedRealtime()
        val network=runCatching{val conn=URL("https://document.pleos.ai/").openConnection() as HttpsURLConnection;try{conn.connectTimeout=8000;conn.readTimeout=8000;conn.requestMethod="HEAD";"HTTP ${conn.responseCode}, TLS cipher ${conn.cipherSuite}"}finally{conn.disconnect()}}
        lines.add("INFO NETWORK_DIAGNOSTIC result=${network.getOrElse{it.javaClass.simpleName}} latencyMs=${android.os.SystemClock.elapsedRealtime()-started}; no model authentication or user data transmitted")
        check(c.getSharedPreferences("soc_rule_cold",0).edit().putString("episode",id).commit())
        raw.close();notices.close();return lines
    }
    fun cold():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val notices=LowSocStore(c);val p=c.getSharedPreferences("soc_live_cold",0)
        verify(notices.notices().single().id==p.getString("episode",null)&&notices.notices().single().phase=="EXITED","COLD actual SDK episode and exit survive full process stop")
        verify(notices.changeCount()==p.getInt("changes",-1),"COLD episode change history survives process stop without duplicate entry")
        notices.close();return lines
    }
}
