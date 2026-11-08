package com.nextdrive.pleos.analytics

import com.nextdrive.pleos.vehicle.*
import java.security.MessageDigest
import kotlin.math.abs

data class AnalysisPoint(val ref:ObservationRef,val at:Long,val number:Double,val segment:Int)
data class Insight(val id:String,val kind:String,val first:Long,val last:Long,val title:String,val meaning:String,val action:String,val rule:String,val evidence:List<ObservationRef>,val delta:Double?=null)
data class VehicleAnalysis(val kind:String,val since:Long,val until:Long,val points:List<AnalysisPoint>,val quality:Map<String,Int>,val insights:List<Insight>,val events:List<SocChange>,val truncated:Boolean,val lastAttempt:ObservationRef?=null){
    val values get()=points.map{it.number}
    val latest get()=points.lastOrNull()
    val delta:Double? get()=points.takeLast(2).takeIf{it.size==2&&it[0].segment==it[1].segment}?.let{it[1].number-it[0].number}
    val stale get()=latest?.let{until-it.at>=600000}?:true
    val unit get()=if(kind=="EV_SOC")"%"else"km"
    val textSummary:String get()=if(points.isEmpty())"분석할 수 있는 수신값이 없어요."else
        "유효 관측 ${points.size}개 · 처음 ${fmt(values.first())}$unit · 최근 ${fmt(values.last())}$unit\n최소 ${fmt(values.min())}$unit · 최대 ${fmt(values.max())}$unit · 관측값 평균 ${fmt(values.average())}$unit"
    companion object {fun fmt(n:Double)=java.math.BigDecimal.valueOf(n).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}
}

