package com.nextdrive.pleos.integration

object LocationPresentation {
    fun freshness(value:IssueLocation,now:Long=System.currentTimeMillis()):String=when{
        !value.valid()->"유효하지 않은 위치예요."
        value.measuredAt>now||value.receivedAt>now+60000->"위치 시각을 확인해 주세요."
        now-value.measuredAt>=600000->"오래된 위치예요. 현재 위치와 다를 수 있어요."
        else->"최근에 측정된 마지막 확인 위치예요."
    }
    fun text(value:IssueLocation,time:(Long)->String,now:Long=System.currentTimeMillis())=
        "${freshness(value,now)}\n위도 ${value.latitude} · 경도 ${value.longitude}\n측정 · ${time(value.measuredAt)}\n앱 수신 · ${time(value.receivedAt)}\n출처 · ${if(value.source=="PLEOS_FUSED_LAST_KNOWN")"Pleos FusedLocation 마지막 확인값"else value.source}\n증상 발생 당시 위치로 확정하지 않아요. 정확도(m)는 제공되지 않아요. AI에는 보내지 않아요."
    fun failure(code:String)=when(code){
        "LOCATION_PERMISSION_DENIED"->"위치 권한이 허용되지 않았어요. 기존 연결은 남아 있어요."
        "LOCATION_SERVICE_UNAVAILABLE","LOCATION_SDK_UNAVAILABLE"->"이 환경에서는 위치 서비스를 사용할 수 없어요."
        "LOCATION_NO_VALID_VALUE"->"유효한 마지막 위치를 받지 못했어요."
        "LOCATION_TIMEOUT"->"위치 응답 시간이 지났어요. 기존 연결은 남아 있어요."
        "LOCATION_CANCELLED"->"이번 위치 확인을 취소했어요. 기존 연결은 남아 있어요."
        else->"위치를 확인하지 못했어요. 기존 연결은 남아 있어요."
    }
}
