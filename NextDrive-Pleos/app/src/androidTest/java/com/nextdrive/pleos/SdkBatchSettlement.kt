package com.nextdrive.pleos

import com.nextdrive.pleos.vehicle.ObservationStore
import com.nextdrive.pleos.vehicle.PleosReadClient

/** The one-shot batch and independent Safety subscriptions both have initial async results. */
object SdkBatchSettlement {
    fun complete(h:BLightHarness,a:MainActivity,raw:ObservationStore):Boolean {
        if((h.get(a,"pleos") as PleosReadClient).busy)return false
        val initial=raw.refs().filter{it.value.detail.startsWith("PLEOS_PUBLIC_SAFETY_2_3_2")}.map{it.value.kind}.toSet()
        return initial.containsAll(listOf("WASHER_LOW","POWER_DOWN","BRAKE_FLUID","ENGINE_OIL"))
    }
}
