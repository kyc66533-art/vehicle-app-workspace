package com.nextdrive.pleos.vehicle

import com.nextdrive.pleos.model.SymptomType

enum class ReadingQuality { CURRENT, STALE, UNSUPPORTED, PERMISSION_DENIED, FAILED, INVALID, NOT_RECEIVED }
data class DriveCheckReading(val kind:String,val title:String,val quality:ReadingQuality,val value:String,
    val unit:String?,val receivedAt:Long?,val source:ObservationSource?,val supported:Boolean?,val attention:Boolean,val reason:String,val attempt:ObservationStatus?){
    val qualityText get()=when(quality){
        ReadingQuality.CURRENT->"실제 수신값"
        ReadingQuality.STALE->"오래되었거나 시각 확인이 필요한 수신값"
        ReadingQuality.UNSUPPORTED->"이 환경에서 미지원"
        ReadingQuality.PERMISSION_DENIED->"읽기 권한 부족"
        ReadingQuality.FAILED->if(attempt==ObservationStatus.TIMEOUT)"수신 시간 초과"else"최근 수신 실패"
        ReadingQuality.INVALID->"유효한 최신 값을 확인하지 못했어요"
        ReadingQuality.NOT_RECEIVED->"아직 수신하지 않았어요"
    }
    fun text(time:(Long)->String)=buildString{
        appendLine(qualityText)
        appendLine((if(quality!=ReadingQuality.CURRENT&&receivedAt!=null)"이전 값 · "else"")+value)
        appendLine("단위 · ${unit?:"제공 없음"} · 지원 · ${when(supported){true->"확인됨";false->"미지원";null->"미확인"}}")
        appendLine(receivedAt?.let{"앱 수신 · "+time(it)}?:"앱 수신 · 없음")
        appendLine("출처 · "+when(source){ObservationSource.PLEOS_EMULATOR_SDK->"Pleos 에뮬레이터 SDK";ObservationSource.PLEOS_VEHICLE_SDK->"차량 SDK";else->"실제 SDK 수신 확인 전"})
        if(reason.isNotBlank())append(reason)
    }
}

