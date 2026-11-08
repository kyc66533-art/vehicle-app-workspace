package com.nextdrive.pleos.ui

import com.nextdrive.pleos.domain.ConsultationSummary
import com.nextdrive.pleos.vehicle.*

/** Read-only presentation: never fetches sensors, changes evidence, or diagnoses a fault. */
object VehicleUi {
    fun values(s:Snapshot?,kind:String):Pair<VehicleObservation?,VehicleObservation?> {
        val evidence=s?.evidence.orEmpty().filter{it.value.kind==kind}
        return evidence.firstOrNull()?.value to evidence.lastOrNull{SnapshotStore.accepted(it.value)}?.value
    }
    fun name(kind:String)=VehicleInfoPolicy.name(kind,PowertrainLayout.UNKNOWN)
    fun shortTime(at:Long?)=at?.let{ConsultationSummary.time(it)}?:"수신한 기록 없음"
    fun source(s:Snapshot?):String=when(s?.evidence?.firstOrNull()?.value?.source){
        ObservationSource.PLEOS_EMULATOR_SDK->"에뮬레이터에서 받은 값이에요."
        ObservationSource.PLEOS_VEHICLE_SDK->"차량 SDK에서 받은 값이에요."
        else->"차량 정보 수신 전이에요."
    }
    fun compact(s:Snapshot):String=VehicleInfoPolicy.kinds(PowertrainLayout.UNKNOWN,{values(s,it).first},{values(s,it).second}).joinToString(" · "){kind->
        val(attempt,valid)=values(s,kind)
        val v=VehiclePresentation.card(kind,attempt,valid,System.currentTimeMillis())
        "${when(kind){"EV_SOC"->"구동 배터리 충전량";"FUEL_PERCENT"->"연료";else->"거리"}} ${if(valid==null)"값 없음"else v.value}"
    }
    fun technical(s:Snapshot):String=buildString{
        appendLine("Vehicle SDK 2.3.2\n기록 ID: ${s.id}\n카드 ID: ${s.cardId}")
        appendLine("차량 측 측정시각: 제공되지 않음 · 실제 차량 식별: 미확인")
        s.evidence.forEach{r->val v=r.value
            appendLine("\n${v.kind} · ${v.status}\n관측 데이터 ID: ${r.storeId}:${r.rowId}\n원본: ${v.raw?:"미수신"} · 단위: ${v.unit?:"없음"}\n지원: ${v.supported} · 유효: ${v.valid}\n앱 수신시각: ${v.receivedAt?:"없음"}\n기록시각: ${v.recordedAt}\n출처: ${v.source}\n세션 ID: ${v.session}\n${v.detail}")
        }
    }
}
