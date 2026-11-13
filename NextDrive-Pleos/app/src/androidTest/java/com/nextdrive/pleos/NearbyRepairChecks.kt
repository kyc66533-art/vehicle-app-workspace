package com.nextdrive.pleos

import android.app.*
import android.content.Intent
import android.widget.Button
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.model.SymptomType
import ai.pleos.playground.navi.constants.NaviMode
import org.json.*
import java.util.UUID
import java.util.concurrent.*

/** Real native View/SQLite, isolated location/auth/place/navigation fixtures. No external lookup. */
class NearbyRepairChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val h=BLightHarness(inst);private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun item()=JSONObject().put("id","101").put("name","격리 정비소").put("address","검증 주소")
        .put("phone","").put("latitude",37.48544722).put("longitude",127.03636666).put("distance_m",210).put("category","GENERAL")
    private fun payload(vararg p:JSONObject)=JSONObject().put("source","KAKAO_LOCAL").put("distance","straight_line_m")
        .put("verified_service_network",false).put("places",JSONArray(p.toList())).toString()
    private fun rejected(raw:String)=runCatching{NearbyRepairClient.parseResponse(raw)}.exceptionOrNull().let{it is NearbyRepairError&&it.code=="PLACES_RESPONSE_INVALID"}
    private class Location:LocationBackend{
        var reads=0;var released=0;var available=true;lateinit var done:(IssueLocation?,String)->Unit
        override fun supported()=available
        override fun read(done:(IssueLocation?,String)->Unit){reads++;this.done=done}
        override fun release(){released++}
    }
    private class Session:PortableSession{
        var account="nearby-fixture-account";var login=true;var hold=false;var acquires=0
        var pending:((String?,String?,String?)->Unit)?=null
        override val signedIn get()=login
        override val subject get()=account.takeIf{login}
        override fun matches(uid:String)=login&&uid==account
        override fun acquire(done:(String?,String?,String?)->Unit){acquires++;if(hold)pending=done else done(account,"fixture-bearer",null)}
        override fun client(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?):PortableAiClient=error("GEMINI_MUST_NOT_RUN")
    }
    private class Lookup:NearbyRepairLookup{
        @Volatile var calls=0;var values=emptyList<NearbyRepairPlace>();var failure:String?=null;var gate:CountDownLatch?=null
        override fun search(latitude:Double,longitude:Double,bearer:String):List<NearbyRepairPlace>{calls++;gate?.let{check(it.await(12,TimeUnit.SECONDS))};failure?.let{throw NearbyRepairError(it)};return values}
    }
    private class Navi:NavigationBackend{
        lateinit var events:NavigationBackend.Events;var routing=false;var requests=0;var released=0;var target:NavigationTarget?=null
        override fun initialize(events:NavigationBackend.Events){this.events=events;events.connected()}
        override fun checkState()=events.state(routing,true,if(routing)NaviMode.ROUTE_GUIDING else NaviMode.SAFE_GUIDING)
        override fun request(target:NavigationTarget){this.target=target;requests++}
        override fun checkDestination(){}
        override fun release(){released++}
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        ck(NearbyRepairClient.parseResponse(payload(item())).single().navigationTarget().valid(),"NR01 verified place coordinates form the existing NavigationTarget")
        val long=NearbyRepairClient.parseResponse(payload(item().put("name","가".repeat(100)))).single()
        ck(long.name.length==100&&long.navigationTarget().name.length==80&&long.navigationTarget().valid(),"NR02 full display name is preserved while SDK label respects the existing 80-character limit")
        ck(NearbyRepairClient.parseResponse(payload()).isEmpty(),"NR03 real empty list has no invented fallback place")
        for((name,p)in listOf("identifier" to item().put("id","invalid"),"category" to item().put("category","INVENTED"),"fractional distance" to item().put("distance_m",0.5),"distance range" to item().put("distance_m",20001),"latitude" to item().put("latitude",0),"longitude" to item().put("longitude",0),"blank name" to item().put("name"," "),"control name" to item().put("name","가\n나"),"phone bound" to item().put("phone","1".repeat(36))))ck(rejected(payload(p)),"NR04 invalid $name is rejected before route UI")
        ck(rejected(payload(item(),item())),"NR05 duplicate place identifiers cannot create duplicate route cards")
        ck(rejected(payload(*Array(4){item().put("id",(101+it).toString())})),"NR06 more than three results are rejected")
        ck(rejected("invalid json")&&rejected(JSONObject(payload(item())).put("source","INVENTED").toString()),"NR07 malformed JSON and invented origin are response errors")
        ck(rejected(JSONObject(payload(item())).put("distance","driving_m").toString())&&rejected(JSONObject(payload(item())).put("verified_service_network",true).toString()),"NR08 neither road distance nor service-network certification is invented")
        ck((runCatching{NearbyRepairClient.search(Double.NaN,127.0,"fixture")}.exceptionOrNull()as?NearbyRepairError)?.code=="LOCATION_OUT_OF_RANGE"&&
            (runCatching{NearbyRepairClient.search(37.0,127.0,"")}.exceptionOrNull()as?NearbyRepairError)?.code=="AUTH_SIGN_IN_REQUIRED","NR09 invalid coordinates and absent token fail before opening HTTP")
        ck(NearbyRepairClient.message("PLACES_NOT_CONFIGURED").contains("아직 연결")&&NearbyRepairClient.message("PLACES_TIMEOUT").contains("늦어"),"NR10 missing deployment/configuration and timeout have actionable separate messages")
        ui()
        lines.add("BOUNDARIES VIEW_SQL=REAL LOCATION_AUTH_PLACES_NAV=FIXTURE EXTERNAL_LOOKUPS=0 GEMINI_CALLS=0")
        return lines
    }
    private fun ui(){
        val prefix="gleo-ui-"+UUID.randomUUID();val db="$prefix.db"
        val (owner,issue)=SqliteIssueRepository(c,db).use{val s=CaritdaService(it,{true});val d=s.registerDriver("정비 조회");s.selectDriver(d);d to s.record(SymptomType.NOISE,"원본 증상 보존 fixture",System.currentTimeMillis(),"")}
        var a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))as MainActivity
        val auth=Session();val lookup=Lookup();val location=Location();val nav=Navi();val monitor=InspectionNavigation(a,{nav},30000){true}
        val sample=NearbyRepairClient.parseResponse(payload(item().put("name","가".repeat(100)))).single();lookup.values=listOf(sample)
        fun go(){h.click(a,"all_issues");h.click(a,"issue_${issue.id}");h.click(a,"issue_summary")}
        fun start(){h.click(a,"repair_nearby_search");h.confirm(a)}
        fun point(age:Long=0){val now=System.currentTimeMillis();h.main{location.done(IssueLocation(37.48544722,127.03636666,now-age,now,"PLEOS_FUSED_LAST_KNOWN"),"LOCATION_SUCCEEDED")}}
        fun busy()=h.main{h.get(a,"nearbyBusy")as Boolean}
        try{
            h.main{h.set(a,"nearbySession\$delegate",lazy{auth});h.set(a,"nearbyLookup",lookup);h.set(a,"locationReader\$delegate",lazy{OneShotLocation(a,{location}){true}});h.set(a,"navigation\$delegate",lazy{monitor})}
            h.click(a,"driver_${owner.id}");h.click(a,"start_driving");go()
            h.click(a,"repair_nearby_search");val consent=h.main{(h.get(a,"activeDialog")as AlertDialog).window!!.decorView.let{h.text(it)}}
            ck(consent.contains("카카오")&&consent.contains("전송")&&consent.contains("문제 내용"),"NR11 actual consent identifies external location transfer and excludes issue contents")
            h.confirm(a,false);ck(location.reads==0&&lookup.calls==0,"NR12 cancelling consent never starts location or provider lookup")
            auth.login=false;h.click(a,"repair_nearby_search");ck(location.reads==0&&h.main{h.text(a.window.decorView).contains("로그인")},"NR13 signed-out account stops before location collection")
            auth.login=true;start();h.await{location.reads==1};point(600000);h.await{!busy()}
            ck(lookup.calls==0&&h.main{h.text(a.window.decorView).contains("오래")},"NR14 stale last-known location never reaches the relay")
            start();h.await{location.reads==2};point();h.await{lookup.calls==1&&!busy()}
            ck(h.main{a.window.decorView.findViewWithTag<Button>("repair_nearby_route_101")!=null&&h.text(a.window.decorView).contains("직선거리")},"NR15 native cards show only returned places with straight-line distance")
            h.capture(a,"nearby-repair-fixture.png")
            h.click(a,"repair_nearby_route_101");h.confirm(a);h.await{nav.requests==1}
            ck(nav.target?.latitude==sample.latitude&&nav.target?.longitude==sample.longitude&&nav.target?.name?.length==80,"NR16 selected coordinates enter the unchanged InspectionNavigation backend with a bounded SDK label")
            ck(h.main{a.window.decorView.findViewWithTag<Button>("repair_nearby_search")?.isEnabled==false},"NR17 pending route blocks a second search/request")
            h.main{nav.events.searchStarted()};h.click(a,"cancel_navigation_monitor")
            ck(!monitor.busy&&nav.released==1&&!monitor.guidanceConfirmed,"NR18 monitoring cancellation releases listener and never claims guidance completion")
            val occupied=Navi().apply{routing=true};val guarded=InspectionNavigation(a,{occupied},30000){true}
            h.main{h.set(a,"navigation\$delegate",lazy{guarded})};h.click(a,"repair_nearby_route_101");h.confirm(a);h.await{occupied.released==1}
            ck(occupied.requests==0&&guarded.events.any{it.code=="NAVI_EXISTING_ROUTE"},"NR19 selecting a place never replaces an existing route")
            lookup.failure="PLACES_NOT_CONFIGURED";start();h.await{location.reads==3};point();h.await{lookup.calls==2&&!busy()}
            ck(h.main{h.text(a.window.decorView).contains("아직 연결")&&a.window.decorView.findViewWithTag<Button>("repair_nearby_search")?.isEnabled==true},"NR20 unavailable server preserves the consultation and exposes a retry")
            lookup.failure=null;auth.hold=true;start();h.await{location.reads==4};point();h.await{auth.pending!=null}
            val delayed=auth.pending!!;auth.pending=null;val calls=lookup.calls;h.click(a,"nav_HOME");h.main{delayed(auth.account,"fixture-bearer",null)}
            ck(lookup.calls==calls&&!busy(),"NR21 late authentication after leaving the screen cannot start a place request")
            go();start();h.await{location.reads==5};point();h.await{auth.pending!=null};val previous=auth.account;val oldAccount=auth.pending!!;auth.pending=null
            h.main{auth.account="changed-fixture-account";h.draw(a);oldAccount(previous,"fixture-bearer",null)}
            ck(lookup.calls==calls&&!busy(),"NR22 account switch invalidates a pending request and result scope")
            start();h.await{location.reads==6};point();h.await{auth.pending!=null};val oldDriver=auth.pending!!;auth.pending=null
            h.main{val s=h.get(a,"service")as CaritdaService;s.selectDriver(s.registerDriver("다음 조회"));h.draw(a);oldDriver(auth.account,"fixture-bearer",null)}
            ck(lookup.calls==calls&&!busy(),"NR23 driver switch cannot inherit a previous driver's place request")
            auth.hold=false;lookup.gate=CountDownLatch(1);start();h.await{location.reads==7};point();h.await{lookup.calls==calls+1};h.click(a,"nav_HOME");lookup.gate!!.countDown()
            (h.get(a,"nearbyWorker")as ExecutorService).submit{}.get(5,TimeUnit.SECONDS);inst.waitForIdleSync()
            ck(!busy()&&h.main{(h.get(a,"nearbyPlaces")as List<*>).isEmpty()},"NR24 in-flight lookup completion after navigation is discarded")
            val activityMonitor=inst.addMonitor(MainActivity::class.java.name,null,false);h.main{a.recreate()};a=(inst.waitForMonitorWithTimeout(activityMonitor,14000)as?MainActivity)?:error("NEARBY_RECREATION_MISSING");inst.removeMonitor(activityMonitor)
            ck(h.main{(h.get(a,"nearbyPlaces")as List<*>).isEmpty()}&&lookup.calls==calls+1,"NR25 Activity recreation restores no location results and never automatically searches")
            SqliteIssueRepository(c,db).use{ck(it.issue(issue.id)==issue&&it.events(issue.id).size==1,"NR26 search, selection, errors and lifecycle changes preserve symptom, author, status and history")}
        }finally{lookup.gate?.countDown();h.main{a.finish();monitor.stop()}}
    }
    fun deployment():List<String>{
        ValidationEnvironment.requireIsolated(c)
        fun request(path:String,post:Boolean):Pair<Int,String>{
            val conn=java.net.URL("https://caritda-ai-relay-dev.kyc66533.workers.dev$path").openConnection()as java.net.HttpURLConnection
            try{conn.instanceFollowRedirects=false;conn.connectTimeout=7000;conn.readTimeout=7000
                if(post){conn.requestMethod="POST";conn.setRequestProperty("Content-Type","application/json");conn.doOutput=true;conn.outputStream.use{it.write("{}".toByteArray())}}
                val status=conn.responseCode;val body=(if(status<400)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText().take(4096)}.orEmpty();return status to body
            }finally{conn.disconnect()}
        }
        try{
            val health=request("/health",false);val route=request("/v2/repair/nearby",true)
            ck(health.first==200,"NRD01 existing relay health is reachable without account or model calls")
            ck(route.first in setOf(401,404,503),"NRD02 anonymous empty-body repair probe never returns place data")
            lines.add("RUNTIME REPAIR_ENDPOINT_HTTP=${route.first} STATUS=${if(route.first==404)"NOT_DEPLOYED"else"AUTH_OR_CONFIG_GATED"} LOCATION_SENT=false GEMINI_CALLS=0")
        }catch(_:java.io.IOException){lines.add("RUNTIME REPAIR_ENDPOINT_UNREACHABLE; no provider/search success claimed")}
        return lines
    }
}
