package com.nextdrive.pleos.voice

import com.nextdrive.pleos.ai.PortableFailure
import org.json.JSONObject

/** Only an explicit relay RATE_LIMIT before reservation. Never retries a provider or UNKNOWN. */
object VoiceRateWindow {
    fun mayWait(code:String,pending:JSONObject?)=code=="RATE_LIMIT"&&pending!=null&&pending.has("intent")&&pending.has("operation")&&pending.optString("state") in setOf("NEW","ISSUED")
    fun awaitNextWindow(valid:()->Boolean){
        val deadline=android.os.SystemClock.elapsedRealtime()+61000
        while(android.os.SystemClock.elapsedRealtime()<deadline){
            if(!valid())throw PortableFailure("AUTH_CANCELLED")
            Thread.sleep(200)
        }
        if(!valid())throw PortableFailure("AUTH_CANCELLED")
    }
}
