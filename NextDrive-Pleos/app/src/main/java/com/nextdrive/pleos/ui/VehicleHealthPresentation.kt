package com.nextdrive.pleos.ui

import com.nextdrive.pleos.vehicle.*

/** Groups public signals without converting door position/pressure thresholds into faults. */
object VehicleHealthPresentation {
    val groups=linkedMapOf("문" to listOf("DOOR_FL","DOOR_FR","DOOR_RL","DOOR_RR"),
        "등화·방향지시" to listOf("LOW_BEAM","HIGH_BEAM","TAIL_LAMP","TURN_SIGNAL"),
        "차량 안내·페달" to listOf("ENGINE_OIL","BRAKE_FLUID","WASHER_LOW","POWER_DOWN","BRAKE_PEDAL"),
        "타이어 안내 기준" to listOf("TIRE_LIMIT_FL","TIRE_LIMIT_FR","TIRE_LIMIT_RL","TIRE_LIMIT_RR"))
    fun quality(q:ReadingQuality)=when(q){ReadingQuality.CURRENT->"수신됨";ReadingQuality.STALE->"오래된 정보";ReadingQuality.UNSUPPORTED->"미지원";ReadingQuality.PERMISSION_DENIED->"권한 필요";ReadingQuality.FAILED->"수신 실패";ReadingQuality.INVALID->"확인하지 못함";ReadingQuality.NOT_RECEIVED->"미수신"}
    fun compact(v:DriveCheckReading):String {
        val value=when{
            v.receivedAt==null->""
            v.kind.startsWith("DOOR_")->"위치 정보 수신"
            v.kind.startsWith("TIRE_LIMIT_")->"안내 기준 수신"
            else->v.value
        }
        return v.title+" · "+if(v.quality==ReadingQuality.CURRENT)value else quality(v.quality)+(if(value.isNotBlank())"\n이전 확인값 · $value"else"")
    }
}
