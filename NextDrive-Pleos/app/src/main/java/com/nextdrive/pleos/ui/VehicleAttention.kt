package com.nextdrive.pleos.ui

import com.nextdrive.pleos.vehicle.*

data class VehicleCheck(val kind:String,val title:String,val reason:String,val origin:String,val reading:DriveCheckReading)

/** Presentation priority: SDK notices first, the user's SOC criterion second. Not a risk ranking.
 * No writers, subscriptions, AI calls or fault classification. Home and health share this list. */
object VehicleAttention {
    val kinds=listOf("POWER_DOWN","BRAKE_FLUID","ENGINE_OIL","WASHER_LOW","EV_SOC")
    fun list(attempt:(String)->VehicleObservation?,last:(String)->VehicleObservation?,policy:SocPolicy,now:Long=System.currentTimeMillis()):List<VehicleCheck> =
        kinds.distinct().mapNotNull{kind->
            val value=DriveCheck.assess(kind,attempt(kind),last(kind),policy,now)
            if(!value.attention)null else VehicleCheck(kind,value.title,
                if(kind=="EV_SOC")"사용자 기준 ${policy.threshold}% 미만이에요."else value.value,
                if(kind=="EV_SOC")"사용자 기준 안내"else"차량에서 받은 안내",value)
        }
}
