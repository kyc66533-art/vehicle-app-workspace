package com.nextdrive.pleos

import com.nextdrive.pleos.analytics.*
import com.nextdrive.pleos.ai.AnalysisExplanation
import com.nextdrive.pleos.vehicle.*
import org.json.JSONObject

/** In-memory only, explicitly TEST_ONLY. Never appended to any observation or user database. */
object SyntheticSocFixture {
    fun payload(now:Long=System.currentTimeMillis()):String {
        val points=listOf(80,60,18).mapIndexed{i,n->val at=now-(2-i)*1000
            AnalysisPoint(ObservationRef("SYNTHETIC_NOT_A_REAL_STORE",i+1L,VehicleObservation("EV_SOC",n.toString(),"%",at,ObservationSource.TEST_ONLY,true,true,"SYNTHETIC_ONLY",ObservationStatus.RECEIVED,"In-memory Gemini PoC fixture",at)),at,n.toDouble(),0)
        }
        val a=VehicleAnalysis("EV_SOC",now-2000,now,points,mapOf("synthetic_fixture" to 3),emptyList(),emptyList(),false)
        return JSONObject(AnalysisExplanation.payload(a)).put("synthetic",true).put("fixture","SOC_80_60_18_V1")
            .put("criterion",JSONObject().put("operator","LT").put("threshold",20)).toString()
    }
}