/** Read-only analysis of app receipts. Sessions are not trips and SOC deltas are not diagnosis. */
object VehicleAnalytics {
    const val RULE="receipt-analysis-v1"
    const val GAP_MS=600000L
    const val SOC_CHANGE_CANDIDATE=20.0 // Exploratory display rule, not an OEM warning threshold.
    fun id(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    fun analyze(kind:String,refs:List<ObservationRef>,since:Long,now:Long,changes:List<SocChange> = emptyList(),gaps:List<Long> = emptyList(),truncated:Boolean=false):VehicleAnalysis {
        require(kind in setOf("EV_SOC","ODOMETER"))
        val q=linkedMapOf<String,Int>()
        fun count(k:String){q[k]=(q[k]?:0)+1}
        val environment=refs.lastOrNull{it.value.source in setOf(ObservationSource.PLEOS_EMULATOR_SDK,ObservationSource.PLEOS_VEHICLE_SDK)}?.value?.source
        val points=mutableListOf<AnalysisPoint>();val insights=linkedMapOf<String,Insight>();var segment=0
        var previous:AnalysisPoint?=null;var changed:AnalysisPoint?=null;val run=mutableListOf<AnalysisPoint>()
        fun emit(k:String,a:AnalysisPoint,b:AnalysisPoint,title:String,meaning:String,action:String,d:Double?=null){
            val key=id("$RULE:$kind:$k:${a.ref.storeId}:${a.ref.rowId}:"+if(k=="TREND")if((d?:0.0)>0)"UP"else"DOWN"else"${b.ref.storeId}:${b.ref.rowId}")
            insights[key]=Insight(key,k,a.at,b.at,title,meaning,action,RULE,listOf(a.ref,b.ref).distinctBy{it.storeId to it.rowId},d)
        }
        refs.forEach{ref->
            val v=ref.value
            if(v.kind!=kind||v.recordedAt<since)return@forEach
            if(v.source !in setOf(ObservationSource.PLEOS_EMULATOR_SDK,ObservationSource.PLEOS_VEHICLE_SDK)){count("합성·출처 미확인 제외");previous=null;changed=null;run.clear();segment++;return@forEach}
            if(v.source!=environment){count("다른 데이터 환경 제외");previous=null;changed=null;run.clear();segment++;return@forEach}
            if(v.status!=ObservationStatus.RECEIVED){count(when(v.status){ObservationStatus.UNSUPPORTED->"미지원";ObservationStatus.PERMISSION_DENIED->"권한 오류";ObservationStatus.CONNECTION_FAILED,ObservationStatus.TIMEOUT->"연결·시간 초과";else->"결측·수신 오류"});previous=null;changed=null;run.clear();segment++;return@forEach}
            val n=v.raw?.toDoubleOrNull();val at=v.receivedAt
            if(n==null||!n.isFinite()||n<0||kind=="EV_SOC"&&n>100||v.unit!=(if(kind=="EV_SOC")"%"else"km")||!v.valid||v.supported!=true){count("유효 범위·단위 오류");previous=null;changed=null;run.clear();segment++;return@forEach}
            if(at==null||at>now+60000||at>v.recordedAt+60000){count("시각 오류");previous=null;changed=null;run.clear();segment++;return@forEach}
            if(at<since){count("기간 이전 수신 제외");previous=null;changed=null;run.clear();segment++;return@forEach}
            val old=previous
            if(old!=null&&at<old.at){count("시간 역순");previous=null;changed=null;run.clear();segment++;return@forEach}
            val discontinuity=old!=null&&(old.ref.storeId!=ref.storeId||old.ref.value.source!=v.source||old.ref.value.session!=v.session||at-old.at>GAP_MS||gaps.any{it>old.at&&it<=at})
            if(discontinuity){count("비교 불가·관측 공백");segment++;changed=null;run.clear()}
            val p=AnalysisPoint(ref,at,n,segment);points.add(p)
            if(now-at>=GAP_MS)count("오래된 수신 이력")
            if(discontinuity&&old!=null)emit("GAP",old,p,"비교하기 어려운 구간이 있어요.","세션·출처가 다르거나 관측이 중단됐어요. 선을 이어 변화로 해석하지 않아요.","수신 시각과 출처를 확인해 주세요.")
            val prev=if(!discontinuity)old else null
            if(prev!=null){
                val d=n-prev.number
                if(d==0.0)count("같은 값 반복 수신") else {
                    val base=changed?:prev
                    emit("CHANGE",base,p,"이전 수신값보다 ${if(d>0)"높아졌어요."else"낮아졌어요."}","${VehicleAnalysis.fmt(prev.number)} → ${VehicleAnalysis.fmt(n)} ${if(kind=="EV_SOC")"%"else"km"} · 변화 ${VehicleAnalysis.fmt(d)}${if(kind=="EV_SOC")"%p"else"km"}","최근 값과 실제 차량 표시를 함께 확인해 주세요.",d)
                    if(kind=="EV_SOC"&&abs(d)>=SOC_CHANGE_CANDIDATE)emit("LARGE_CHANGE",base,p,"큰 수치 변화가 관측됐어요.","${VehicleAnalysis.fmt(d)}%p 차이예요. 20%p 이상이라는 탐색 기준이며 고장 기준이 아니에요.","에뮬레이터 조작·수신 공백·실제 상태 변화 여부를 확인해 주세요.",d)
                    if(run.size>=2&&(run.last().number-run[run.lastIndex-1].number)*d<0)run.clear()
                    if(run.isEmpty())run.add(prev);run.add(p)
                    if(run.size>=3)emit("TREND",run.first(),p,"연속 수신값이 ${if(d>0)"올라갔어요."else"내려갔어요."}","서로 비교 가능한 세 값 이상이 같은 방향이에요. 운행 효율이나 고장 추정은 아니에요.","관측 기간과 출처를 확인해 주세요.",p.number-run.first().number)
                    changed=p
                }
            }
            previous=p
        }
        val evidence=points.associateBy{it.ref.storeId to it.ref.rowId}
        val events=if(kind=="EV_SOC")changes.filter{it.at>=since&&it.at<=now&&evidence.containsKey(it.store to it.row)}else emptyList()
        events.forEach{e->val p=evidence.getValue(e.store to e.row);val k="${e.episode}:${e.kind}";val key=id("$RULE:$k")
            insights[key]=Insight(key,e.kind,p.at,p.at,if(e.kind=="USER_LOW_SOC")"충전량이 설정한 기준보다 낮아졌어요."else"설정한 낮은 충전량 안내가 해제됐어요.","${e.raw}% · 당시 사용자 기준 ${e.threshold}% 미만",if(e.kind=="USER_LOW_SOC")"다음 운행 전에 충전량과 이동 거리를 확인해 주세요."else"충전량 조건이 해제된 기록이며 수리 완료는 아니에요.","${SocRule.VERSION}:${e.version}",listOf(p.ref))
        }
        if(points.size<2)count("비교 표본 부족")
        val failures=refs.takeLastWhile{it.value.source==environment&&it.value.status!=ObservationStatus.RECEIVED}
        if(failures.size>=3){val first=failures.first();val last=failures.last();val key=id("$RULE:ERROR:${first.storeId}:${first.rowId}")
            insights[key]=Insight(key,"RECEIPT_ERRORS",first.value.recordedAt,last.value.recordedAt,"최근 세 번의 수신에 오류가 있어요.","연결·권한·지원 상태를 확인해야 해요. 차량 고장 판정은 아니에요.","안전하게 정차한 뒤 차량 정보를 다시 불러와 주세요.",RULE,failures)}
        if(truncated)count("최근 2000행 범위 제한")
        return VehicleAnalysis(kind,since,now,points,q,insights.values.sortedByDescending{it.last},events,truncated,refs.lastOrNull{it.value.source==environment})
    }
}
