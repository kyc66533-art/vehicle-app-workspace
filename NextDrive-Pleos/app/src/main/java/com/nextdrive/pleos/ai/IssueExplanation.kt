package com.nextdrive.pleos.ai

import com.nextdrive.pleos.analytics.VehicleAnalytics
import com.nextdrive.pleos.model.VehicleIssue
import org.json.JSONObject

/** A read-only, single-record projection. No memo, author, ID, time, history or sensor data. */
object IssueExplanation {
    const val CONTRACT="caritda-issue-v1"
    const val CONSENT="issue-explanation-v1"
    private val privateData=Regex("https?://|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|(?:\\+?82[- .]?)?01[016789][- .]?\\d{3,4}[- .]?\\d{4}|\\b[A-HJ-NPR-Z0-9]{17}\\b",RegexOption.IGNORE_CASE)
    private val instruction=Regex("ignore.{0,30}(instruction|previous)|system\\s*(prompt|message)|api[ _-]?key|bearer\\s|이전.{0,15}지시.{0,15}무시|시스템.{0,8}(프롬프트|명령)|비밀키|토큰.{0,8}(출력|공개)",RegexOption.IGNORE_CASE)
    fun payload(issue:VehicleIssue,excerpt:String=issue.situation):String {
        if(issue.deletedAt!=null)throw PortableFailure("ISSUE_UNAVAILABLE")
        val text=excerpt.trim()
        if(text.length !in 3..800||text.any{it.code in 0..8||it.code in 11..12||it.code in 14..31})throw PortableFailure("ISSUE_INPUT_INVALID")
        if(privateData.containsMatchIn(text))throw PortableFailure("ISSUE_PRIVATE_DATA")
        if(instruction.containsMatchIn(text))throw PortableFailure("ISSUE_INSTRUCTIONS")
        return JSONObject().put("contract",CONTRACT).put("source","USER_SELECTED_RECORD").put("category",issue.type.name).put("situation",text).toString()
    }
    fun jobKey(issue:VehicleIssue)=VehicleAnalytics.id(issue.id+":"+issue.revision+":"+issue.type.name+":"+issue.situation)
    fun validate(a:JSONObject,payload:String,operation:String):String {
        fun guard(ok:Boolean,code:String){if(!ok)throw PortableFailure(code)}
        val facts=JSONObject(payload)
        guard(a.optString("contract")==CONTRACT&&a.optString("operation_id")==operation,"APP_CONTRACT")
        guard(a.optString("origin")=="MODEL"&&a.optString("model")==PortableAnalysis.MODEL,"APP_ORIGIN")
        guard(a.optString("evidence_digest")==VehicleAnalytics.id(payload)&&PortableAnalysis.equal(a.optJSONObject("facts"),facts),"APP_FACTS")
        val quote=a.optString("evidence_quote");guard(quote.length>=3&&facts.getString("situation").contains(quote),"APP_FACTS")
        val numbers=Regex("\\d+(?:\\.\\d+)?").findAll(facts.getString("situation")).map{it.value}.toSet()
        val unsafe=Regex("확정|확실|틀림없|원인입니다|원인이에요|고장입니다|고장이에요|수리.{0,8}완료|안전합니다|안전해요|운행해도|계속 운전|위험.{0,5}없|https?:|[{}<>]|API|프롬프트",RegexOption.IGNORE_CASE)
        for(k in listOf("possible_causes","checks","inspection")){
            val t=a.optString(k);guard(t.length in 5..350&&Regex("[가-힣]").containsMatchIn(t),"APP_TEXT")
            guard(!unsafe.containsMatchIn(t)&&Regex("\\d+(?:\\.\\d+)?").findAll(t).all{it.value in numbers},"APP_UNSAFE_PROSE")
        }
        guard(Regex("가능|수 있|수도|추정|단정.{0,5}없|확인.{0,5}필요").containsMatchIn(a.getString("possible_causes")),"APP_UNSAFE_PROSE")
        return "가능한 원인 · 확정 진단이 아니에요\n${a.getString("possible_causes")}\n\n추가로 확인해 주세요\n${a.getString("checks")}\n\n정비소 점검이 필요한 경우\n${a.getString("inspection")}\n\n선택한 기록을 바탕으로 AI가 작성했어요. 실제 차량 상태와 다를 수 있어요."
    }
}
