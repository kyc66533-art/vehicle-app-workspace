package com.nextdrive.pleos.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate encrypted metadata/result sidecar. No password, bearer or provider secret stored. */
class PortableAiStore(context:Context,name:String="portable-ai-v2") {
    private var blocked=false
    private val preferences=context.applicationContext.getSharedPreferences(name,Context.MODE_PRIVATE)
    private val alias="caritda.ai.storage.$name"
    private fun key(create:Boolean):SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(alias,null) as? SecretKey)?.let{return it}
        check(create){"DEVICE_STORAGE_KEY_MISSING"}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run{
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());generateKey()
        }
    }
    @Synchronized fun read():JSONObject {
        val raw=preferences.getString("state",null)?:return JSONObject()
        try{val data=Base64.decode(raw,Base64.NO_WRAP);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),GCMParameterSpec(128,data.copyOfRange(0,12)));cipher.updateAAD(alias.toByteArray());return JSONObject(String(cipher.doFinal(data.copyOfRange(12,data.size)),Charsets.UTF_8))}
        catch(_:Exception){throw PortableFailure("DEVICE_KEY_MISSING")}
    }
    @Synchronized fun write(value:JSONObject){
        check(!blocked){"AI_SESSION_CLOSED"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(!preferences.contains("state")));cipher.updateAAD(alias.toByteArray())
        val bytes=cipher.iv+cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8));check(preferences.edit().putString("state",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit())
    }
    @Synchronized fun withdraw(){check(preferences.edit().remove("state").commit())} // Only this AI sidecar; never vehicle/user DBs.
    @Synchronized fun invalidate(){blocked=true}
}
