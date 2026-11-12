package com.nextdrive.pleos.ai

import com.nextdrive.pleos.model.VehicleIssue
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Single saved-record use case shared by the ordinary UI and Gleo.
 * No record writer, sensor reader, login, automatic retry or consent bypass lives here. */
object IssueAnalysisService {
    enum class Origin { NEW, RECOVERED, SAVED }
    data class Result(val text:String,val situation:String,val origin:Origin) {
        val title get()=when(origin){
            Origin.NEW->"이번 요청으로 받은 Gemini 설명"
            Origin.RECOVERED->"진행 중이던 요청에서 확인한 Gemini 설명"
            Origin.SAVED->"이전에 저장된 Gemini 설명 · 새 요청 없음"
        }
    }
    private val lease=IssueAiLease.busy

    fun saved(last:JSONObject):Result {
        val answer=last.getJSONObject("answer")
        val payload=last.getString("payload")
        return Result(IssueExplanation.validate(answer,payload,answer.getString("operation_id")),
            JSONObject(payload).getString("situation"),Origin.SAVED)
    }

    /** Call only after an explicit request. Pending work always uses its frozen payload/intent.
     * The lease spans cancellation until the blocking call actually exits. */
    fun explain(api:PortableAiClient,selected:VehicleIssue,excerpt:String,fresh:Boolean,
                current:()->VehicleIssue?):Result {
        if(!lease.compareAndSet(false,true))throw PortableFailure("REQUEST_PENDING")
        try {
            val latest=current()?:throw PortableFailure("ISSUE_UNAVAILABLE")
            if(latest.deletedAt!=null)throw PortableFailure("ISSUE_UNAVAILABLE")
            if(IssueExplanation.jobKey(selected)!=IssueExplanation.jobKey(latest))throw PortableFailure("ISSUE_CHANGED")
            val job=api.jobState()
            val cached=!fresh&&job.has("last")
            val recovering=job.has("pending")&&!cached
            val payload=if(recovering||cached)""else IssueExplanation.payload(latest,excerpt)
            val answer=api.explain(payload,fresh)
            // Validate the actual frozen request, including an edited/redacted excerpt.
            val last=api.jobState().getJSONObject("last")
            val frozen=last.getString("payload")
            val text=IssueExplanation.validate(answer,frozen,answer.getString("operation_id"))
            return Result(text,JSONObject(frozen).getString("situation"),
                if(cached)Origin.SAVED else if(recovering)Origin.RECOVERED else Origin.NEW)
        } finally { lease.set(false) }
    }
}
