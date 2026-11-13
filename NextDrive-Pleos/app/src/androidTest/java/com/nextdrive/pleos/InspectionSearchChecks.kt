package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.Button
import ai.pleos.playground.navi.constants.NaviMode
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.model.SymptomType
import java.util.UUID

/** Public-SDK seam fixtures and real native Views/SQLite. No live POI/model requests. */
class InspectionSearchChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val h=BLightHarness(inst);private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private class Navi:NavigationBackend{
        lateinit var events:NavigationBackend.Events
        var routing=false;var terms=true;var mode=NaviMode.SAFE_GUIDING
        var holdConnect=false;var holdState=false;var connects=0;var reads=0;var searches=0;var routes=0;var released=0
        var failure:Exception?=null
        override fun initialize(events:NavigationBackend.Events){this.events=events;connects++;if(!holdConnect)events.connected()}
        override fun checkState(){reads++;if(!holdState)events.state(routing,terms,mode)}
        override fun showRepairSearch(){failure?.let{throw it};searches++}
        override fun request(target:NavigationTarget){routes++}
        override fun checkDestination()=Unit
        override fun release(){released++}
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val granted=object:ContextWrapper(c){override fun checkSelfPermission(permission:String)=PackageManager.PERMISSION_GRANTED}
        fun start(n:Navi,timeout:Long=150,allowed:()->Boolean={true})=h.main{InspectionSearch(granted,{n},timeout,allowed).also{it.request{}}}
        val n=Navi().apply{holdConnect=true};val search=start(n,2000)
        h.main{search.request{};n.events.connected();n.events.connected()};h.await{n.searches==1}
        ck(n.connects==1&&n.reads==1,"NS01 duplicate clicks and connection callbacks create one SDK connection and preflight")
        h.main{n.events.state(false,true);n.events.searchStarted();n.events.searchCompleted()}
        ck(n.searches==1&&n.routes==0&&search.dispatched,"NS02 one public search screen request never requests a destination or claims route completion")
        ck(search.events==listOf("NAVI_SEARCH_CONNECTING","NAVI_SEARCH_CONNECTED","NAVI_SEARCH_PREFLIGHT_READY","NAVI_SEARCH_REQUEST_SENT"),"NS03 screen submission is distinct from POI results and guidance")
        h.main{search.stop();n.events.connected();n.events.state(false,true)};Thread.sleep(50)
        ck(n.released==1&&n.searches==1&&!search.busy,"NS04 stop removes provider ownership and ignores late callbacks")
        for((label,configure,code)in listOf(
            Triple("active route",{v:Navi->v.routing=true},"NAVI_SEARCH_EXISTING_ROUTE"),
            Triple("guidance mode",{v:Navi->v.mode=NaviMode.ROUTE_GUIDING},"NAVI_SEARCH_EXISTING_ROUTE"),
            Triple("simulation mode",{v:Navi->v.mode=NaviMode.SIMULATION_GUIDING},"NAVI_SEARCH_EXISTING_ROUTE"),
            Triple("terms",{v:Navi->v.terms=false},"NAVI_SEARCH_TERMS_REQUIRED"),
            Triple("unready app",{v:Navi->v.mode=NaviMode.NOT_WORKING},"NAVI_SEARCH_APP_NOT_READY")
        )){val fake=Navi().also(configure);val guarded=start(fake);h.await{!guarded.busy};ck(fake.searches==0&&fake.routes==0&&fake.released==1&&guarded.events.last()==code,"NS05 $label blocks search without cancelling/replacing a route")}
        var calls=0
        for(permission in listOf("pleos.car.permission.NAVI_ROUTE","pleos.car.permission.NAVI_CUSTOM_MAP")){
            val denied=object:ContextWrapper(granted){override fun checkSelfPermission(p:String)=if(p==permission)PackageManager.PERMISSION_DENIED else PackageManager.PERMISSION_GRANTED}
            val refused=h.main{InspectionSearch(denied,{calls++;Navi()},100){true}.also{it.request{}}}
            ck(calls==0&&refused.events.last()=="NAVI_SEARCH_PERMISSION_DENIED","NS06 denied $permission never opens an SDK or changes OS permissions")
        }
        val silent=Navi().apply{holdState=true};val timeout=start(silent);h.await{!timeout.busy}
        ck(silent.searches==0&&silent.released==1&&timeout.events.last()=="NAVI_SEARCH_TIMEOUT","NS07 no state response times out without searching")
        val sent=Navi();val bounded=start(sent);h.await{!bounded.busy}
        ck(sent.searches==1&&sent.released==1&&bounded.events.last()=="NAVI_SEARCH_MONITOR_ENDED"&&bounded.dispatched,"NS08 bounded post-dispatch monitoring is not a fabricated result acknowledgement")
        for((error,code)in listOf(SecurityException() to "NAVI_SEARCH_PERMISSION_DENIED",UnsupportedOperationException() to "NAVI_SEARCH_UNSUPPORTED",IllegalStateException() to "NAVI_SEARCH_REQUEST_FAILED")){
            val fake=Navi().apply{failure=error};val failed=start(fake);h.await{!failed.busy}
            ck(!failed.dispatched&&fake.released==1&&failed.events.last()==code,"NS09 ${error.javaClass.simpleName} is a distinct failure and releases SDK")
        }
        val error=Navi();val failed=start(error,2000);h.await{error.searches==1};h.main{error.events.failed("NAVI_ERROR_CAUSE_SYSTEM")};h.await{!failed.busy}
        ck(failed.events.last()=="NAVI_ERROR_CAUSE_SYSTEM"&&error.released==1,"NS10 asynchronous public SDK error after dispatch stays a failure")
        var allowed=true;val late=Navi().apply{holdConnect=true};val restricted=start(late,2000){allowed};allowed=false;h.main{late.events.connected()};h.await{!restricted.busy}
        ck(late.searches==0&&late.released==1&&restricted.events.last()=="NAVI_SEARCH_STOPPED","NS11 changed safety/visibility is rechecked before acting on late connection")
        val blocked=Navi();val inactive=start(blocked,100){false}
        ck(!inactive.busy&&blocked.connects==0,"NS12 initially restricted request never opens SDK")
        ui()
        lines.add("BOUNDARIES NATIVE_VIEW_SQL=REAL SDK_SEARCH=FIXTURE LOCATION_AUTH_MODEL_HTTP=0")
        return lines
    }
    private fun ui(){
        val prefix="gleo-ui-"+UUID.randomUUID();val db="$prefix.db"
        val (owner,issue)=SqliteIssueRepository(c,db).use{val s=CaritdaService(it,{true});val d=s.registerDriver("내비 연결");s.selectDriver(d);d to s.record(SymptomType.NOISE,"기존 증상 보존 fixture",System.currentTimeMillis(),"")}
        var a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))as MainActivity
        var current=Navi();val search=InspectionSearch(a,{current},2000){true}
        fun go(){h.click(a,"all_issues");h.click(a,"issue_${issue.id}");h.click(a,"issue_summary")}
        try{
            h.main{h.set(a,"repairSearch\u0024delegate",lazy{search})}
            h.click(a,"driver_${owner.id}");h.click(a,"start_driving");go()
            ck(h.main{h.text(a.window.decorView).contains("자동차 정비소")&&!h.text(a.window.decorView).contains("카카오")},"NS13 primary native UI delegates place selection to installed navigation without external map disclosure")
            h.click(a,"repair_nearby_search");h.await{current.searches==1}
            ck(current.routes==0&&h.main{a.window.decorView.findViewWithTag<Button>("repair_nearby_search")?.isEnabled==false},"NS14 one native button opens search without names/coordinates/login and blocks duplicate clicks")
            ck(h.main{!h.text(a.window.decorView).contains("곳을 찾았")&&a.window.decorView.findViewWithTag<View>("repair_search_status")!=null},"NS15 dispatched status stays visible without invented places")
            h.capture(a,"pleos-repair-search-fixture.png")
            h.click(a,"nav_HOME");h.main{current.events.state(false,true)}
            ck(current.released==1&&!search.busy,"NS16 leaving consultation releases the listener and ignores old callbacks")
            current=Navi().apply{holdConnect=true};go();h.click(a,"repair_nearby_search");val previous=current
            h.main{val s=h.get(a,"service")as CaritdaService;s.selectDriver(s.registerDriver("다음 운전자"));h.draw(a);previous.events.connected()}
            ck(previous.searches==0&&previous.released==1&&!search.busy,"NS17 driver change invalidates pending connection without transferring ownership")
            val monitor=inst.addMonitor(MainActivity::class.java.name,null,false);h.main{a.recreate()};a=(inst.waitForMonitorWithTimeout(monitor,14000)as?MainActivity)?:error("SEARCH_RECREATION_MISSING");inst.removeMonitor(monitor)
            ck(h.main{!(h.get(a,"repairSearch\u0024delegate")as Lazy<*>).let{it.isInitialized()&&(it.value as InspectionSearch).busy}},"NS18 recreation never automatically repeats a search")
            SqliteIssueRepository(c,db).use{ck(it.issue(issue.id)==issue&&it.events(issue.id).size==1,"NS19 search, errors and lifecycle changes preserve symptom, author, state and issue history")}
        }finally{h.main{a.finish();search.stop()}}
    }
}
