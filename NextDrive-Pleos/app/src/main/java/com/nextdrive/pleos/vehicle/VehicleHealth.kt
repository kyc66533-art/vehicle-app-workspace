package com.nextdrive.pleos.vehicle

/** Public read-only signals. Lamp state and tire warning threshold are not physical fault tests. */
object VehicleHealth {
    val labels=linkedMapOf(
        "DOOR_FL" to "앞쪽 왼쪽 문", "DOOR_FR" to "앞쪽 오른쪽 문", "DOOR_RL" to "뒤쪽 왼쪽 문", "DOOR_RR" to "뒤쪽 오른쪽 문",
        "LOW_BEAM" to "하향등 상태", "HIGH_BEAM" to "상향등 상태", "TAIL_LAMP" to "미등 상태", "TURN_SIGNAL" to "방향지시 상태",
        "ENGINE_OIL" to "엔진오일 수위", "BRAKE_FLUID" to "브레이크액 수위", "WASHER_LOW" to "워셔액 부족 안내", "POWER_DOWN" to "출력 저하 안내",
        "BRAKE_PEDAL" to "브레이크 페달", "TIRE_LIMIT_FL" to "앞 왼쪽 저압 기준", "TIRE_LIMIT_FR" to "앞 오른쪽 저압 기준", "TIRE_LIMIT_RL" to "뒤 왼쪽 저압 기준", "TIRE_LIMIT_RR" to "뒤 오른쪽 저압 기준")
    fun valid(kind:String,v:Any?):Boolean=when {
        kind.startsWith("DOOR_")->v is Int&&v>=0
        kind.startsWith("TIRE_LIMIT_")->v is Number&&v.toDouble().isFinite()&&v.toDouble()>0
        kind in setOf("LOW_BEAM","HIGH_BEAM","TAIL_LAMP")->v?.toString() in setOf("ON","OFF","DAYTIME_RUNNING")
        kind=="TURN_SIGNAL"->v?.toString() in setOf("OFF","LEFT","RIGHT")
        kind in setOf("ENGINE_OIL","BRAKE_FLUID")->v?.toString() in setOf("NORMAL","LOW","HIGH","CRITICALLY_LOW")
        else->v is Boolean
    }
    fun value(kind:String,raw:String)=when {
        kind.startsWith("DOOR_")->"문 위치값 $raw · 차량 기준"
        kind.startsWith("TIRE_LIMIT_")->"$raw kPa · 실제 압력 아님"
        kind in setOf("WASHER_LOW","POWER_DOWN")->if(raw=="true")"차량에서 안내가 확인됐어요"else"현재 해당 안내가 없어요"
        kind=="BRAKE_PEDAL"->if(raw=="true")"밟고 있어요"else"밟지 않고 있어요"
        else->when(raw){"ON"->"켜짐";"OFF"->"꺼짐";"DAYTIME_RUNNING"->"주간등 상태";"LEFT"->"왼쪽";"RIGHT"->"오른쪽";"NORMAL"->"차량이 수위 정상으로 보고했어요";"LOW"->"차량이 수위 낮음으로 보고했어요";"HIGH"->"차량이 수위 높음으로 보고했어요";"CRITICALLY_LOW"->"차량이 수위 매우 낮음으로 보고했어요";else->"확인할 수 없음"}
    }
    fun text(kind:String,attempt:VehicleObservation?,last:VehicleObservation?,now:Long=System.currentTimeMillis()):String {
        val status=when(attempt?.status){null->"아직 확인하지 않았어요";ObservationStatus.UNSUPPORTED->"이 환경에서 지원되지 않아요";ObservationStatus.PERMISSION_DENIED->"읽기 권한이 없어요";ObservationStatus.SDK_UNAVAILABLE->"차량 서비스가 준비되지 않았어요";ObservationStatus.TIMEOUT->"응답 시간이 지났어요";ObservationStatus.INVALID->"유효한 값을 받지 못했어요";ObservationStatus.CONNECTION_FAILED->"연결하지 못했어요";else->""}
        if(last?.receivedAt==null||!last.valid||last.raw==null)return status.ifEmpty{"값을 받지 못했어요"}
        val old=now-last.receivedAt !in 0 until 600000
        return value(kind,last.raw)+(if(attempt?.status!=ObservationStatus.RECEIVED)" · 이전 수신값 ($status)"else if(old)" · 오래된 값"else"")
    }
}
