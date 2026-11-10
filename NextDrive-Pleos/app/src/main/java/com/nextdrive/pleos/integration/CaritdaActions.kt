package com.nextdrive.pleos.integration

import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.vehicle.*
import org.json.JSONObject

/** Read-only command handler shared by Gleo intents and isolated validation. No Gemini request. */
object CaritdaActions {
    const val QUERY="QUERY_CARITDA_CONTEXT"
    const val DRAFT="DRAFT_CARITDA_ISSUE"
    val topics=setOf("recent_issues","handover","vehicle","tires")
    fun topic(value:String?):String{require(value in topics){"ACTION_INVALID_TOPIC"};return value!!}
    fun draft(value:String?):String{val s=value?.trim().orEmpty();require(s.length<=800){"ACTION_INPUT_TOO_LONG"};return s}
    fun query(topic:String,service:CaritdaService,observations:ObservationStore):String{
        topic(topic)
        return when(topic){
            "recent_issues"->service.issues().take(3).joinToString("\n"){it.type.label+" · "+it.situation.take(160)+" · "+it.status.label}.ifBlank{"저장된 문제 기록이 없어요."}
            "handover"->{if(!service.confirmed())"기록할 사람을 선택한 뒤 인계 내용을 확인해 주세요." else service.issues().filter{service.needsAcknowledgement(it)}.take(3).joinToString("\n"){it.type.label+" · "+it.situation.take(160)}.ifBlank{"아직 읽지 않은 전달 기록이 없어요."}}
            "vehicle"->listOf("EV_SOC" to "배터리 충전량","ODOMETER" to "누적 주행거리").joinToString("\n"){(kind,label)->val last=observations.mostRecent(kind,true);val attempt=observations.mostRecent(kind);label+": "+if(last==null)"값을 받지 못했어요."else last.raw+" "+last.unit+" · 조회 "+com.nextdrive.pleos.ui.VehicleUi.shortTime(last.receivedAt!!)+(if(attempt?.status!=ObservationStatus.RECEIVED)" · 이전 수신값"else"")}
            else->"실제 타이어 압력과 TPMS 고장은 이 공개 SDK에서 확인하지 못했어요. 저압 기준값은 실제 압력이 아니에요."
        }
    }
    fun callback(function:String,status:String,message:String):JSONObject{
        require(function in setOf(QUERY,DRAFT)&&status in setOf("success","asking","fail")&&message.length<=600)
        return JSONObject().put("functionName",function).put("status",status).put("message",message).put("mode","silent")
    }
}
