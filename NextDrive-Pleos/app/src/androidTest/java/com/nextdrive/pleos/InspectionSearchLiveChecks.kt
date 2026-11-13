package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.InspectionSearch
import com.nextdrive.pleos.model.SymptomType
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import ai.pleos.playground.navi.constants.*
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.nextdrive.pleos.integration.NavigationTarget
import com.nextdrive.pleos.safety.*

/** Explicit validation-only native button -> actual NaviHelper SearchPlace screen request.
 * Does not choose a POI, change/cancel a route or call a remote CARITDA service. */
class InspectionSearchLiveChecks(private val inst:Instrumentation){
    /** Watches a destination chosen explicitly in the native navigation UI during this
     * bounded experiment. Never requests a route. Cleanup may cancel only the matching
     * new route, with prior no-route evidence, expected POI name and rechecked coordinates. */
    fun watchSelectedRoute(expected:String):List<String>{
        val c=inst.targetContext;ValidationEnvironment.requireIsolated(c)
        require(expected.trim()==expected&&expected.length in 1..80&&expected.none{it.isISOControl()})
        val h=BLightHarness(inst);val lines=mutableListOf<String>();val trace=mutableListOf<String>()
        fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
        val ready=CountDownLatch(1);val safe=CountDownLatch(1);val routed=CountDownLatch(1);val cleaned=CountDownLatch(1)
        var gate:CarSafetyGate?=null;var helper:NaviHelper?=null;var listener:NaviHelperEventListener?=null
        var initial=false;var baselineKnown=false;var routeStarted=false;var completed=false;var routeDrive=false
        var mode:NaviMode?=null;var target:NavigationTarget?=null;var cleanup=false;var cancelled=false;var clean=false
        val marker=java.io.File(c.filesDir,"inspection-search-route-ready.txt")
        val proof=java.io.File(c.filesDir,"inspection-search-owned-route.json")
        val handler=android.os.Handler(android.os.Looper.getMainLooper());var cleanupReads=0
        fun event(code:String){if(code !in trace)trace.add(code)}
        try{
            h.main{gate=CarSafetyGate(c){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()}
            ck(safe.await(12,TimeUnit.SECONDS),"NSR01 real parked safety gate allows native navigation experiment")
            h.main{
                val sdk=NaviHelper(c);helper=sdk
                val events=object:NaviHelperEventListener{
                    override fun onRouteStateInfo(info:RouteStateInfo){
                        mode=info.naviMode
                        val active=info.isRouting||mode in setOf(NaviMode.ROUTE_GUIDING,NaviMode.SIMULATION_GUIDING)
                        if(!baselineKnown){
                            baselineKnown=true;initial=!active&&info.termsAgreement&&mode!=NaviMode.NOT_WORKING
                            if(initial){event("NO_EXISTING_ROUTE");marker.writeText(System.currentTimeMillis().toString()+"\n"+expected)}
                            ready.countDown();return
                        }
                        event("MODE_"+mode?.name)
                        if(cleanup){if(!active){clean=true;cleaned.countDown()}else if(cancelled&&cleanupReads++<3)handler.postDelayed({sdk.getRouteStateInfo()},700)}
                    }
                    override fun onRouteStarted(info:RouteStartInfo){
                        event("ROUTE_SEARCH_STARTED");routeStarted=initial&&info.name.trim()==expected;sdk.getDestinationInfo()
                    }
                    override fun onRoutingCompleted(){completed=true;event("ROUTING_COMPLETED");sdk.getDestinationInfo();sdk.getRouteStateInfo();routed.countDown()}
                    override fun onNavigationStatus(status:NavigationStatus){
                        event("STATUS_"+status.name);routeDrive=status==NavigationStatus.ROUTE_DRIVE
                        if(routeDrive){sdk.getDestinationInfo();sdk.getRouteStateInfo()}
                    }
                    override fun onDestinationChanged(){event("DESTINATION_CHANGED");sdk.getDestinationInfo()}
                    override fun onDestinationInfo(info:DestinationInfo){
                        val received=NavigationTarget(info.latitude,info.longitude,info.name.trim())
                        if(!cleanup){
                            if(initial&&routeStarted&&received.valid()&&received.name==expected){
                                target=received;event("SELECTED_POI_CONFIRMED")
                                proof.writeText(org.json.JSONObject().put("expected",expected).put("latitude",received.latitude).put("longitude",received.longitude).put("initial_no_route",true).toString())
                            }
                        }else{
                            val owned=target
                            if(!cancelled&&owned!=null&&received.name==owned.name&&owned.matches(received.latitude,received.longitude)&&gate?.state==EditingState.ALLOWED){cancelled=true;sdk.cancelRoute()}
                            else if(!cancelled)cleaned.countDown()
                        }
                    }
                    override fun onRouteCancelled(){event("ROUTE_CANCELLED");if(cleanup)handler.postDelayed({sdk.getRouteStateInfo()},700)}
                    override fun onError(code:NaviErrorCode){event("ERROR_"+code.name);ready.countDown();routed.countDown();if(cleanup)cleaned.countDown()}
                };listener=events
                sdk.initialize({sdk.addListener(events);sdk.getRouteStateInfo()},{event("CONNECTION_FAILED");ready.countDown()})
            }
            ck(ready.await(12,TimeUnit.SECONDS)&&initial,"NSR02 actual initial route is empty and terms are accepted; otherwise no route experiment")
            // External test operator now chooses the already displayed POI and guidance
            // button in the native navigation UI. No typing or private Intent is involved.
            routed.await(45,TimeUnit.SECONDS)
            val before=h.main{trace.toList()}
            lines.add("RUNTIME SELECTED_ROUTE_TRACE="+before.joinToString(","))
            lines.add("RUNTIME ROUTE_SEARCH_STARTED=$routeStarted SELECTED_POI_MATCH=${target!=null} ROUTING_COMPLETED=$completed ROUTE_DRIVE=$routeDrive MODE=$mode")
            ck(h.main{routeStarted&&target!=null&&routeDrive&&mode==NaviMode.ROUTE_GUIDING},"NSR03 chosen native POI produces real search-start, matching destination and guidance mode")
            h.main{cleanup=true;helper!!.getDestinationInfo()}
            ck(cleaned.await(12,TimeUnit.SECONDS)&&clean,"NSR04 only the newly selected matching test route is cancelled and no-route state is rechecked")
            if(clean)proof.delete()
            java.io.File(c.filesDir,"inspection-search-route-trace.txt").writeText(before.joinToString(",")+"\nCOMPLETED="+completed+"\nCLEANUP_NO_ROUTE="+clean)
            lines.add("BOUNDARIES NATIVE_POI_ROUTE=REAL ARRIVAL=NOT_TESTED ROUTING_COMPLETION="+if(completed)"RECEIVED"else"NOT_RECEIVED")
        }finally{
            h.main{handler.removeCallbacksAndMessages(null);listener?.let{helper?.removeListener(it)};helper?.release();gate?.stop()}
            marker.delete()
        }
        return lines
    }
    fun run():List<String>{
        val c=inst.targetContext;ValidationEnvironment.requireIsolated(c)
        val h=BLightHarness(inst);val lines=mutableListOf<String>()
        fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
        ck(c.checkSelfPermission("pleos.car.permission.NAVI_CUSTOM_MAP")==PackageManager.PERMISSION_GRANTED,"NSL01 actual public navigation-screen permission is granted")
        val prefix="gleo-ui-"+UUID.randomUUID();val db="$prefix.db"
        val (owner,issue)=SqliteIssueRepository(c,db).use{val s=CaritdaService(it,{true});val d=s.registerDriver("내비 검색 시험");s.selectDriver(d);d to s.record(SymptomType.OTHER,"실제 내비 검색 연결 시험 TEST",System.currentTimeMillis(),"")}
        val a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))as MainActivity
        try{
            h.click(a,"driver_${owner.id}");h.click(a,"start_driving");h.click(a,"all_issues");h.click(a,"issue_${issue.id}");h.click(a,"issue_summary")
            h.click(a,"repair_nearby_search")
            val search=h.get(a,"repairSearch\u0024delegate").let{(it as Lazy<*>).value as InspectionSearch}
            h.await{!search.busy||search.dispatched}
            val events=h.main{search.events};ck(events.contains("NAVI_SEARCH_CONNECTED"),"NSL02 native button connects to actual installed Navi service")
            if(search.dispatched)ck(events.contains("NAVI_SEARCH_PREFLIGHT_READY"),"NSL03 actual no-route/terms state precedes screen dispatch")
            lines.add("RUNTIME SEARCH_TRACE="+events.joinToString(","))
            java.io.File(c.filesDir,"inspection-search-live-trace.txt").writeText(events.joinToString(","))
            // Allow the external navigation UI to render; UI capture is evaluated separately.
            Thread.sleep(3000)
            SqliteIssueRepository(c,db).use{ck(it.issue(issue.id)==issue&&it.events(issue.id).size==1,"NSL04 real screen request preserves isolated issue and history")}
            lines.add("BOUNDARIES SDK_CONNECTION=REAL SEARCH_RESULTS=VERIFY_NAV_UI DESTINATION_REQUESTS=0 GEMINI_CALLS=0")
        }finally{h.main{a.finish()}}
        return lines
    }
}
