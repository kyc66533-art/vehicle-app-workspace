package com.nextdrive.pleos

import android.app.Instrumentation
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.safety.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit emulator route experiment using the public documentation's destination.
 * Existing active routes are refused. Only a newly requested matching fixture route may be
 * cancelled during test cleanup. No user location or route coordinates are logged. */
class DriveCheckNavigationLiveChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun main(block:()->Unit)=inst.runOnMainSync(block)
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    /** Recovery is allowed only with a private receipt of THIS validation experiment's dispatch.
     * It still queries the destination and never cancels a different current route. */
    fun cleanupOwned():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val proof=java.io.File(c.filesDir,"drivecheck-owned-navigation-proof.txt")
        val receipt=proof.takeIf{it.isFile}?.readText()?:error("OWNED_EXPERIMENT_RECEIPT_REQUIRED")
        check(receipt.contains("NAVI_PREFLIGHT_READY,NAVI_REQUEST_SENT,NAVI_ROUTE_SEARCH_STARTED"))
        val safe=CountDownLatch(1);var gate:CarSafetyGate?=null
        try{
            main{gate=CarSafetyGate(c){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()}
            check(safe.await(12,TimeUnit.SECONDS)){"PARKED_GATE_REQUIRED"}
            restoreOwnedFixture(NavigationTarget(37.48544722,127.03636666,"공식 문서 목적지 시험"))
            check(proof.delete())
        }finally{main{gate?.stop()}}
        return lines
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val safe=CountDownLatch(1);var gate:CarSafetyGate?=null;var nav:InspectionNavigation?=null
        main{gate=CarSafetyGate(c){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()}
        val target=NavigationTarget(37.48544722,127.03636666,"공식 문서 목적지 시험")
        try{
            verify(safe.await(12,TimeUnit.SECONDS),"NL01 actual parked safety gate allows the bounded validation request")
            val done=CountDownLatch(1)
            main{nav=InspectionNavigation(c){gate?.state==EditingState.ALLOWED};nav!!.request(target.latitude,target.longitude,target.name){
                val codes=nav!!.events.joinToString(","){it.code}
                if(codes.contains("NAVI_REQUEST_SENT"))java.io.File(c.filesDir,"drivecheck-owned-navigation-proof.txt").writeText(codes)
                if(nav?.busy==false)done.countDown()
            }}
            verify(done.await(35,TimeUnit.SECONDS),"NL02 actual SDK session reaches a bounded terminal result")
            val codes=nav!!.events.map{it.code};lines.add("RUNTIME NAVI_TRACE "+codes.joinToString(","))
            java.io.File(c.filesDir,"drivecheck-navigation-trace.txt").writeText(codes.joinToString(","))
            verify(codes.contains("NAVI_CONNECTED")||codes.any{it in setOf("NAVI_CONNECTION_FAILED","NAVI_PERMISSION_DENIED","NAVI_SDK_UNAVAILABLE","NAVI_TIMEOUT")},"NL03 connection outcome is distinguished from destination acceptance")
            if(codes.contains("NAVI_EXISTING_ROUTE")){
                verify(!codes.contains("NAVI_REQUEST_SENT"),"NL04 existing actual route remains untouched; destination experiment skipped")
                lines.add("RUNTIME NAVI_EXISTING_ROUTE_PRESERVED; no new-route success claimed")
                return lines
            }
            if(codes.contains("NAVI_REQUEST_SENT")){
                verify(codes.contains("NAVI_PREFLIGHT_READY"),"NL05 actual request follows confirmed no-route and accepted-terms state")
                verify(!nav!!.guidanceConfirmed||codes.containsAll(listOf("NAVI_DESTINATION_CONFIRMED","NAVI_STATUS_ROUTE_DRIVE","NAVI_ROUTING_COMPLETED","NAVI_MODE_ROUTE_GUIDING")),"NL06 actual guidance claim requires matching destination, completed search, ROUTE_DRIVE and consistent guidance mode")
                restoreOwnedFixture(target)
                java.io.File(c.filesDir,"drivecheck-owned-navigation-proof.txt").delete()
            }else lines.add("RUNTIME NAVI_NO_DESTINATION_DISPATCH; environment/terms result is not guidance success")
            return lines
        }finally{main{nav?.stop();gate?.stop()}}
    }
    private fun restoreOwnedFixture(target:NavigationTarget){
        val checked=CountDownLatch(1);var helper:NaviHelper?=null;var listener:NaviHelperEventListener?=null
        var routing:Boolean?=null;var mode:NaviMode?=null;var destinationMatches=false;var cancelled=false;var error=false;var errorName="NONE"
        val handler=android.os.Handler(android.os.Looper.getMainLooper());var retries=0
        main{
            val sdk=NaviHelper(c);helper=sdk
            val events=object:NaviHelperEventListener{
                override fun onRouteStateInfo(routeStateInfo:RouteStateInfo){
                    mode=routeStateInfo.naviMode;routing=routeStateInfo.isRouting||mode in setOf(NaviMode.ROUTE_GUIDING,NaviMode.SIMULATION_GUIDING)
                    if(routing==false)checked.countDown()else if(!cancelled)sdk.getDestinationInfo()else if(retries++<3)handler.postDelayed({sdk.getRouteStateInfo()},700)
                }
                override fun onDestinationInfo(destinationInfo:DestinationInfo){
                    if(cancelled)return
                    destinationMatches=target.matches(destinationInfo.latitude,destinationInfo.longitude)
                    if(destinationMatches&&!cancelled){cancelled=true;sdk.cancelRoute()}else if(!destinationMatches){error=true;checked.countDown()}
                }
                override fun onRouteCancelled(){handler.postDelayed({sdk.getRouteStateInfo()},700)}
                override fun onError(errorCode:NaviErrorCode){errorName=errorCode.name;error=true;checked.countDown()}
            };listener=events
            sdk.initialize({sdk.addListener(events);sdk.getRouteStateInfo()},{error=true;checked.countDown()})
        }
        try{
            val received=checked.await(12,TimeUnit.SECONDS)
            verify(received&&!error&&routing==false,"NL07 owned-fixture cleanup routing=$routing mode=$mode matched=$destinationMatches cancelled=$cancelled error=$errorName received=$received")
            lines.add("RUNTIME NAVI_CLEANUP active=false mode=$mode cancelled_owned_fixture=$cancelled destination_match=$destinationMatches")
        }finally{main{handler.removeCallbacksAndMessages(null);listener?.let{helper?.removeListener(it)};helper?.release()}}
    }
}
