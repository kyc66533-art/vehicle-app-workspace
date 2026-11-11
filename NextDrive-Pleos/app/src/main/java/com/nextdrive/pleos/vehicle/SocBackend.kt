package com.nextdrive.pleos.vehicle

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener

interface SocBackend {
    val provenance:String
    fun initialize()
    fun capability(done:(Boolean)->Unit,failed:(Exception)->Unit)
    fun subscribe(value:(Int?)->Unit,failed:(Exception)->Unit)
    fun initial(value:(Int?)->Unit,failed:(Exception)->Unit)
    fun release()
}
/** Exactly the existing public 2.3.2 SOC APIs and CAR_ENERGY permission. */
class PleosSocBackend(context:Context):SocBackend {
    override val provenance="PLEOS_PUBLIC_SOC_2_3_2"
    private val sdk=Vehicle(context.applicationContext)
    private var listener:EvBatteryStateOfChargeListener?=null
    override fun initialize()=sdk.initialize()
    override fun capability(done:(Boolean)->Unit,failed:(Exception)->Unit)=sdk.getEvBattery().checkEvBatteryStateOfChargeCapabilities({done(it.accessType in setOf(AccessType.READ,AccessType.READ_WRITE))},failed)
    override fun subscribe(value:(Int?)->Unit,failed:(Exception)->Unit){
        val emit=value
        val receiver=object:EvBatteryStateOfChargeListener{
            override fun onEvBatteryStateOfChargeUpdated(value:Int?)=emit(value)
            override fun onFailed(e:Exception)=failed(e)
        };listener=receiver;sdk.getEvBattery().registerEvBatteryStateOfCharge(receiver)
    }
    override fun initial(value:(Int?)->Unit,failed:(Exception)->Unit)=sdk.getEvBattery().getEvBatteryStateOfCharge(value,failed)
    override fun release(){try{listener?.let{sdk.getEvBattery().unregisterEvBatteryStateOfCharge(it)};listener=null}finally{sdk.release()}}
}
