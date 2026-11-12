package com.nextdrive.pleos.ai.auth

import android.content.Context
import com.nextdrive.pleos.ai.PortableAiClient
import com.nextdrive.pleos.ai.PortableAiStore

/** Authentication/transport boundary. The ordinary implementation always uses the existing
 * Firebase account, device key and fixed-origin relay. No Intent can replace this dependency. */
interface PortableSession {
    val signedIn:Boolean
    val subject:String?
    fun matches(uid:String):Boolean
    fun acquire(done:(String?,String?,String?)->Unit)
    fun client(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?):PortableAiClient
    fun dialogueClient(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String,record:String):PortableAiClient = error("DIALOGUE_SESSION_UNAVAILABLE")
}

class FirebasePortableSession(private val context:Context):PortableSession {
    override val signedIn get()=FirebaseSessionProvider.snapshot().signedIn
    override val subject get()=FirebaseSessionProvider.subject()
    override fun matches(uid:String)=FirebaseSessionProvider.matches(uid)
    override fun acquire(done:(String?,String?,String?)->Unit)=FirebaseSessionProvider.withSession(done)
    override fun client(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?)=
        PortableAiClient(store,DeviceKeyStore("caritda.portable.${context.packageName}"),uid,bearer,allowed,issueJob=job,issueRecord=record)
    override fun dialogueClient(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String,record:String)=
        PortableAiClient(store,DeviceKeyStore("caritda.portable.${context.packageName}"),uid,bearer,allowed,issueJob=job,issueRecord=record,dialogue=true)
}
