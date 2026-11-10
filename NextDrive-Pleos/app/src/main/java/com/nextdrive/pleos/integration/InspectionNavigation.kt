package com.nextdrive.pleos.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.constants.NaviErrorCode

/** Explicit destination request. Never cancels an existing route or claims a service reservation. */
class InspectionNavigation(private val context:Context,private val allowed:()->Boolean){
    private val main=Handler(Looper.getMainLooper());private var sdk:NaviHelper?=null;private var generation=0
    var busy=false;private set
    fun request(latitude:Double,longitude:Double,name:String,report:(String)->Unit){
        require(latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0&&name.isNotBlank()&&name.length<=80)
        if(busy||!allowed())return
        stop();busy=true;val run=generation;var submitted=false
        fun post(action:()->Unit){main.post{if(run==generation&&allowed())action()}}
        fun finish(code:String){stop();report(code)}
        try{val n=NaviHelper(context.applicationContext);sdk=n;n.addListener(object:NaviHelperEventListener{
            override fun onRouteStateInfo(routeStateInfo:RouteStateInfo){post{
                if(submitted)return@post
                if(routeStateInfo.isRouting){finish("NAVI_EXISTING_ROUTE");return@post}
                if(!routeStateInfo.termsAgreement){finish("NAVI_TERMS_REQUIRED");return@post}
                try{submitted=true;n.requestRoute(RouteInfo(latitude,longitude,poiName=name,poiId="",address="",poiSubId=""));report("NAVI_REQUEST_SENT")}catch(_:SecurityException){finish("NAVI_PERMISSION_DENIED")}catch(_:Exception){finish("NAVI_REQUEST_FAILED")}
            }}
            override fun onRoutingCompleted(){post{if(submitted)finish("NAVI_ROUTING_COMPLETED")}}
            override fun onError(errorCode:NaviErrorCode){post{finish("NAVI_ERROR_"+errorCode.name)}}
        });n.initialize({post{try{n.getRouteStateInfo()}catch(_:SecurityException){finish("NAVI_PERMISSION_DENIED")}catch(_:Exception){finish("NAVI_REQUEST_FAILED")}}},{post{finish("NAVI_CONNECTION_FAILED")}})
        }catch(_:SecurityException){finish("NAVI_PERMISSION_DENIED")}catch(_:Exception){finish("NAVI_SERVICE_ERROR")}catch(_:LinkageError){finish("NAVI_SDK_UNAVAILABLE")}
        main.postDelayed({if(run==generation)finish("NAVI_TIMEOUT")},20000)
    }
    fun stop(){generation++;busy=false;main.removeCallbacksAndMessages(null);runCatching{sdk?.release()};sdk=null}
}
