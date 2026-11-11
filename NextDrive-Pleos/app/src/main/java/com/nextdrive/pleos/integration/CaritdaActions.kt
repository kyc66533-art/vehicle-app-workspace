package com.nextdrive.pleos.integration

import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.vehicle.*
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import android.content.Intent

/** Read-only command handler shared by Gleo intents and isolated validation. No Gemini request. */
object CaritdaActions {
    const val QUERY="QUERY_CARITDA_CONTEXT"
    const val DRAFT="DRAFT_CARITDA_ISSUE"
    const val ANALYZE="ANALYZE_CARITDA_ISSUE"
    val functions=setOf(QUERY,DRAFT,ANALYZE)
    val topics=setOf("recent_issues","handover","vehicle","battery","tires")
    data class Request(val function:String,val topic:String="",val situation:String="",val recordQuery:String="",val fresh:Boolean=false)
    data class Reply(val status:String,val message:String,val text:String,val data:JSONArray=JSONArray())
    /** Accept the documented ACTION_PARAM JSON, including its quoted-string example, or
     * the documented individual extras. Conflicting/unknown parameters fail closed. */
    fun parse(intent:Intent):Request {
        val fn=intent.action;require(fn in functions){"ACTION_UNSUPPORTED"}
        val keys=when(fn){QUERY->setOf("topic");DRAFT->setOf("situation");else->setOf("record_query","request_mode")}
        val raw=intent.getStringExtra("ACTION_PARAM")
        require(raw==null||raw.length<=4096){"ACTION_INPUT_TOO_LONG"}
        val args=if(raw==null)JSONObject()else {
            val value=JSONTokener(raw).nextValue()
            when(value){is JSONObject->value;is String->{require(value.length<=2048);JSONObject(value)};else->error("ACTION_INVALID_JSON")}
        }
        require(args.keys().asSequence().all{it in keys}){"ACTION_INVALID_PARAMETER"}
        fun string(key:String):String? {
            val direct=intent.extras?.get(key)
            val nested=if(args.has(key))args.get(key)else null
            require((direct==null||direct is String)&&(nested==null||nested is String)){"ACTION_INVALID_TYPE"}
            require(direct==null||nested==null||direct==nested){"ACTION_CONFLICT"}
            return (nested?:direct) as String?
        }
        return when(fn){
            QUERY->Request(fn!!,topic=topic(string("topic")))
            DRAFT->Request(fn!!,situation=draft(string("situation")))
            else->{val mode=string("request_mode")?:"existing";require(mode in setOf("existing","new")){"ACTION_INVALID_MODE"};Request(fn!!,recordQuery=draft(string("record_query")),fresh=mode=="new")}
        }
    }
    fun topic(value:String?):String{require(value in topics){"ACTION_INVALID_TOPIC"};return value!!}
    fun draft(value:String?):String{val s=value?.trim().orEmpty();require(s.length<=800){"ACTION_INPUT_TOO_LONG"};return s}
    fun query(topic:String,service:CaritdaService,observations:ObservationStore):String{
        topic(topic)
        return when(topic){
            "recent_issues"->service.issues().take(3).joinToString("\n"){it.type.label+" · "+it.situation.take(160)+" · "+it.status.label}.ifBlank{"저장된 문제 기록이 없어요."}
            "handover"->{if(!service.confirmed())"기록할 사람을 선택한 뒤 인계 내용을 확인해 주세요." else service.issues().filter{service.needsAcknowledgement(it)}.take(3).joinToString("\n"){it.type.label+" · "+it.situation.take(160)}.ifBlank{"아직 읽지 않은 전달 기록이 없어요."}}
            "vehicle","battery"->vehicleReply(topic,observations).text
            else->"실제 타이어 압력과 TPMS 고장은 이 공개 SDK에서 확인하지 못했어요. 저압 기준값은 실제 압력이 아니에요."
        }
    }
    fun queryReply(topic:String,service:CaritdaService,observations:ObservationStore):Reply {
        topic(topic)
        if(topic in setOf("vehicle","battery"))return vehicleReply(topic,observations)
        if(topic=="handover"&&!service.confirmed())return Reply("asking","Please confirm the recording person in CARITDA while parked.",query(topic,service,observations))
        if(topic=="tires")return Reply("success","Measured tire pressure and tire faults are unavailable from this integration.",query(topic,service,observations),JSONArray().put(JSONObject().put("availability","UNVERIFIED").put("physical_fault_diagnosis",false)))
        val issues=service.issues().filter{topic!="handover"||service.needsAcknowledgement(it)}
        val data=JSONArray().put(JSONObject().put("record_count",issues.size).put("physical_fault_count",JSONObject.NULL))
        issues.take(3).forEach{data.put(JSONObject().put("category",it.type.label).put("reported_symptom",it.situation.take(160)).put("inspection_status",it.status.label))}
        return Reply("success","The requested driver records were read without marking them as acknowledged.",query(topic,service,observations),data)
    }
    private fun vehicleReply(topic:String,observations:ObservationStore,now:Long=System.currentTimeMillis()):Reply {
        val data=JSONArray();val text=mutableListOf<String>()
        val kinds=if(topic=="battery")listOf("EV_SOC" to "구동 배터리 충전량")else listOf("EV_SOC" to "구동 배터리 충전량","ODOMETER" to "누적 주행거리")
        for((kind,label) in kinds){
            val last=observations.mostRecent(kind,true);val attempt=observations.mostRecent(kind)
            val age=last?.receivedAt?.let{now-it};val fresh=age!=null&&age in 0..600000&&attempt?.status==ObservationStatus.RECEIVED
            val state=when{last==null->attempt?.status?.name?:"NOT_RECEIVED";!fresh->"PREVIOUS_OR_STALE";else->"LATEST_RECEIPT"}
            val row=JSONObject().put("signal",kind).put("availability",state).put("source",last?.source?.name?:"NONE")
            if(last!=null){row.put("value",last.raw).put("unit",last.unit).put("app_received_at",last.receivedAt).put("vehicle_measured_at",JSONObject.NULL)}
            data.put(row)
            text.add(label+": "+if(last==null)"확인하지 못했어요."else "${last.raw} ${last.unit} · ${if(fresh)"최근 앱 수신값"else"이전 수신값 · 현재 상태 미확인"}\n앱 수신 ${com.nextdrive.pleos.ui.VehicleUi.shortTime(last.receivedAt!!)}")
        }
        return Reply("success","Only received vehicle values are available; missing or old values do not establish current vehicle health.",text.joinToString("\n"),data)
    }
    fun callback(function:String,status:String,message:String,data:JSONArray=JSONArray(),mode:String="silent"):JSONObject{
        require(function in functions&&status in setOf("success","asking","fail")&&message.length in 5..600&&Regex("[A-Za-z]").containsMatchIn(message))
        require(mode in setOf("voice","silent","beep")&&data.length()<=6&&(0 until data.length()).all{data.optJSONObject(it)!=null}&&data.toString().length<=6000)
        return JSONObject().put("functionName",function).put("status",status).put("message",message).put("mode",mode).put("data",data.toString())
    }
}
