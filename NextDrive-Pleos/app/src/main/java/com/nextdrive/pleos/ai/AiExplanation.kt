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
data class Explanation(val text:String,val model:String,val requestId:String,val origin:String,val latencyMs:Long,val diagnostic:String="")
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
    @Volatile private var active:HttpsURLConnection?=null
    @Volatile private var cancelled=false
    fun cancel(){cancelled=true;active?.disconnect();active=null}
    fun explainAnalysis(payload:String,consent:AnalysisConsent,requestId:String,preflightOnly:Boolean=false):Explanation {
        check(consent.permits(payload)){"전송 동의가 필요해요."}
        require(approvedEndpoint.toString()=="https://caritda-ai-relay-dev.kyc66533.workers.dev/v1/synthetic-soc")
        val facts=JSONObject(payload)
        require(facts.optBoolean("synthetic")&&facts.optString("source")=="TEST_ONLY"&&facts.optString("fixture")=="SOC_80_60_18_V1")
        val token=transientAppToken()?.takeIf{Regex("[A-Za-z0-9_-]{43}").matches(it)}?:error("유효한 임시 앱 인증이 필요해요.")
        val start=android.os.SystemClock.elapsedRealtime()
        val body=JSONObject().put("request_id",requestId).put("analysis_payload",payload).toString().toByteArray(Charsets.UTF_8)
        val preflight=try{JSONObject(postAnalysis(URL("https://caritda-ai-relay-dev.kyc66533.workers.dev/v1/preflight"),token,body))}catch(e:RelayFailure){throw e}catch(_:Exception){throw RelayFailure(502,"APP_PREFLIGHT")}
        if(preflight.optString("contract")!="caritda-gemini-synthetic-v1"||!preflight.optBoolean("authenticated")||!preflight.optBoolean("synthetic_only")||preflight.optBoolean("provider_calls",true)||!preflight.optBoolean("binding_ok"))throw RelayFailure(502,"APP_PREFLIGHT")
        if(preflight.optInt("remaining_attempts",-1) !in 0..5)throw RelayFailure(502,"APP_PREFLIGHT")
        if(preflightOnly)return Explanation("앱 인증과 호출 제한 저장소를 확인했어요. 남은 모델 요청 ${preflight.getInt("remaining_attempts")}회 · 모델 활성화 ${preflight.getBoolean("enabled")} · 모델 호출 없음","MODEL_NOT_CALLED",requestId,"PREFLIGHT",android.os.SystemClock.elapsedRealtime()-start,RelayDiagnostics.read(preflight))
        if(!RelayDiagnostics.readyForModel(preflight))throw RelayFailure(502,"APP_RELAY_VERSION")
        if(!preflight.getBoolean("enabled"))throw RelayFailure(503,"POC_DISABLED")
        if(preflight.getInt("remaining_attempts") !in 1..5)throw RelayFailure(429,"TOTAL_LIMIT")
        check(consent.permits(payload)&&!cancelled){"동의가 취소돼 요청하지 않아요."}
        val response=postAnalysis(approvedEndpoint,token,body)
        check(consent.permits(payload)&&!cancelled){"동의가 취소돼 응답을 표시하지 않아요."}
        val json=try{JSONObject(response)}catch(_:Exception){throw RelayFailure(502,"APP_RESPONSE_JSON")}
        if(json.optString("contract")!="caritda-gemini-synthetic-v1"||json.optString("request_id")!=requestId||json.optString("model")!="gemini-3.5-flash-lite"||!json.optBoolean("informational_only"))throw RelayFailure(502,"APP_ENVELOPE")
        val text=AnalysisExplanation.validate(response,consent,payload)
        return Explanation(text,json.getString("model"),requestId,"MODEL",android.os.SystemClock.elapsedRealtime()-start,RelayDiagnostics.read(json.optJSONObject("diagnostic")))
    }
    /** v2 reuses the bounded HTTPS transport, not the legacy shared-token authorization. */
    internal fun postPortable(path:String,bearer:String,body:String,device:String="",version:Int=1):String {
        require(approvedEndpoint.toString()=="https://caritda-ai-relay-dev.kyc66533.workers.dev")
        require(Regex("/v2/(devices/(challenge|register|revoke)|account/status|issues/(explanations|consent/revoke|dialogue|dialogue/consent/revoke)|explanations|operations/o2\\.[0-9]{13}\\.[a-f0-9-]{36})").matches(path))
        require(bearer.length in 1..8192&&!bearer.contains('\n')&&!bearer.contains('\r'))
        return postAnalysis(URL(approvedEndpoint.toString()+path),bearer,body.toByteArray(Charsets.UTF_8),mapOf("X-Caritda-Device-Id" to device,"X-Caritda-Key-Version" to version.toString()))
    }
    private fun postAnalysis(endpoint:URL,token:String,body:ByteArray,extraHeaders:Map<String,String> = emptyMap()):String {
        check(!cancelled)
        val c=endpoint.openConnection() as HttpsURLConnection;active=c
        try{
            check(!cancelled)
            c.connectTimeout=6000;c.readTimeout=18000;c.instanceFollowRedirects=false;c.requestMethod="POST";c.doOutput=true
            c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json; charset=utf-8")
            extraHeaders.forEach{(name,value)->c.setRequestProperty(name,value)}
            require(body.size<=4096);c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use{it.write(body)}
            val status=c.responseCode
            val stream=if(status==200||status==202&&extraHeaders.isNotEmpty())c.inputStream else c.errorStream
            val response=stream?.use{s->val out=java.io.ByteArrayOutputStream();val chunk=ByteArray(2048);while(true){val n=s.read(chunk);if(n<0)break;if(out.size()+n>16384)throw RelayFailure(502,"APP_RESPONSE_LIMIT");out.write(chunk,0,n)};String(out.toByteArray(),Charsets.UTF_8)}?:"{}"
            if(status!=200&&!(status==202&&extraHeaders.isNotEmpty())){val error=runCatching{JSONObject(response).getJSONObject("error")}.getOrNull();if(extraHeaders.isNotEmpty())throw PortableFailure(error?.optString("code")?:"HTTP_ERROR");throw RelayFailure(status,error?.optString("code")?:"HTTP_ERROR",RelayDiagnostics.read(error?.optJSONObject("diagnostic")))}
            return response
        }catch(e:java.net.SocketTimeoutException){throw RelayFailure(504,"APP_TIMEOUT")}
        catch(e:java.io.IOException){throw RelayFailure(502,"APP_NETWORK")}
        finally{c.disconnect();if(active===c)active=null}
    }
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
class RelayFailure(val status:Int,rawCode:String,val diagnostic:String=""):IllegalStateException(when{
    status==401->"앱 인증이 거부됐어요. 임시 시험 토큰을 확인해 주세요."
    rawCode=="TOKEN_EXPIRED"->"앱 인증 시간이 끝났어요. 새 만료 설정을 확인해 주세요."
    status==429->"요청 한도에 도달했어요. 자동으로 다시 요청하지 않아요."
    rawCode=="PROVIDER_AUTH"->"Worker의 모델 인증 설정을 확인해 주세요."
    rawCode=="APP_RELAY_VERSION"->"먼저 Worker 수정본 v2를 배포해 주세요. 모델은 요청하지 않았어요."
    status==504->"모델 응답이 늦어 요청을 종료했어요."
    status==503->"중계의 보안 설정이나 활성화 상태를 확인해 주세요."
    rawCode=="MODEL_REQUEST_REJECTED"->"모델이 요청 형식을 거부했어요. 진단 코드와 Worker 버전을 확인해 주세요."
    rawCode=="MODEL_OUTPUT_LIMIT"->"모델 답변이 길이 제한으로 끝났어요. 자동으로 다시 요청하지 않아요."
    rawCode=="MODEL_SAFETY_BLOCKED"->"모델의 안전 필터가 답변을 제한했어요. 분석값은 그대로 유지돼요."
    RelayDiagnostics.code(rawCode).startsWith("APP_")->"앱의 응답 검사를 통과하지 못했어요. 진단 코드를 확인해 주세요."
    RelayDiagnostics.code(rawCode).startsWith("MODEL_")->"모델 응답을 검증하지 못했어요. 분석값은 그대로 유지돼요."
    else->"AI 요청을 완료하지 못했어요. 연결과 배포 버전을 확인해 주세요."
}) {val code=RelayDiagnostics.code(rawCode)}
