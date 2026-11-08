package com.nextdrive.pleos.ai

import com.nextdrive.pleos.analytics.*
import org.json.JSONObject

/** No networking or keys here. Consent binds to an immutable minimized analysis payload. */
class AnalysisConsent(val payload:String){
    val digest=VehicleAnalytics.id(payload)
    private var accepted=false
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
        check(consent.permits(payload)){"전송 내용 확인과 동의가 필요해요."}
        require(body.length<=16384)
        val j=JSONObject(body);require(j.getString("origin")=="MODEL"&&j.getString("evidence_digest")==consent.digest)
        val facts=j.getJSONObject("facts");val expected=JSONObject(payload)
        require(facts.keys().asSequence().toSet()==expected.keys().asSequence().toSet())
        expected.keys().forEach{k->require(facts.get(k).toString()==expected.get(k).toString()){"분석 근거와 다른 수치·조건이 있어요."}}
        val text=j.getString("explanation");require(text.length in 1..600&&Regex("[가-힣]").containsMatchIn(text))
        // Numeric facts are rendered locally from the exact facts object. Free prose cannot
        // swap two otherwise allowed numbers (e.g. latest SOC and a user threshold).
        require(!Regex("\\d").containsMatchIn(text)){"수치·시각은 검증된 근거에서만 표시해요."}
        require(!Regex("고장|수리|안전|운행 가능|브레이크|타이어|교체|정비 완료").containsMatchIn(text)){"근거 없는 진단·안전 설명은 표시하지 않아요."}
        require(j.getString("action") in setOf("CHECK_VEHICLE_DISPLAY","CHECK_CHARGING_PLAN","RETRY_RECEIPT","INSUFFICIENT_DATA"))
        return text
    }
}
