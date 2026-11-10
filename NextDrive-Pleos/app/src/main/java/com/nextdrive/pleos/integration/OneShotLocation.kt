package com.nextdrive.pleos.integration

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData

/** Permission and explicit UI consent precede each request. No mock provider or continuous tracking. */
class OneShotLocation(private val context:Context,private val allowed:()->Boolean){
    private val main=Handler(Looper.getMainLooper());private var sdk:FusedLocation?=null;private var generation=0
    var busy=false;private set
    fun read(done:(IssueLocation?,String)->Unit){
        if(busy||!allowed())return
        if(context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")!=PackageManager.PERMISSION_GRANTED){done(null,"LOCATION_PERMISSION_DENIED");return}
        stop();busy=true;val run=generation
        fun finish(value:IssueLocation?,code:String){main.post{if(run==generation&&allowed()){stop();done(value,code)}}}
        try{val client=FusedLocation(context.applicationContext);sdk=client
            if(!client.isSupported()){stop();done(null,"LOCATION_SERVICE_UNAVAILABLE");return}
            client.initialize();client.getLastKnownLocation(object:FusedLocationListener{
                override fun onChanged(location:LocationData?){val v=location?.let{IssueLocation(it.latitude,it.longitude,it.timestampMillis,System.currentTimeMillis(),"PLEOS_FUSED_LAST_KNOWN")};finish(v?.takeIf{it.valid()},if(v?.valid()==true)"LOCATION_RECEIVED"else"LOCATION_NO_VALID_VALUE")}
                override fun onError(exception:Exception){finish(null,if(exception is SecurityException)"LOCATION_PERMISSION_DENIED"else"LOCATION_SERVICE_ERROR")}
            })
        }catch(_:SecurityException){finish(null,"LOCATION_PERMISSION_DENIED")}catch(_:Exception){finish(null,"LOCATION_SERVICE_ERROR")}catch(_:LinkageError){finish(null,"LOCATION_SDK_UNAVAILABLE")}
        main.postDelayed({if(run==generation)finish(null,"LOCATION_TIMEOUT")},12000)
    }
    fun stop(){generation++;busy=false;main.removeCallbacksAndMessages(null);runCatching{sdk?.release()};sdk=null}
}
