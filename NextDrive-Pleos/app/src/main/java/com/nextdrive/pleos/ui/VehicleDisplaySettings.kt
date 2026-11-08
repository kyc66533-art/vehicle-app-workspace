package com.nextdrive.pleos.ui

import android.content.SharedPreferences
import com.nextdrive.pleos.vehicle.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** User layout preference, never SDK-verified vehicle identity or a detector input. */
enum class PowertrainLayout(val label:String) {
    UNKNOWN("차종 미확인"), EV("전기차"), ICE("내연기관차"), HEV("하이브리드"),
    PHEV("플러그인 하이브리드"), OTHER("기타")
}

class VehicleDisplaySettings(private val prefs:SharedPreferences) {
    val layout:PowertrainLayout get()=runCatching{PowertrainLayout.valueOf(prefs.getString("layout","UNKNOWN")!!)}.getOrDefault(PowertrainLayout.UNKNOWN)
    val deviceTime:Boolean get()=prefs.getString("time_zone","seoul")=="device"
    val zone:ZoneId get()=if(deviceTime)ZoneId.systemDefault()else ZoneId.of("Asia/Seoul")
    val layoutDescription:String get()=if(layout==PowertrainLayout.UNKNOWN)"차종 미확인 · 지원되는 정보를 보여드려요."else "${layout.label} · 사용자가 정한 화면 배치예요."
    val timeDescription:String get()=if(deviceTime)"기기 시간 · ${zone.id}"else "한국 시간 · Asia/Seoul"
    fun setLayout(value:PowertrainLayout){check(prefs.edit().putString("layout",value.name).commit()){"차종 설정을 저장하지 못했어요. 다시 선택해 주세요."}}
    fun setDeviceTime(value:Boolean){check(prefs.edit().putString("time_zone",if(value)"device"else"seoul").commit()){"시간 표시 설정을 저장하지 못했어요. 다시 선택해 주세요."}}
    fun time(at:Long)=format(at,zone)
    companion object {
        fun format(at:Long,zone:ZoneId):String {
            val instant=Instant.ofEpochMilli(at)
            val date=DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss",Locale.ENGLISH).withZone(zone).format(instant)
            val name=if(zone.id=="Asia/Seoul")"KST"else zone.id
            val offset=zone.rules.getOffset(instant).id.let{if(it=="Z")"+00:00"else it}
            return "$date $name ($offset)"
        }
    }
}

/** Selects presentation only. A successful SOC or unsupported fuel never classifies powertrain. */
object VehicleInfoPolicy {
    fun kinds(layout:PowertrainLayout,attempt:(String)->VehicleObservation?,valid:(String)->VehicleObservation?):List<String> {
        fun available(k:String)=valid(k)?.let{it.kind==k&&SnapshotStore.accepted(it)}==true || attempt(k)?.supported==true
        val energy=when(layout){
            PowertrainLayout.EV->listOf("EV_SOC")
            PowertrainLayout.ICE->listOf("FUEL_PERCENT")
            PowertrainLayout.HEV,PowertrainLayout.PHEV->listOf("FUEL_PERCENT","EV_SOC")
            else->listOf("EV_SOC","FUEL_PERCENT").filter(::available)
        }
        return energy+"ODOMETER"
    }
    fun name(kind:String,layout:PowertrainLayout)=when(kind){
        "EV_SOC"->if(layout==PowertrainLayout.EV)"배터리 충전량"else"구동 배터리 충전량"
        "FUEL_PERCENT"->"연료 잔량"
        else->"누적 주행거리"
    }
}
