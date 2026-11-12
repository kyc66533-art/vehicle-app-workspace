package com.nextdrive.pleos.voice

import com.nextdrive.pleos.ai.IssueDialogueContract
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.ui.VehicleAttention
import java.util.UUID

/** The existing record/analysis/query adapter, shared by the home host and manual voice screen. */
class VoiceIssueContext(
    private val service:CaritdaService, private val conversations:ConversationStore,
    private val raw:ObservationStore, private val low:LowSocStore, private val attention:AttentionEventStore,
    private val valid:()->Boolean, private val owner:()->String, private val account:()->String,
    private val controller:()->VoiceConversationController, private val bridge:()->VoiceAiBridge,
    private val attentionKind:()->String?
) {
    private var anchorUser=""
    fun save(token:String,text:String,type:SymptomType):CommitReceipt {
        check(valid());check(service.draft()==null){"DRAFT_ALREADY_EXISTS"}
        val now=System.currentTimeMillis()
        val draft=JournalDraft(UUID.nameUUIDFromBytes(token.toByteArray()).toString(),owner(),service.driver().label,"RECORD",type=type,body=text,foundAt=now,updatedAt=now)
        val command=DraftCommit(token,draft)
        conversations.stageLink(controller().sessionId!!,token,command.digest,owner(),draft.id)
        service.saveDraft(draft);check(valid())
        val receipt=service.commitDraft(command);anchorUser=token.substringAfterLast(':')
        controller().alertId?.let{attention.link(it,receipt.issueId,receipt.revision)}
        return receipt
    }
    fun analyze(issue:String,revision:Int,turn:String,done:(Result<VoiceConversationController.Reply>)->Unit) {
        val selected=service.issue(issue);check(selected.revision==revision)
        bridge().request(selected,turn,null,emptyList(),done)
    }
    fun dialogue(issue:String,revision:Int,turn:String,question:String,done:(Result<VoiceConversationController.Reply>)->Unit) {
        val selected=service.issue(issue);check(selected.revision==revision)
        val messages=conversations.messages(controller().sessionId!!,owner(),account())
        val index=messages.indexOfFirst{it.id==anchorUser}.coerceAtLeast(0)
        // Local SOC/vehicle queries can contain SDK values. They are NOT approved
        // symptom dialogue or prior AI inference and must never enter the model context.
        val pairs=messages.drop(index).windowed(2).filter{it[0].role=="USER"&&it[1].role=="ASSISTANT"&&it[0].id!=turn&&it[1].revision==revision&&it[1].origin in setOf("GEMINI_NEW","GEMINI_SAVED","GEMINI_RECOVERED")}
        val context=pairs.takeLast(2).flatMap{it.map{m->IssueDialogueContract.Turn(m.role,if(m.role=="ASSISTANT")m.text.substringBefore("\n\n")else m.text)}}
        bridge().request(selected,turn,question,context,done)
    }
    fun query(command:VoiceCommand):String=when(command) {
        VoiceCommand.Soc->{val v=raw.mostRecent("EV_SOC")
            if(v!=null&&DriveCheck.accepted(v)&&System.currentTimeMillis()-v.receivedAt!! in 0 until 600000)
                "최근 SDK로 확인한 배터리 잔량은 ${v.raw}%예요. 수신 시각 ${java.time.Instant.ofEpochMilli(v.receivedAt).atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalTime().withNano(0)}예요."
            else "최근 유효한 배터리 값을 확인하지 못했어요. 차량 상태 화면에서 새로 확인해 주세요."
        }
        VoiceCommand.Attention->{val checks=VehicleAttention.list({raw.mostRecent(it)},{raw.mostRecent(it,true)},low.policy())
            if(checks.isEmpty())"현재 받은 값에서 확인 필요 안내가 없어요. 차량 전체가 정상이라는 뜻은 아니에요."else checks.joinToString(" "){it.title+". "+it.reason}}
        VoiceCommand.Recent->service.issues().firstOrNull()?.let{"최근 기록은 ${it.situation}. 현재 ${it.status.label} 상태예요."}?:"저장된 문제 기록이 없어요."
        VoiceCommand.Handover->service.issues().filter{service.needsAcknowledgement(it)}.take(3).let{if(it.isEmpty())"아직 확인하지 않은 다른 운전자의 기록이 없어요."else"다른 운전자가 남긴 미확인 기록이에요. "+it.joinToString(" "){i->i.situation}}
        VoiceCommand.RecordContext->DriveCheck.draftText(attentionKind()?:"EV_SOC")
        is VoiceCommand.FollowUp->"이 안내는 사용자 기준에 따른 차량 정보예요. 차량의 표시와 사용 설명서를 확인하고, 충전이 필요하면 안전하게 정차한 뒤 충전 계획을 세워 주세요. 고장 진단은 아니에요."
        else->"차량 상태나 기록에 대해 다시 말씀해 주세요."
    }
}
