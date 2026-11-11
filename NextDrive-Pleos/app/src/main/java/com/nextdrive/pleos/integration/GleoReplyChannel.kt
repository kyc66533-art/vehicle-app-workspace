package com.nextdrive.pleos.integration

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import com.nextdrive.pleos.R
import org.json.JSONObject
import java.security.MessageDigest

/** Official callback action; target and invocation must BOTH be authenticated.
 * No known production receiver/certificate has been verified in this emulator. Empty release
 * resources therefore disable export. Never trust Intent/referrer/packageName extras as identity.
 * API34 callingPackage is only populated for a result-based caller: the plain startActivity
 * sample alone does not establish caller identity, so it deliberately stays local. */
class GleoReplyChannel(private val activity:Activity,private val beganAt:Long=SystemClock.elapsedRealtime()) {
    private var replied=false
    private val peer=activity.getString(R.string.caritda_gleo_peer_package)
    private val pin=activity.getString(R.string.caritda_gleo_peer_sha256).lowercase()
    val remainingMs get()=(6000-(SystemClock.elapsedRealtime()-beganAt)).coerceAtLeast(0)
    @Suppress("DEPRECATION")
    fun target():ComponentName? = runCatching {
        if(!Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+").matches(peer)||!Regex("[a-f0-9]{64}").matches(pin)||activity.callingPackage!=peer)return null
        val pm=activity.packageManager
        val info=pm.getPackageInfo(peer,PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures=info.signingInfo?.apkContentsSigners?:return null
        if(signatures.size!=1||MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()).joinToString(""){"%02x".format(it)}!=pin)return null
        val receivers=pm.queryBroadcastReceivers(Intent(ACTION).setPackage(peer),0).map{it.activityInfo}.filter{it.exported&&it.enabled&&it.applicationInfo.enabled}
        val receiver=receivers.singleOrNull()?:return null
        ComponentName(receiver.packageName,receiver.name)
    }.getOrNull()
    fun consented():Boolean=target()!=null&&preferences().getString("peer","")=="$peer:$pin:v1"
    fun allowReadOnceConfigured():Boolean {
        if(target()==null)return false
        return preferences().edit().putString("peer","$peer:$pin:v1").commit()
    }
    fun revoke(){preferences().edit().remove("peer").apply()}
    private fun preferences()=activity.getSharedPreferences("caritda_gleo_read_consent",Context.MODE_PRIVATE)
    fun send(response:JSONObject):Boolean {
        if(replied||remainingMs==0L||!consented())return false
        val component=target()?:return false
        val fn=response.optString("functionName");if(fn!=activity.intent.action)return false
        val data=org.json.JSONArray(response.getString("data"))
        CaritdaActions.callback(fn,response.getString("status"),response.getString("message"),data,response.getString("mode"))
        replied=true // No retry even if Android reports a transport error.
        return runCatching{activity.sendBroadcast(Intent(ACTION).setComponent(component).putExtra("packageName",activity.packageName).putExtra("response",response.toString()));true}.getOrDefault(false)
    }
    companion object { const val ACTION="ai.pleos.playground.intent.action.APP_ACTION_RESPONSE" }
}

/** Only the live, explicitly confirmed MainActivity session can supply a handover reader. */
object ActionDriverSession { var id:String?=null;internal set }
