package com.nextdrive.pleos.integration

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

/** Permission and explicit UI consent precede each request. No mock provider or continuous tracking. */
class OneShotLocation(private val context:Context,private val factory:()->LocationBackend,private val allowed:()->Boolean){
    constructor(context:Context,allowed:()->Boolean):this(context,{PleosLocationBackend(context)},allowed)
    private val main=Handler(Looper.getMainLooper());private var sdk:LocationBackend?=null;private var generation=0
    private var report:((IssueLocation?,String)->Unit)?=null
    var busy=false;private set
    var lastCode="LOCATION_IDLE";private set
    fun read(done:(IssueLocation?,String)->Unit){
        if(busy||!allowed())return
        if(context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")!=PackageManager.PERMISSION_GRANTED){done(null,"LOCATION_PERMISSION_DENIED");return}
        stop();busy=true;lastCode="LOCATION_REQUESTING";report=done;val run=generation
        fun finish(value:IssueLocation?,code:String){main.post{if(run==generation&&allowed()){stop();lastCode=code;done(value,code)}}}
        try{val client=factory();sdk=client
            if(!client.supported()){finish(null,"LOCATION_SERVICE_UNAVAILABLE");return}
            client.read{value,code->finish(value?.takeIf{it.valid()},if(value!=null&&!value.valid())"LOCATION_NO_VALID_VALUE"else code)}
        }catch(_:SecurityException){finish(null,"LOCATION_PERMISSION_DENIED")}catch(_:Exception){finish(null,"LOCATION_SERVICE_ERROR")}catch(_:LinkageError){finish(null,"LOCATION_SDK_UNAVAILABLE")}
        main.postDelayed({if(run==generation)finish(null,"LOCATION_TIMEOUT")},12000)
    }
    fun cancel(){val done=report;stop();lastCode="LOCATION_CANCELLED";if(allowed())done?.invoke(null,lastCode)}
    fun stop(){generation++;busy=false;report=null;main.removeCallbacksAndMessages(null);runCatching{sdk?.release()};sdk=null}
}
