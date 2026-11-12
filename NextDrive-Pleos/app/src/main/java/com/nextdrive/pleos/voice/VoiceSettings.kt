package com.nextdrive.pleos.voice

import android.content.Context
import com.nextdrive.pleos.analytics.VehicleAnalytics

/** Every scope defaults off. Cloud permission is bound to the current driver AND Firebase subject. */
class VoiceSettings(context:Context,name:String="voice-automation-v1") {
    private val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
    data class Permit(val record:Boolean=false,val cloud:Boolean=false,val speak:Boolean=false,val followUp:Boolean=false,val history:Boolean=false,val alerts:Boolean=false)
    private fun key(owner:String,subject:String)="permit."+VehicleAnalytics.id(owner+":"+subject)
    fun read(owner:String,subject:String):Permit {val b=prefs.getInt(key(owner,subject),0);return Permit(b and 1!=0,b and 2!=0,b and 4!=0,b and 8!=0,b and 16!=0,b and 32!=0)}
    fun write(owner:String,subject:String,p:Permit){val key=key(owner,subject);check(prefs.edit().putString("$key.account",subject).putInt(key,(if(p.record)1 else 0)+(if(p.cloud)2 else 0)+(if(p.speak)4 else 0)+(if(p.followUp)8 else 0)+(if(p.history)16 else 0)+(if(p.alerts)32 else 0)).commit())}
    fun revokeCloud(subject:String){val edit=prefs.edit();prefs.all.filter{it.key.endsWith(".account")&&it.value==subject}.keys.forEach{entry->val key=entry.removeSuffix(".account");edit.putInt(key,prefs.getInt(key,0) and 2.inv())};check(edit.commit())}
}
