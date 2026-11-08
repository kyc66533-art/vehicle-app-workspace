package com.nextdrive.pleos.domain

import com.nextdrive.pleos.model.VehicleIssue
import java.text.Normalizer
import java.util.Locale

/** Local projection only. Never edits stored text, sensor observations, or record state. */
object RecordSearch {
    fun matches(issue:VehicleIssue,query:String):Boolean {
        fun normalized(text:String)=Normalizer.normalize(text,Normalizer.Form.NFC).lowercase(Locale.ROOT)
        val words=normalized(query).trim().split(Regex("\\s+")).filter{it.isNotEmpty()}
        val haystack=normalized("${issue.type.label}\n${issue.situation}\n${issue.memo}")
        return words.all{haystack.contains(it)}
    }
}
