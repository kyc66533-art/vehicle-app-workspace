package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.widget.Button
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.ai.IssueExplanation
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.vehicle.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

/** Synthetic, private fixture databases; no default preparation, model call or broadcast. */
class GleoIntegrationChecks(private val inst:Instrumentation) {
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun checkCase(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T{var v:T?=null;var e:Throwable?=null;inst.runOnMainSync{try{v=block()}catch(t:Throwable){e=t}};e?.let{throw it};@Suppress("UNCHECKED_CAST")return v as T}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val schema=JSONObject(c.assets.open("schema.json").bufferedReader().use{it.readText()})
        val actions=schema.getJSONArray("actionCalls")
        checkCase(schema.getString("packageName")==c.packageName&&actions.length()==3,"GL01 validation schema identity and three actions")
        val names=(0 until actions.length()).map{actions.getJSONObject(it).getJSONObject("function").getString("name")}.toSet()
        checkCase(names==CaritdaActions.functions,"GL02 action/schema dispatch agrees")
        val query=Intent(CaritdaActions.QUERY).putExtra("ACTION_PARAM","{\"topic\":\"battery\"}")
        checkCase(CaritdaActions.parse(query).topic=="battery","GL03 object ACTION_PARAM")
        checkCase(CaritdaActions.parse(Intent(CaritdaActions.QUERY).putExtra("ACTION_PARAM",JSONObject.quote("{\"topic\":\"vehicle\"}"))).topic=="vehicle","GL04 official quoted JSON example")
        checkCase(CaritdaActions.parse(Intent(CaritdaActions.QUERY).putExtra("topic","handover")).topic=="handover","GL05 individual documented extras")
        fun rejected(intent:Intent)=runCatching{CaritdaActions.parse(intent)}.isFailure
        checkCase(rejected(Intent("DELETE_CARITDA_ISSUE")),"GL06 unknown function denied")
        checkCase(rejected(Intent(CaritdaActions.QUERY).putExtra("ACTION_PARAM","{\"topic\":\"battery\",\"delete\":true}")),"GL07 unsupported parameter denied")
        checkCase(rejected(Intent(CaritdaActions.QUERY).putExtra("ACTION_PARAM","{\"topic\":18}")),"GL08 invalid type denied")
        checkCase(rejected(Intent(query).putExtra("topic","handover")),"GL09 conflicting inputs denied")
        checkCase(rejected(Intent(CaritdaActions.DRAFT).putExtra("situation","x".repeat(801))),"GL10 excessive draft denied")
        checkCase(rejected(Intent(CaritdaActions.QUERY).putExtra("ACTION_PARAM","x".repeat(4097))),"GL11 oversized JSON denied")
        checkCase(CaritdaActions.parse(Intent(CaritdaActions.DRAFT)).situation.isEmpty(),"GL12 absent symptom remains empty not guessed")
        checkCase(!CaritdaActions.parse(Intent(CaritdaActions.ANALYZE).putExtra("record_query","소음")).fresh,"GL13 analysis default never requests fresh model")
        checkCase(CaritdaActions.parse(Intent(CaritdaActions.ANALYZE).putExtra("record_query","소음").putExtra("request_mode","new")).fresh,"GL14 explicit new analysis remains distinct")
        checkCase(rejected(Intent(CaritdaActions.ANALYZE).putExtra("request_mode","force")),"GL15 cannot override model policy")
        val callback=CaritdaActions.callback(CaritdaActions.QUERY,"success","The received values were read.",JSONArray().put(JSONObject().put("availability","NOT_RECEIVED")),"voice")
        checkCase(callback.get("data") is String&&JSONArray(callback.getString("data")).getJSONObject(0).getString("availability")=="NOT_RECEIVED","GL16 callback data is encoded object array")
        checkCase(callback.getString("mode")=="voice"&&callback.getString("functionName")==CaritdaActions.QUERY,"GL17 official voice callback fields")
        checkCase(runCatching{CaritdaActions.callback(CaritdaActions.QUERY,"pending","Please wait.")}.isFailure,"GL18 unsupported callback status rejected")
        checkCase(runCatching{CaritdaActions.callback(CaritdaActions.QUERY,"success","Read complete.",JSONArray().put("raw"))}.isFailure,"GL19 non-object data rejected")
        val root=JSONObject().put("consent",IssueExplanation.CONSENT);val job=JSONObject()
        checkCase(GleoIssueAnalysis.preflight(JSONObject(),job)=="CONSENT_REQUIRED","GL20 new analysis requires existing issue consent")
        checkCase(GleoIssueAnalysis.preflight(root,JSONObject().put("pending",JSONObject()))=="REQUEST_PENDING","GL21 pending cannot become new request")
        val usage=JSONObject().put("uncertain_block",true).put("model_enabled",true).put("remaining",5);root.put("usage_status",usage)
        checkCase(GleoIssueAnalysis.preflight(root,job)=="MODEL_OUTCOME_UNKNOWN","GL22 UNKNOWN blocks automatic retry")
        usage.put("uncertain_block",false).put("model_enabled",false)
        checkCase(GleoIssueAnalysis.preflight(root,job)=="MODEL_DISABLED","GL23 model OFF cannot be bypassed")
        usage.put("model_enabled",true).put("remaining",0)
        checkCase(GleoIssueAnalysis.preflight(root,job)=="TOTAL_LIMIT","GL24 zero remaining blocks new request")
        usage.put("remaining",5)
        checkCase(GleoIssueAnalysis.preflight(root,job)==null,"GL25 cached preflight permits server check only")
        val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
        SqliteIssueRepository(c,"gleo-contract-$id.db").use{repo->ObservationStore(c,"gleo-obs-$id.db").use{obs->
            val s=CaritdaService(repo,{true});val author=s.registerDriver("음성작성 fixture");val reader=s.registerDriver("인계 fixture");s.selectDriver(author)
            val issue=s.record(SymptomType.NOISE,"선택한 증상 fixture",now,"전송하지 않을 메모")
            val payload=JSONObject(IssueExplanation.payload(issue)).toString()
            checkCase(!payload.contains(issue.id)&&!payload.contains(issue.memo)&&!payload.contains(issue.reporter.label),"GL26 AI payload excludes ID author and memo")
            val empty=CaritdaActions.queryReply("battery",s,obs).data.getJSONObject(0)
            checkCase(empty.getString("availability")=="NOT_RECEIVED"&&!empty.has("value"),"GL27 missing battery is never zero")
            obs.append(VehicleObservation("EV_SOC","80","%",now-1000,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"SYNTHETIC_VALIDATION_ONLY",ObservationStatus.RECEIVED,"validation only"))
            checkCase(CaritdaActions.queryReply("battery",s,obs).data.getJSONObject(0).getString("availability")=="LATEST_RECEIPT","GL28 current app receipt identified")
            obs.append(VehicleObservation("EV_SOC",null,null,null,ObservationSource.PLEOS_EMULATOR_SDK,true,false,"SYNTHETIC_VALIDATION_ONLY",ObservationStatus.TIMEOUT,"validation only"))
            checkCase(CaritdaActions.queryReply("battery",s,obs).data.getJSONObject(0).getString("availability")=="PREVIOUS_OR_STALE","GL29 failed latest query does not make old success current")
            s.selectDriver(reader);val before=s.events(issue.id).size
            val handover=CaritdaActions.queryReply("handover",s,obs)
            checkCase(handover.status=="success"&&handover.text.contains(issue.situation),"GL30 B reads A handover")
            checkCase(s.needsAcknowledgement(issue)&&s.events(issue.id).size==before,"GL31 read action never acknowledges or changes status")
            checkCase(!handover.data.toString().contains(issue.memo)&&!handover.data.toString().contains(issue.reporter.label),"GL32 handover excludes memo and author identity")
            checkCase(CaritdaActions.queryReply("tires",s,obs).text.contains("실제 타이어 압력"),"GL33 tire availability not a fault claim")
        }}
        val a=inst.startActivitySync(Intent(c,CaritdaActionActivity::class.java).setAction(CaritdaActions.QUERY).putExtra("topic","battery").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try{
            val channel=main{GleoReplyChannel(a)}
            checkCase(main{channel.target()==null&&!channel.consented()&&!channel.allowReadOnceConfigured()},"GL34 ADB/unverified caller cannot authorize external data")
            checkCase(main{!channel.send(callback)},"GL35 unverified callback emits no broadcast")
            checkCase(main{GleoReplyChannel(a,android.os.SystemClock.elapsedRealtime()-7001).remainingMs==0L},"GL36 action deadline cannot be extended by a late result")
            checkCase(main{a.window.decorView.findViewWithTag<Button>("action_open")!=null},"GL37 external Intent opens local safe screen")
        }finally{main{a.finish()}}
        return lines
    }
}
