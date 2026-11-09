package com.nextdrive.pleos.ai

import org.json.JSONObject

/** Closed vocabulary only: never a provider message, credential, or model text. */
object RelayDiagnostics {
    private val codes=setOf("UNAUTHORIZED","TOKEN_EXPIRED","SECURITY_NOT_CONFIGURED","POC_DISABLED","TOTAL_LIMIT","RATE_LIMIT","DUPLICATE_REQUEST","BUDGET_UNAVAILABLE","RELAY_UNAVAILABLE","HTTP_ERROR","MODEL_QUOTA","PROVIDER_AUTH","MODEL_TIMEOUT","MODEL_NETWORK","MODEL_ERROR","MODEL_FORMAT","MODEL_BLOCKED","MODEL_UNGROUNDED","MODEL_REQUEST_REJECTED","MODEL_NOT_FOUND","MODEL_HTTP_ERROR","MODEL_BODY_INVALID","MODEL_SAFETY_BLOCKED","MODEL_NO_CANDIDATE","MODEL_OUTPUT_LIMIT","MODEL_FINISH_REJECTED","MODEL_EMPTY_TEXT","MODEL_JSON_INVALID","MODEL_SCHEMA_INVALID","MODEL_TEXT_INVALID","MODEL_NUMERIC_PROSE","MODEL_UNSAFE_PROSE","MODEL_ACTION_INVALID","APP_PREFLIGHT","APP_ENVELOPE","APP_RESPONSE_JSON","APP_CONSENT","APP_ORIGIN","APP_EVIDENCE","APP_FACTS","APP_TEXT","APP_NUMERIC_PROSE","APP_UNSAFE_PROSE","APP_ACTION")
    fun code(raw:String)=raw.takeIf{it in codes||it in setOf("APP_NETWORK","APP_TIMEOUT","APP_RESPONSE_LIMIT","APP_RELAY_VERSION")}?:"HTTP_ERROR"
    fun readyForModel(preflight:JSONObject)=preflight.optString("release")=="gemini-response-fix-v2"
    fun read(j:JSONObject?):String {
        if(j==null)return "진단 정보 없음 · 이전 Worker일 수 있어요."
        val out=mutableListOf<String>()
        if(j.optString("release")=="gemini-response-fix-v2")out.add("Worker: gemini-response-fix-v2")
        val allowed=mapOf(
            "stage" to setOf("REQUEST","BUDGET","PROVIDER_HTTP","PROVIDER_BODY","PROVIDER_FINISH","PROVIDER_JSON","RELAY_GUARD","COMPLETE"),
            "finish_reason" to setOf("STOP","MAX_TOKENS","SAFETY","RECITATION","LANGUAGE","OTHER","BLOCKLIST","PROHIBITED_CONTENT","SPII","MALFORMED_FUNCTION_CALL","IMAGE_SAFETY","FINISH_REASON_UNSPECIFIED","UNKNOWN"),
            "provider_status" to setOf("INVALID_ARGUMENT","UNAUTHENTICATED","PERMISSION_DENIED","NOT_FOUND","RESOURCE_EXHAUSTED","UNAVAILABLE","INTERNAL","DEADLINE_EXCEEDED","FAILED_PRECONDITION","UNKNOWN"),
            "request_field" to setOf("TEMPERATURE","RESPONSE_JSON_SCHEMA","MAX_OUTPUT_TOKENS"))
        allowed.forEach{(key,values)->j.optString(key).takeIf{it in values}?.let{out.add("$key: $it")}}
        listOf("upstream_status" to 100..599,"provider_ms" to 0..60000).forEach{(key,range)->
            (j.opt(key) as? Number)?.toInt()?.takeIf{it in range}?.let{out.add("$key: $it")}}
        (j.opt("prompt_blocked") as? Boolean)?.let{out.add("prompt_blocked: $it")}
        return out.joinToString("\n").ifEmpty{"허용된 진단 정보 없음"}
    }
}

class AnalysisValidationFailure(val code:String):IllegalArgumentException(code)
