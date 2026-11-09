package com.nextdrive.pleos.ai

import com.nextdrive.pleos.analytics.*
import org.json.JSONObject

/** No networking or keys here. Consent binds to an immutable minimized analysis payload. */
class AnalysisConsent(val payload:String){
    val digest=VehicleAnalytics.id(payload)
    @Volatile private var accepted=false
    fun accept(){accepted=true}
    fun revoke(){accepted=false}
    fun permits(candidate:String)=accepted&&VehicleAnalytics.id(candidate)==digest
}
object AnalysisExplanation {
    fun payload(a:VehicleAnalysis):String=JSONObject().put("signal",a.kind).put("source",a.latest?.ref?.value?.source?.name?:"NONE")
        .put("unit",a.unit).put("first_received_at",a.points.firstOrNull()?.at).put("last_received_at",a.latest?.at)
        .put("sample_count",a.points.size).put("latest",a.latest?.number).put("delta",a.delta)
        .put("minimum",a.values.minOrNull()).put("maximum",a.values.maxOrNull()).put("mean_of_receipts",a.values.takeIf{it.isNotEmpty()}?.average())
        .put("rule",VehicleAnalytics.RULE).put("quality",JSONObject(a.quality))
        .put("limitations","app_receipts_not_ecu_time;session_not_trip;emulator_is_simulated;no_diagnosis_no_safety_decision;observation_mean_not_time_average").toString()
    /** Echoed structured facts and evidence hash must match. Regex is only an additional gate. */
    fun validate(body:String,consent:AnalysisConsent,payload:String):String {
        fun guard(ok:Boolean,code:String){if(!ok)throw AnalysisValidationFailure(code)}
        guard(consent.permits(payload),"APP_CONSENT")
        guard(body.length<=16384,"APP_RESPONSE_JSON")
        val j=try{JSONObject(body)}catch(_:Exception){throw AnalysisValidationFailure("APP_RESPONSE_JSON")}
        guard(j.optString("origin")=="MODEL","APP_ORIGIN")
        guard(j.optString("evidence_digest")==consent.digest,"APP_EVIDENCE")
        val facts=j.optJSONObject("facts")?:throw AnalysisValidationFailure("APP_FACTS");val expected=JSONObject(payload)
        guard(facts.keys().asSequence().toSet()==expected.keys().asSequence().toSet(),"APP_FACTS")
        expected.keys().forEach{k->guard(facts.get(k).toString()==expected.get(k).toString(),"APP_FACTS")}
        val text=j.opt("explanation") as? String?:throw AnalysisValidationFailure("APP_TEXT")
        guard(text.length in 1..600&&Regex("[가-힣]").containsMatchIn(text),"APP_TEXT")
        // Numeric facts are rendered locally from the exact facts object. Free prose cannot
        // swap two otherwise allowed numbers (e.g. latest SOC and a user threshold).
        guard(!Regex("\\d").containsMatchIn(text),"APP_NUMERIC_PROSE")
        guard(!Regex("고장|수리|안전|운행 가능|브레이크|타이어|교체|정비 완료").containsMatchIn(text),"APP_UNSAFE_PROSE")
        guard(j.optString("action") in setOf("CHECK_VEHICLE_DISPLAY","CHECK_CHARGING_PLAN","RETRY_RECEIPT","INSUFFICIENT_DATA"),"APP_ACTION")
        return text
    }
}
