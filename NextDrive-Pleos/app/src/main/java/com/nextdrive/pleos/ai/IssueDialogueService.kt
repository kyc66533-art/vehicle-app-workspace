package com.nextdrive.pleos.ai

import com.nextdrive.pleos.model.VehicleIssue
import java.util.concurrent.atomic.AtomicBoolean

object IssueDialogueService {
    private val lease=IssueAiLease.busy
    fun answer(api:PortableAiClient,issue:VehicleIssue,question:String,history:List<IssueDialogueContract.Turn>,current:()->VehicleIssue?):String {
        if(!lease.compareAndSet(false,true))throw PortableFailure("REQUEST_PENDING")
        try{
            check(current()?.let{IssueExplanation.jobKey(it)==IssueExplanation.jobKey(issue)}==true){"ISSUE_CHANGED"}
            val job=api.jobState()
            val payload=if(job.has("pending")||job.has("last"))""else IssueDialogueContract.payload(issue,question,history)
            val answer=api.explain(payload)
            val last=api.jobState().getJSONObject("last")
            return IssueDialogueContract.validate(answer,last.getString("payload"),answer.getString("operation_id"))
        } finally {lease.set(false)}
    }
}
