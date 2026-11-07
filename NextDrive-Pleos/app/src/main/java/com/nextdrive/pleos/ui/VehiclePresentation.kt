package com.nextdrive.pleos.ui

import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.domain.ConsultationSummary
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

data class VehicleCard(val value:String,val time:String,val source:String,val attempt:String,val stale:Boolean)

object VehiclePresentation {
    const val STALE_AFTER_MS=600_000L
    fun card(kind:String,attempt:VehicleObservation?,valid:VehicleObservation?,now:Long):VehicleCard {
        val number=valid?.raw?.toDoubleOrNull()
        val accepted=valid?.takeIf {
            it.status==ObservationStatus.RECEIVED && it.valid && it.supported==true &&
                it.source in setOf(ObservationSource.PLEOS_EMULATOR_SDK,ObservationSource.PLEOS_VEHICLE_SDK) &&
                it.receivedAt!=null && number!=null && number.isFinite() && number>=0 &&
                if(kind=="ODOMETER")it.unit=="km" else it.unit=="%" && number<=100
        }
        val at=accepted?.receivedAt
        val future=at!=null && at>now+60_000
        val stale=at!=null && (future || now-at>=STALE_AFTER_MS)
        val failed=attempt!=null && attempt.status!=ObservationStatus.RECEIVED
        val status=when(attempt?.status) {
            ObservationStatus.UNSUPPORTED->"이 환경에서는 이 정보를 확인할 수 없어요."
            ObservationStatus.PERMISSION_DENIED->"이 항목을 불러올 권한이 없어요."
            ObservationStatus.CONNECTION_FAILED,ObservationStatus.TIMEOUT->"최근에는 응답을 받지 못했어요. 잠시 뒤 다시 불러와 주세요."
            ObservationStatus.INVALID->"최근에는 사용할 수 있는 값을 받지 못했어요."
            ObservationStatus.SDK_UNAVAILABLE->"아직 차량에 연결하지 못했어요."
            else->""
        }
        val value=if(accepted==null)"아직 불러온 값이 없어요." else
            (if(failed||stale)"이전 값 · " else "")+"${displayNumber(accepted.raw!!)} ${accepted.unit}"
        val time=if(at==null)"마지막 수신: 없음" else if(future)"수신 시각을 확인해 주세요 · ${ConsultationSummary.time(at)}" else
            "마지막 수신 · ${ConsultationSummary.time(at)}"+(if(stale)"\n오래된 값이에요. 지금 상태가 아닐 수 있어요." else "")
        val source=when(accepted?.source ?: attempt?.source) {
            ObservationSource.PLEOS_EMULATOR_SDK->"에뮬레이터에서 받은 값이에요. 실제 차량 측정값은 아니에요."
            ObservationSource.PLEOS_VEHICLE_SDK->"차량에서 불러와 저장한 값이에요."
            else->"차량에서 받은 값은 아직 없어요."
        }
        return VehicleCard(value,time,source,status,stale)
    }
    private fun displayNumber(raw:String):String {
        val decimal=BigDecimal(raw).stripTrailingZeros()
        return NumberFormat.getNumberInstance(Locale.KOREA).apply {
            isGroupingUsed=true
            maximumFractionDigits=decimal.scale().coerceAtLeast(0)
        }.format(decimal)
    }
}
