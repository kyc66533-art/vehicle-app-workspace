package com.nextdrive.pleos.integration

import android.content.Context
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData

/** The existing public last-known API only. No mock provider or subscription. */
interface LocationBackend {
    fun supported():Boolean
    fun read(done:(IssueLocation?,String)->Unit)
    fun release()
}
class PleosLocationBackend(context:Context):LocationBackend {
    private val sdk=FusedLocation(context.applicationContext)
    override fun supported()=sdk.isSupported()
    override fun read(done:(IssueLocation?,String)->Unit){
        sdk.initialize()
        sdk.getLastKnownLocation(object:FusedLocationListener{
            override fun onChanged(location:LocationData?){
                val value=location?.let{IssueLocation(it.latitude,it.longitude,it.timestampMillis,System.currentTimeMillis(),"PLEOS_FUSED_LAST_KNOWN")}
                done(value?.takeIf{it.valid()},if(value?.valid()==true)"LOCATION_RECEIVED"else"LOCATION_NO_VALID_VALUE")
            }
            override fun onError(exception:Exception){done(null,if(exception is SecurityException)"LOCATION_PERMISSION_DENIED"else"LOCATION_SERVICE_ERROR")}
        })
    }
    override fun release()=sdk.release()
}
