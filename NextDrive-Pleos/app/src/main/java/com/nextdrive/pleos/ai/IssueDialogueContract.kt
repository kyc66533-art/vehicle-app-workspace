package com.nextdrive.pleos.ai

import com.nextdrive.pleos.model.VehicleIssue
import com.nextdrive.pleos.analytics.VehicleAnalytics
import org.json.JSONArray
import org.json.JSONObject

/** Separate consent/endpoint. No identity, coordinates, memo, other records or sensor facts. */
object IssueDialogueContract {
    const val CONTRACT="caritda-issue-dialogue-v1"
    const val CONSENT="issue-dialogue-v1"
    data class Turn(val role:String,val text:String)
    private fun equal(a:Any?,b:Any?):Boolean=when{
        a is JSONArray&&b is JSONArray->a.length()==b.length()&&(0 until a.length()).all{equal(a.get(it),b.get(it))}
        a is JSONObject&&b is JSONObject->a.keys().asSequence().toSet()==b.keys().asSequence().toSet()&&a.keys().asSequence().all{equal(a.get(it),b.get(it))}
        else->PortableAnalysis.equal(a,b)
    }
    fun payload(issue:VehicleIssue,question:String,context:List<Turn>,excerpt:String=issue.situation):String {
        IssueExplanation.payload(issue,excerpt)
        require(question.length in 3..240&&question==question.trim()&&context.size<=4&&context.size%2==0){"DIALOGUE_INPUT_INVALID"}
        IssueExplanation.payload(issue.copy(situation=question),question)
        val turns=JSONArray();context.forEachIndexed{i,t->
            require(t.role==if(i%2==0)"USER"else"ASSISTANT");require(t.text.length in 3..500){"DIALOGUE_CONTEXT_TOO_LARGE"}
            IssueExplanation.payload(issue.copy(situation=t.text),t.text)
            turns.put(JSONObject().put("role",t.role).put("origin",if(t.role=="USER")"USER_QUESTION"else"ASSISTANT_INFERENCE").put("text",t.text))
        }
        val result=JSONObject().put("contract",CONTRACT).put("source","USER_AUTHORIZED_ISSUE_DIALOGUE")
            .put("selected_record",JSONObject().put("category",issue.type.name).put("situation",excerpt))
            .put("question",question).put("context_turns",turns).put("facts",JSONArray()).toString()
        require(result.toByteArray(Charsets.UTF_8).size<=3500){"DIALOGUE_TOO_LARGE"};return result
    }
    fun validate(a:JSONObject,payload:String,operation:String):String {
        fun guard(ok:Boolean,code:String){if(!ok)throw PortableFailure(code)}
        val facts=JSONObject(payload)
        guard(a.optString("contract")==CONTRACT&&a.optString("operation_id")==operation,"APP_CONTRACT")
        guard(a.optString("origin")=="MODEL"&&a.optString("model")==PortableAnalysis.MODEL,"APP_ORIGIN")
        guard(a.optString("evidence_digest")==VehicleAnalytics.id(payload)&&equal(a.optJSONObject("facts"),facts),"APP_FACTS")
        val record=facts.getJSONObject("selected_record");val quote=a.optString("evidence_quote")
        guard(quote.length>=3&&record.getString("situation").contains(quote),"APP_FACTS")
        val refs=a.optJSONArray("context_refs")?:throw PortableFailure("APP_FACTS")
        guard(refs.length()<=4&&(0 until refs.length()).all{refs.opt(it) is Number&&refs.getDouble(it)==refs.getInt(it).toDouble()&&refs.getInt(it) in 0 until facts.getJSONArray("context_turns").length()},"APP_FACTS")
        guard(facts.getJSONArray("context_turns").length()==0||(0 until refs.length()).any{refs.getInt(it)%2==1},"APP_FACTS")
        val numbers=Regex("\\d+(?:\\.\\d+)?").findAll(record.getString("situation")+" "+facts.getString("question")).map{it.value}.toSet()
        val unsafe=Regex("확정|틀림없|원인입니다|원인이에요|고장입니다|고장이에요|수리.{0,8}완료|안전합니다|안전해요|운행해도|계속 운전|위험.{0,5}없|https?:|[{}<>]|API|프롬프트",RegexOption.IGNORE_CASE)
        for(k in listOf("answer","checks","inspection","uncertainty")){
            val t=a.optString(k);guard(t.length in 5..350&&Regex("[가-힣]").containsMatchIn(t),"APP_TEXT")
            guard(!unsafe.containsMatchIn(t)&&Regex("\\d+(?:\\.\\d+)?").findAll(t).all{it.value in numbers},"APP_UNSAFE_PROSE")
        }
        guard(a.optLong("generated_at")>0&&Regex("수 있|수도|가능|확인|단정|판단.{0,5}어려").containsMatchIn(a.getString("uncertainty")),"APP_FACTS")
        return listOf("answer","checks","inspection","uncertainty").joinToString("\n\n"){a.getString(it)}
    }
}
