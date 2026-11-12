package com.nextdrive.pleos.domain

import com.nextdrive.pleos.model.JournalDraft
import com.nextdrive.pleos.analytics.VehicleAnalytics
import org.json.JSONArray

/** Stable recognition-turn identity. Retry time is deliberately excluded from the digest. */
data class DraftCommit(val token:String,val draft:JournalDraft) {
    init { require(token.length in 1..160 && draft.kind=="RECORD" && draft.target==null && draft.revision==1) }
    val digest:String get()=VehicleAnalytics.id(JSONArray(listOf(draft.ownerId,draft.ownerName,draft.kind,
        draft.target.orEmpty(),draft.revision,draft.type.name,draft.body,draft.memo,draft.foundAt)).toString())
}
data class CommitReceipt(val token:String,val digest:String,val ownerId:String,val issueId:String,val revision:Int,val at:Long)