/** Local information checks only. No risk score, diagnosis, issue writer or AI payload builder. */
object DriveCheck {
    fun accepted(v:VehicleObservation?):Boolean {
        if(v==null||v.source !in setOf(ObservationSource.PLEOS_EMULATOR_SDK,ObservationSource.PLEOS_VEHICLE_SDK)||v.status!=ObservationStatus.RECEIVED||!v.valid||v.supported!=true||v.receivedAt==null||v.receivedAt<=0||v.raw==null)return false
        return when {
            v.kind in setOf("EV_SOC","FUEL_PERCENT","ODOMETER")->SnapshotStore.accepted(v)
            v.kind.startsWith("DOOR_")->v.unit=="position"&&v.raw.toIntOrNull()?.let{it>=0}==true
            v.kind.startsWith("TIRE_LIMIT_")->v.unit=="kPa"&&v.raw.toDoubleOrNull()?.let{it.isFinite()&&it>0}==true
            v.kind in setOf("LOW_BEAM","HIGH_BEAM","TAIL_LAMP")->v.unit=="state"&&v.raw in setOf("ON","OFF","DAYTIME_RUNNING")
            v.kind=="TURN_SIGNAL"->v.unit=="state"&&v.raw in setOf("OFF","LEFT","RIGHT")
            v.kind in setOf("ENGINE_OIL","BRAKE_FLUID")->v.unit=="state"&&v.raw in setOf("NORMAL","LOW","HIGH","CRITICALLY_LOW")
            v.kind in setOf("WASHER_LOW","POWER_DOWN","BRAKE_PEDAL")->v.unit=="state"&&v.raw in setOf("true","false")
            else->false
        }
    }
    fun assess(kind:String,attempt:VehicleObservation?,last:VehicleObservation?,policy:SocPolicy,now:Long=System.currentTimeMillis()):DriveCheckReading {
        val current=attempt?.takeIf{it.kind==kind&&accepted(it)}
        val sample=current?:last?.takeIf{it.kind==kind&&accepted(it)}
        val quality=when(attempt?.status){
            null->ReadingQuality.NOT_RECEIVED
            ObservationStatus.UNSUPPORTED->ReadingQuality.UNSUPPORTED
            ObservationStatus.PERMISSION_DENIED->ReadingQuality.PERMISSION_DENIED
            ObservationStatus.INVALID->ReadingQuality.INVALID
            ObservationStatus.RECEIVED->if(current==null)ReadingQuality.INVALID else if(now-current.receivedAt!! !in 0 until 600000)ReadingQuality.STALE else ReadingQuality.CURRENT
            else->ReadingQuality.FAILED
        }
        val attention=quality==ReadingQuality.CURRENT&&when(kind){
            "EV_SOC"->policy.enabled&&current!!.raw!!.toDouble()<policy.threshold
            "ENGINE_OIL","BRAKE_FLUID"->current!!.raw in setOf("LOW","HIGH","CRITICALLY_LOW")
            "WASHER_LOW","POWER_DOWN"->current!!.raw=="true"
            else->false
        }
        val reason=when{
            attention&&kind=="EV_SOC"->"사용자 기준 ${policy.threshold}% 미만이에요. 배터리 고장 판정은 아니에요."
            attention->"차량이 이 항목의 안내를 보고했어요. 차량 표시와 함께 확인해 주세요."
            kind.startsWith("TIRE_LIMIT_")->"저압 안내 기준값이며 현재 타이어 압력이 아니에요."
            kind in setOf("LOW_BEAM","HIGH_BEAM","TAIL_LAMP")->"등화의 켜짐 상태예요. 전구·LED 고장 여부를 판정하지 않아요."
            quality!=ReadingQuality.CURRENT->"이 상태를 차량이 정상이라는 뜻으로 사용하지 않아요."
            else->"조회 시점의 정보예요. 차량 전체의 정상 여부를 판정하지 않아요."
        }
        val title=when(kind){"EV_SOC"->"구동 배터리 충전량";"ODOMETER"->"누적 주행거리";"FUEL_PERCENT"->"연료 잔량";else->VehicleHealth.labels[kind]?:kind}
        val value=sample?.let{if(kind in setOf("EV_SOC","ODOMETER","FUEL_PERCENT"))"${it.raw} ${it.unit}"else VehicleHealth.value(kind,it.raw!!)}?:"수신한 값 없음"
        return DriveCheckReading(kind,title,quality,value,sample?.unit,sample?.receivedAt,sample?.source?:attempt?.source,attempt?.supported,attention,reason,attempt?.status)
    }
    fun draftType(kind:String)=if(kind=="EV_SOC")SymptomType.OTHER else SymptomType.WARNING
    /** Only a generic editable prompt. SDK numbers, clocks, identity and coordinates stay out
     * of the symptom field and therefore out of the existing AI category/situation contract. */
    fun draftText(kind:String)=when(kind){
        "EV_SOC"->"충전량 안내를 보고 차량 상태를 확인하고 싶어요."
        "WASHER_LOW"->"워셔액 안내가 보여 직접 확인할 내용을 남겨요."
        "ENGINE_OIL"->"엔진오일 안내가 보여 직접 확인할 내용을 남겨요."
        "BRAKE_FLUID"->"브레이크액 안내가 보여 직접 확인할 내용을 남겨요."
        "POWER_DOWN"->"출력 저하 안내가 보여 직접 확인할 내용을 남겨요."
        else->"차량 안내를 보고 직접 확인할 내용을 남겨요."
    }
}

object SocGuide {
    fun text(policy:SocPolicy,notices:List<SocNotice>,latest:VehicleObservation?,now:Long=System.currentTimeMillis()):String {
        if(!policy.enabled)return "자동 안내가 꺼져 있어요. 이전 사건은 아래에 보존돼요."
        val sample=latest
        if(sample==null||!DriveCheck.accepted(sample)||sample.kind!="EV_SOC"||now-sample.receivedAt!! !in 0 until 600000)return "새로 받은 유효한 값이 없어 현재 조건을 판단하지 않아요. 이전 안내가 해제되거나 차량이 정상이라는 뜻은 아니에요."
        val notice=notices.firstOrNull{it.source==sample.source.name}
        val number=sample.raw!!.toDouble()
        return when{
            notice?.phase=="LOW"&&number<policy.threshold->"최근 수신 충전량이 사용자 기준보다 낮아요. 같은 상태의 반복 수신은 하나의 안내로 유지해요."
            notice?.phase=="LOW"->"낮은 충전량 안내 기록이 유지 중이에요. 해제 기준은 ${policy.threshold+SocRule.CLEAR_MARGIN}% 이상이에요."
            number<policy.threshold->"최근 수신값은 사용자 기준 미만이에요. 설정 후 새 수신의 안내 이력을 확인해 주세요."
            notice?.phase=="EXITED"->"사용자 기준의 낮은 충전량 안내가 해제된 기록이 있어요. 고장 수리나 운행 안전을 판정한 결과는 아니에요."
            else->"최근 수신값은 설정한 낮은 충전량 기준 이상이에요. 차량 전체의 정상 여부는 판단하지 않아요."
        }
    }
}
