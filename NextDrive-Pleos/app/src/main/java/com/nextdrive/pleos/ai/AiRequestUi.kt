package com.nextdrive.pleos.ai

import com.nextdrive.pleos.analytics.VehicleAnalysis
import org.json.JSONObject

/** Read-only wording for the existing wire contracts; never changes consent or source records. */
object AiRequestUi {
    fun shouldRefresh(snapshot:JSONObject?,now:Long=System.currentTimeMillis()):Boolean = snapshot==null||snapshot.optBoolean("needs_refresh")||now-snapshot.optLong("checked_at") !in 0 until 60000
    fun summary(payload:String):String {
        val p=JSONObject(payload)
        if(p.optString("contract")==IssueExplanation.CONTRACT){
            val category=when(p.getString("category")){"NOISE"->"소음";"VIBRATION"->"진동";"WARNING"->"경고등";else->"기타"}
            return "증상 종류: $category\n보낼 증상: ${p.getString("situation")}\n\n이 내용만 Cloudflare 중계와 Google Gemini로 보내요. 작성자·메모·시간·차량값·다른 기록은 보내지 않아요. 원래 기록은 바뀌지 않아요."
        }
        val f=p.getJSONObject("facts")
        fun value(k:String)=VehicleAnalysis.fmt(f.getDouble(k))+"%"
        return "보낼 정보: 최근 충전량 ${value("latest")}, 최소 ${value("minimum")}, 최대 ${value("maximum")}, 수신값 평균 ${value("mean_of_receipts")}\n비교 가능한 변화량과 표본 수·경과시간·품질 요약도 보내요. 이름·위치·증상 원문·원본 DB는 보내지 않아요."
    }
    fun block(snapshot:JSONObject?,pending:Boolean):String? {
        if(pending||snapshot==null)return null // An existing operation is read, not replaced.
        if(snapshot.optBoolean("needs_refresh"))return "이용 가능 횟수를 다시 확인해 주세요. 기존 설명은 계속 볼 수 있어요."
        if(snapshot.optBoolean("uncertain_block"))return "이전 요청 결과를 확인 중이에요. 새 요청은 만들지 않아요."
        if(!snapshot.optBoolean("model_enabled"))return "현재 새 AI 설명이 꺼져 있어요. 기존 설명은 계속 볼 수 있어요."
        if(snapshot.optInt("remaining",0)<=0)return if(snapshot.optString("quota_mode")=="recurring")"일일 또는 월간 한도에 도달했어요. 기존 설명은 계속 볼 수 있어요. 다음 이용 전 횟수를 다시 확인해 주세요." else "승인된 이용 횟수를 모두 사용했어요. 현재는 일일 자동 갱신 정책이 아니에요. 기존 설명은 계속 볼 수 있어요."
        return null
    }
    fun quotaText(s:JSONObject):String {
        val mode=if(s.optString("quota_mode")=="recurring")"한국 시간 기준 일일·월간 한도가 적용돼요. 일일 갱신 후에도 월간 한도는 유지돼요." else "현재는 누적 승인 정책이에요. 날짜가 바뀌어도 자동 충전되지 않아요."
        return "이용 가능한 새 설명: ${s.getInt("remaining")}회\n$mode\n기존 TEST ${s.getInt("legacy_total")}회 · 정식 요청 시도 ${s.getInt("portable_attempts")}회\n${if(s.optString("quota_mode")=="recurring")"월간 서비스 상한"else"누적 승인 상한"} ${s.getInt("global_total")}회\n모델 ${if(s.getBoolean("model_enabled"))"켜짐"else"꺼짐"} · 불확실한 이전 요청 ${if(s.optBoolean("uncertain_block"))"있음"else"없음"}\n횟수 확인과 저장 설명 조회에는 Gemini를 호출하지 않아요."
    }
}
