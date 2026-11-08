package com.nextdrive.pleos.ai

import com.nextdrive.pleos.vehicle.SocNotice
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** No VIN, driver identity, location, issue database or local observation IDs in the payload. */
data class ExplanationInput(val requestId:String,val kind:String,val raw:String,val unit:String,val receivedAt:Long,val source:String,val threshold:Int,val ruleVersion:String,val assessment:String){
    fun json()=JSONObject().put("request_id",requestId).put("signal",kind).put("value",raw).put("unit",unit).put("received_at",receivedAt).put("source",source)
        .put("criterion",JSONObject().put("operator","LT").put("threshold",threshold).put("version",ruleVersion)).put("assessment",assessment)
        .put("purpose","short_korean_information_only_no_diagnosis_no_safety_decision").toString()
    companion object {fun from(n:SocNotice)=ExplanationInput(java.util.UUID.randomUUID().toString(),"EV_SOC",n.value,n.unit,n.last,n.source,n.threshold,"user-soc-v1:${n.version}",if(n.phase=="LOW")"USER_LOW_SOC"else"CONDITION_EXIT")}
}
data class Explanation(val text:String,val model:String,val requestId:String,val origin:String,val latencyMs:Long)
object ExplanationContract {
    fun parse(body:String,input:ExplanationInput,latencyMs:Long,allowMock:Boolean=false):Explanation {
        require(body.length<=16384)
        val json=JSONObject(body);val origin=json.getString("origin");val text=json.getString("text");val model=json.getString("model")
        require(json.getString("request_id")==input.requestId&&model.isNotBlank()&&model.length<=100)
        require(origin=="MODEL"||allowMock&&origin=="MOCK"){"실제 AI 응답과 검증용 응답을 구분할 수 없어요."}
        require(json.getBoolean("informational_only")&&text.isNotBlank()&&text.length<=260&&Regex("[가-힣]").containsMatchIn(text))
        require(!Regex("고장(입니다|으로 확정)|수리.{0,8}완료|안전(합니다|이 보장)|운행.{0,8}(가능합니다|안전)").containsMatchIn(text)){"확정적인 진단·안전 문구는 표시하지 않아요."}
        return Explanation(text,model,input.requestId,origin,latencyMs)
    }
}
/** Call only after approval of endpoint, cost and selected minimal data. No provider key in APK. */
class AiRelayClient(private val approvedEndpoint:URL,private val transientAppToken:()->String?) {
    fun explain(input:ExplanationInput):Explanation {
        require(approvedEndpoint.protocol=="https"&&approvedEndpoint.userInfo==null&&approvedEndpoint.query==null)
        val token=transientAppToken()?.takeIf{it.isNotBlank()}?:error("승인된 임시 앱 인증이 필요해요.")
        val start=android.os.SystemClock.elapsedRealtime()
        val c=approvedEndpoint.openConnection() as HttpsURLConnection
        try{
            c.connectTimeout=5000;c.readTimeout=10000;c.instanceFollowRedirects=false;c.requestMethod="POST";c.doOutput=true
            c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json; charset=utf-8")
            c.outputStream.use{it.write(input.json().toByteArray(Charsets.UTF_8))}
            check(c.responseCode==200){"AI 중계 응답 오류: HTTP ${c.responseCode}"}
            val body=c.inputStream.use{stream->
                val out=java.io.ByteArrayOutputStream();val chunk=ByteArray(2048)
                while(true){val n=stream.read(chunk);if(n<0)break;require(out.size()+n<=16384);out.write(chunk,0,n)}
                String(out.toByteArray(),Charsets.UTF_8)
            }
            return ExplanationContract.parse(body,input,android.os.SystemClock.elapsedRealtime()-start)
        }finally{c.disconnect()}
    }
}
