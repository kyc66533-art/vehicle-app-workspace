package com.nextdrive.pleos.integration

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** One explicit, Firebase-authenticated repair-place lookup.
 * No API key or persistent location/issue data is stored in the APK or journal. */
data class NearbyRepairPlace(
    val id:String, val name:String, val address:String, val phone:String,
    val latitude:Double, val longitude:Double, val distanceMeters:Int,
    val bluehandsCandidate:Boolean
)
class NearbyRepairError(val code:String):Exception(code)
object NearbyRepairClient {
    private const val ENDPOINT="https://caritda-ai-relay-dev.kyc66533.workers.dev/v2/repair/nearby"
    fun search(latitude:Double,longitude:Double,bearer:String):List<NearbyRepairPlace> {
        if(!latitude.isFinite()||!longitude.isFinite()||latitude !in 33.0..39.5||longitude !in 124.0..132.0)
            throw NearbyRepairError("LOCATION_OUT_OF_RANGE")
        if(bearer.isBlank())throw NearbyRepairError("AUTH_SIGN_IN_REQUIRED")
        val conn=URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod="POST";conn.instanceFollowRedirects=false
            conn.connectTimeout=6000;conn.readTimeout=9000
            conn.setRequestProperty("Authorization","Bearer $bearer")
            conn.setRequestProperty("Content-Type","application/json")
            conn.setRequestProperty("Accept","application/json")
            conn.setRequestProperty("Cache-Control","no-store")
            conn.doOutput=true
            val payload=JSONObject().put("latitude",latitude).put("longitude",longitude).toString().toByteArray(StandardCharsets.UTF_8)
            conn.outputStream.use{it.write(payload)}
            val status=conn.responseCode
            if(status!=200){
                val code=runCatching {
                    conn.errorStream?.use {stream->
                        val bytes=stream.readNBytes(4096)
                        JSONObject(String(bytes,StandardCharsets.UTF_8)).optJSONObject("error")?.optString("code")
                    }
                }.getOrNull()
                throw NearbyRepairError(code?.takeIf{Regex("[A-Z_]{2,60}").matches(it)}?:"PLACES_HTTP_$status")
            }
            val bytes=conn.inputStream.use{it.readNBytes(32769)}
            if(bytes.size>32768)throw NearbyRepairError("PLACES_RESPONSE_LIMIT")
            val root=JSONObject(String(bytes,StandardCharsets.UTF_8))
            if(root.optString("source")!="KAKAO_LOCAL")throw NearbyRepairError("PLACES_RESPONSE_INVALID")
            val items=root.getJSONArray("places")
            if(items.length()>3)throw NearbyRepairError("PLACES_RESPONSE_INVALID")
            return (0 until items.length()).map {i->
                val item=items.getJSONObject(i)
                val place=NearbyRepairPlace(
                    id=item.getString("id"),name=item.getString("name"),
                    address=item.getString("address"),phone=item.optString("phone",""),
                    latitude=item.getDouble("latitude"),longitude=item.getDouble("longitude"),
                    distanceMeters=item.getInt("distance_m"),
                    bluehandsCandidate=item.getString("category")=="BLUEHANDS_CANDIDATE")
                if(place.name.length !in 2..100||place.address.length>150||
                    place.latitude !in 33.0..39.5||place.longitude !in 124.0..132.0||
                    place.distanceMeters !in 0..20000)throw NearbyRepairError("PLACES_RESPONSE_INVALID")
                place
            }
        } catch(e:NearbyRepairError){throw e}
        catch(_:Exception){throw NearbyRepairError("PLACES_NETWORK_UNAVAILABLE")}
        finally{conn.disconnect()}
    }
    fun message(code:String)=when(code){
        "PLACES_NOT_CONFIGURED"->"주변 정비소 검색 서비스가 아직 연결되지 않았어요. 기존 정비 상담 기능은 이용할 수 있어요."
        "AUTH_SIGN_IN_REQUIRED","AUTH_RESTORING","AUTH_TOKEN_MISSING"->"정비소 검색을 위해 AI 계정의 로그인 상태를 확인해 주세요. Gemini 호출은 하지 않아요."
        "LOCATION_OUT_OF_RANGE"->"이 위치에서는 국내 정비소를 검색할 수 없어요."
        "LOCATION_STALE"->"마지막 확인 위치가 오래됐어요. 정확한 위치를 확인한 다음 다시 검색해 주세요."
        "PLACES_PROVIDER_UNAVAILABLE","PLACES_NETWORK_UNAVAILABLE","PLACES_RESPONSE_LIMIT","PLACES_RESPONSE_INVALID"->"정비소 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
        else->"주변 정비소 검색을 완료하지 못했어요. 기존 기록은 그대로예요."
    }
}
