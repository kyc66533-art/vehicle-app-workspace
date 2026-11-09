package com.nextdrive.pleos.ai.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/** Standard Android Keystore P-256; registration/attestation is NOT implied by a local key. */
class DeviceKeyStore(private val alias:String) {
    init{require(alias.startsWith("caritda.")&&alias.length<=150)}
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    fun ensure() {
        if(store().containsAlias(alias))return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").apply{
            initialize(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false).build())
        }.generateKeyPair()
    }
    private fun publicKey()=store().getCertificate(alias)?.publicKey as? ECPublicKey?:error("DEVICE_KEY_MISSING")
    fun publicJwk():JSONObject {
        val key=publicKey()
        return JSONObject().put("kty","EC").put("crv","P-256").put("x",b64(coordinate(key.w.affineX))).put("y",b64(coordinate(key.w.affineY)))
    }
    fun thumbprint():String {val j=publicJwk();val canonical="{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"${j.getString("x")}\",\"y\":\"${j.getString("y")}\"}"
        return b64(MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)))}
    fun sign(proof:ByteArray):ByteArray {
        require(proof.size in 1..8192)
        val private=store().getKey(alias,null) as? PrivateKey?:error("DEVICE_KEY_MISSING")
        return derToP1363(Signature.getInstance("SHA256withECDSA").run{initSign(private);update(proof);sign()})
    }
    fun verify(proof:ByteArray,signature:ByteArray)=Signature.getInstance("SHA256withECDSA").run{initVerify(publicKey());update(proof);verify(p1363ToDer(signature))}
    companion object {
        private val order=BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",16)
        fun b64(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        private fun coordinate(n:BigInteger):ByteArray {require(n.signum()>=0);val raw=n.toByteArray().let{if(it.size==33&&it[0]==0.toByte())it.copyOfRange(1,it.size)else it};require(raw.size<=32);return ByteArray(32-raw.size)+raw}
        /** Length-prefixed UTF-8 wire contract from Aside §5.3a, not separator concatenation. */
        fun proof(fields:List<String>):ByteArray {require(fields.size==12);val out=ByteArrayOutputStream();DataOutputStream(out).use{d->fields.forEach{f->val b=f.toByteArray(Charsets.UTF_8);require(b.size<=1024);d.writeInt(b.size);d.write(b)}};return out.toByteArray().also{require(it.size<=8192)}}
        fun derToP1363(der:ByteArray):ByteArray {
            require(der.size in 8..72&&der[0]==0x30.toByte()&&(der[1].toInt() and 255)==der.size-2)
            var pos=2
            fun integer():ByteArray {
                require(pos+2<=der.size&&der[pos++]==2.toByte());val len=der[pos++].toInt() and 255
                require(len in 1..33&&pos+len<=der.size)
                val raw=der.copyOfRange(pos,pos+len);pos+=len
                require((raw[0].toInt() and 128)==0)
                require(raw.size==1||raw[0]!=0.toByte()||(raw[1].toInt() and 128)!=0)
                val n=BigInteger(1,raw);require(n>BigInteger.ZERO&&n<order)
                return coordinate(n)
            }
            val result=integer()+integer();require(pos==der.size);return result
        }
        fun p1363ToDer(bytes:ByteArray):ByteArray {
            require(bytes.size==64)
            fun integer(offset:Int):ByteArray {val n=BigInteger(1,bytes.copyOfRange(offset,offset+32));require(n>BigInteger.ZERO&&n<order);val b=n.toByteArray();return byteArrayOf(2,b.size.toByte())+b}
            val body=integer(0)+integer(32);return byteArrayOf(0x30,body.size.toByte())+body
        }
    }
}
