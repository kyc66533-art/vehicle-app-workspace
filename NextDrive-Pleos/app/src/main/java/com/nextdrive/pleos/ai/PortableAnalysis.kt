package com.nextdrive.pleos.ai

import com.nextdrive.pleos.analytics.*
import com.nextdrive.pleos.vehicle.*
import org.json.JSONObject

/** v2 minimizes the existing VehicleAnalytics result; never recomputes or mutates observations. */
object PortableAnalysis {
    const val CONTRACT="caritda-analysis-v2"
    const val CONSENT="soc-explanation-v1"
    const val MODEL="gemini-3.5-flash-lite"
    const val LIMITS="receipts_not_ecu;not_trip;not_time_average;no_diagnosis;no_safety_decision"
    fun payload(a:VehicleAnalysis,now:Long=System.currentTimeMillis()):String {
        val latest=a.latest?:throw PortableFailure("NO_VALID_SOC")
        require(a.kind=="EV_SOC")
        if(latest.ref.value.source!=ObservationSource.PLEOS_EMULATOR_SDK)throw PortableFailure("REAL_VEHICLE_NOT_APPROVED")
        if(now-latest.at !in 0 until 600000)throw PortableFailure("STALE_RECEIPT")
        if(a.lastAttempt?.let{!SnapshotStore.accepted(it.value)}==true)throw PortableFailure("LATEST_RECEIPT_FAILED")
        return JSONObject().put("contract",CONTRACT).put("signal","EV_SOC").put("source",latest.ref.value.source.name).put("synthetic",false).put("unit","%")
            .put("window",JSONObject().put("duration_ms",latest.at-a.points.first().at).put("latest_age_ms",now-latest.at).put("sample_count",a.points.size).put("truncated",a.truncated))
            .put("facts",JSONObject().put("latest",latest.number).put("minimum",a.values.min()).put("maximum",a.values.max()).put("mean_of_receipts",a.values.average()).put("delta_last_comparable",a.delta?:JSONObject.NULL))
            .put("quality",JSONObject().put("flag_count",a.quality.filterKeys{!it.contains("반복")&&!it.contains("오래된")}.values.sum()).put("segments",a.points.map{it.segment}.distinct().size).put("latest_valid",true))
            .put("rule",VehicleAnalytics.RULE).put("limitations",LIMITS).toString()
    }
    /** Key order and 18 vs 18.0 are irrelevant; types, missing fields and arrays are not. */
    fun equal(a:Any?,b:Any?):Boolean=when {
        a is JSONObject&&b is JSONObject->a.keys().asSequence().toSet()==b.keys().asSequence().toSet()&&a.keys().asSequence().all{equal(a.get(it),b.get(it))}
        a is Number&&b is Number->a.toDouble().isFinite()&&b.toDouble().isFinite()&&java.math.BigDecimal(a.toString()).compareTo(java.math.BigDecimal(b.toString()))==0
        a===JSONObject.NULL||a==null->b===JSONObject.NULL||b==null
        else->a==b
    }
    fun validate(body:JSONObject,payload:String,operation:String):String {
        fun guard(ok:Boolean,code:String){if(!ok)throw PortableFailure(code)}
        guard(body.optString("contract")==CONTRACT&&body.optString("operation_id")==operation,"APP_CONTRACT")
        guard(body.optString("origin")=="MODEL"&&body.optString("model")==MODEL,"APP_ORIGIN")
        guard(body.optString("evidence_digest")==VehicleAnalytics.id(payload)&&equal(body.optJSONObject("facts"),JSONObject(payload)),"APP_FACTS")
        val text=body.optString("explanation");guard(text.length in 1..260&&Regex("[가-힣]").containsMatchIn(text),"APP_TEXT")
        guard(!Regex("\\d|고장|수리|안전|운행 가능|브레이크|타이어|교체|정비 완료").containsMatchIn(text),"APP_UNSAFE_PROSE")
        val action=if(JSONObject(payload).getJSONObject("window").getInt("sample_count")<2)"INSUFFICIENT_DATA"else"CHECK_VEHICLE_DISPLAY"
        guard(body.optString("action")==action,"APP_ACTION")
        // Reuse the v1 evidence contract only for its independent prose/evidence guard.
        val consent=AnalysisConsent(payload).apply{accept()};AnalysisExplanation.validate(body.toString(),consent,payload)
        return text
    }
}
class PortableFailure(raw:String):IllegalStateException("AI_REQUEST_FAILED") {
    val code=raw.takeIf{Regex("[A-Z_]{1,64}|(?:AUTH_KEYS_HTTP|MODEL_HTTP)_[1-5][0-9]{2}").matches(it)}?:"AI_REQUEST_FAILED"
    fun displayMessage()="${userMessage()}\n확인 코드: $code"
    fun userMessage()=when(code){
        "AUTH_SIGN_IN_REQUIRED","AUTH_CREDENTIALS","AUTH_RELOGIN","RECENT_LOGIN_REQUIRED"->"로그아웃 후 다시 로그인해 주세요. 기존 차량 기록은 그대로예요."
        "AUTH_TOKEN_TIMEOUT","AUTH_NETWORK"->"계정 확인 응답이 늦어지고 있어요. 인터넷 연결을 확인한 뒤 다시 시도해 주세요."
        "UID_NOT_APPROVED"->"이 계정의 AI 이용 승인이 필요해요. 관리자에게 문의해 주세요."
        "DEVICE_LIMIT","DEVICE_REVOKED","DEVICE_NOT_REGISTERED","DEVICE_KEY_MISSING"->"이 기기의 AI 등록을 확인해 주세요. 기존 차량 기록은 그대로예요."
        "TOTAL_LIMIT","PERIOD_LIMIT","RATE_LIMIT","MODEL_QUOTA","CONTROL_RATE_LIMIT"->"요청 한도에 도달했어요. 자동으로 다시 요청하지 않아요."
        "PORTABLE_DISABLED","MODEL_DISABLED","POLICY_REQUIRED","POLICY_INVALID"->"AI 서버의 사용 승인이 아직 준비되지 않았어요. 차량 분석은 계속 볼 수 있어요."
        "STALE_RECEIPT","LATEST_RECEIPT_FAILED","NO_VALID_SOC"->"최근 차량 정보를 먼저 불러와 주세요. 이전 분석은 그대로예요."
        "MODEL_OUTCOME_UNKNOWN","APP_TIMEOUT","APP_NETWORK"->"응답을 확인하지 못했어요. 다시 눌러 기존 요청 결과를 확인할 수 있어요. 새 모델 요청은 자동으로 만들지 않아요."
        "REAL_VEHICLE_NOT_APPROVED"->"이번 시험은 에뮬레이터 충전량만 설명해요. 실차 정보 전송은 아직 승인되지 않았어요."
        "CONSENT_REQUIRED"->"전송할 내용을 확인하고 AI 이용에 동의해 주세요."
        "ISSUE_UNAVAILABLE","ISSUE_CHANGED"->"기록이 수정되거나 삭제됐어요. 원래 화면에서 다시 선택해 주세요."
        "ISSUE_INPUT_INVALID"->"설명할 증상을 3~800자로 적어 주세요. 원래 기록은 바뀌지 않아요."
        "ISSUE_PRIVATE_DATA"->"전송할 내용에서 전화번호·이메일·인터넷 주소·차량 식별번호를 빼 주세요."
        "ISSUE_INSTRUCTIONS"->"AI에 대한 명령 대신 차량 증상만 남겨 주세요. 원래 기록은 바뀌지 않아요."
        "MODEL_REQUEST_REJECTED","PROVIDER_AUTH"->"AI 제공자 설정을 확인해야 해요. 관리자에게 문의해 주세요. 자동 재요청하지 않아요."
        "MODEL_FINISH_REJECTED","MODEL_JSON_INVALID","MODEL_UNSAFE_PROSE","MODEL_EVIDENCE_INVALID","MODEL_NUMBER_INVALID","MODEL_CAUSE_NOT_CONDITIONAL","APP_FACTS","APP_UNSAFE_PROSE"->"근거에 맞는 AI 설명인지 확인하지 못했어요. 원래 기록은 그대로예요."
        else->if(code.startsWith("AUTH_KEYS_"))"서버에서 로그인 확인 정보를 불러오지 못했어요. 계정은 유지돼요. 관리자에게 확인 코드를 알려 주세요."else"AI 설명을 표시하지 못했어요. 차량 정보와 분석은 그대로 보존돼요."
    }
}
