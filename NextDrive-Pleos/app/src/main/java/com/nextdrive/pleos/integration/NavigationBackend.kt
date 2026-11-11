package com.nextdrive.pleos.integration

import android.content.Context
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlin.math.abs

data class NavigationTarget(val latitude:Double,val longitude:Double,val name:String){
    fun valid()=latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0&&name.trim().length in 1..80
    fun matches(lat:Double,lon:Double)=lat.isFinite()&&lon.isFinite()&&abs(lat-latitude)<=0.00001&&abs(lon-longitude)<=0.00001
}
interface NavigationBackend {
    interface Events {
        fun connected()
        fun state(routing:Boolean,terms:Boolean,mode:NaviMode=NaviMode.SAFE_GUIDING)
        fun destinationChanged()
        fun destination(latitude:Double,longitude:Double)
        fun searchStarted()
        fun searchCompleted()
        fun navigation(status:NavigationStatus)
        fun cancelled()
        fun failed(code:String)
    }
    fun initialize(events:Events)
    fun checkState()
    fun request(target:NavigationTarget)
    fun checkDestination()
    fun release()
}

/** Public 3.0.5 only. Intentionally exposes no route cancellation or replacement shortcut. */
class PleosNavigationBackend(context:Context):NavigationBackend {
    private val sdk=NaviHelper(context.applicationContext)
    private var listener:NaviHelperEventListener?=null
    private val lock=Any()
    @Volatile private var closed=false
    override fun initialize(events:NavigationBackend.Events){
        val receiver=object:NaviHelperEventListener{
            override fun onRouteStateInfo(routeStateInfo:RouteStateInfo)=events.state(routeStateInfo.isRouting,routeStateInfo.termsAgreement,routeStateInfo.naviMode)
            override fun onDestinationChanged()=events.destinationChanged()
            override fun onDestinationInfo(destinationInfo:DestinationInfo)=events.destination(destinationInfo.latitude,destinationInfo.longitude)
            override fun onRouteStarted(routeStartInfo:RouteStartInfo)=events.searchStarted()
            override fun onRoutingCompleted()=events.searchCompleted()
            override fun onNavigationStatus(status:NavigationStatus)=events.navigation(status)
            override fun onRouteCancelled()=events.cancelled()
            override fun onError(errorCode:NaviErrorCode)=events.failed("NAVI_ERROR_"+errorCode.name)
        }
        listener=receiver
        sdk.initialize({
            val attached=synchronized(lock){if(closed)false else{sdk.addListener(receiver);true}}
            if(attached)events.connected()
        },{if(!closed)events.failed("NAVI_CONNECTION_FAILED")})
    }
    override fun checkState()=sdk.getRouteStateInfo()
    override fun request(target:NavigationTarget)=sdk.requestRoute(RouteInfo(latitude=target.latitude,longitude=target.longitude,poiName=target.name.trim(),poiId="",address="",poiSubId=""))
    override fun checkDestination()=sdk.getDestinationInfo()
    override fun release(){try{synchronized(lock){closed=true;listener?.let{sdk.removeListener(it)};listener=null}}finally{sdk.release()}}
}
