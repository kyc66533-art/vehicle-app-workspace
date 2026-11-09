package com.nextdrive.pleos.ai.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.nextdrive.pleos.R

/** Auth SDK owns session persistence and refresh. No password/JWT/refresh copy or token UI. */
object FirebaseSessionProvider {
    private var auth:FirebaseAuth?=null
    private var revision=0
    private var stateReady=false
    private val observers=linkedSetOf<()->Unit>()
    fun observe(callback:()->Unit):()->Unit {observers.add(callback);return {observers.remove(callback);Unit}}
    private fun changed(){observers.toList().forEach{it()}}
    private val authListener=FirebaseAuth.AuthStateListener {stateReady=true;changed()}
    val initialized get()=auth!=null&&stateReady
    var busy=false;private set
    var configState="MISSING";private set
    data class Result(val code:String,val signedIn:Boolean=false,val tokenAvailable:Boolean=false,val expiresAtSeconds:Long?=null,val issuedAtSeconds:Long?=null)
    fun prepare(context:Context):Result {
        configState=context.getString(R.string.caritda_firebase_state)
        if(configState!="READY")return Result("AUTH_CONFIG_$configState")
        if(context.getString(R.string.caritda_firebase_package)!=context.packageName)return Result("AUTH_PACKAGE_MISMATCH")
        return try {
            val name="CARITDA-AUTH-${context.packageName}"
            val app=FirebaseApp.getApps(context).firstOrNull{it.name==name}?:FirebaseApp.initializeApp(context.applicationContext,FirebaseOptions.Builder()
                .setApplicationId(context.getString(R.string.caritda_firebase_app))
                .setProjectId(context.getString(R.string.caritda_firebase_project))
                .setApiKey(context.getString(R.string.caritda_firebase_key)).build(),name)
            app.setDataCollectionDefaultEnabled(false)
            val next=FirebaseAuth.getInstance(app)
            if(auth!==next){auth?.removeAuthStateListener(authListener);auth=next;stateReady=false;next.addAuthStateListener(authListener)}
            snapshot()
        }catch(_:Exception){Result("AUTH_INITIALIZATION")}
    }
    fun snapshot()=Result(if(!initialized)"AUTH_RESTORING"else if(auth?.currentUser!=null)"AUTH_SIGNED_IN"else"AUTH_SIGNED_OUT",initialized&&auth?.currentUser!=null)
    internal fun subject()=if(initialized)auth?.currentUser?.uid?.let{com.nextdrive.pleos.analytics.VehicleAnalytics.id(it)}else null
    internal fun matches(uid:String)=initialized&&auth?.currentUser?.uid==uid
    fun signIn(email:String,password:String,done:(Result)->Unit) {
        val service=auth?:return done(Result("AUTH_CONFIG_MISSING"))
        if(!initialized)return done(Result("AUTH_RESTORING"))
        if(busy)return done(Result("AUTH_BUSY"))
        if(email.trim().length !in 3..254||!email.trim().contains('@')||password.isEmpty())return done(Result("AUTH_INPUT"))
        busy=true;val operation=revision;changed()
        try{service.signInWithEmailAndPassword(email.trim(),password).addOnCompleteListener{task->
            busy=false
            if(operation!=revision){service.signOut();done(Result("AUTH_CANCELLED"))}
            else if(task.isSuccessful)done(snapshot()) else done(Result(error(task.exception)))
            changed()
        }}catch(e:Exception){busy=false;done(Result(error(e)));changed()}
    }
    /** Metadata only for the Day1 compatibility gate. Never returns the bearer token to UI. */
    fun inspectToken(forceRefresh:Boolean=false,done:(Result)->Unit) {
        if(!initialized)return done(Result("AUTH_RESTORING"))
        val user=auth?.currentUser?:return done(Result("AUTH_SIGN_IN_REQUIRED"))
        if(busy)return done(Result("AUTH_BUSY"))
        busy=true;val operation=revision;changed()
        try{user.getIdToken(forceRefresh).addOnCompleteListener{task->
            busy=false
            if(operation!=revision)done(Result("AUTH_CANCELLED"))
            else if(task.isSuccessful){val r=task.result;val present=!r.token.isNullOrBlank()
                done(Result(if(present)"AUTH_TOKEN_AVAILABLE"else"AUTH_TOKEN_MISSING",true,present,r.expirationTimestamp,r.issuedAtTimestamp))}
            else done(Result(error(task.exception),auth?.currentUser!=null))
            changed()
        }}catch(e:Exception){busy=false;done(Result(error(e)));changed()}
    }
    fun signOut():Result {revision++;auth?.signOut();changed();return snapshot()}
    /** Bearer is handed only to the fixed-origin relay client; never rendered or persisted. */
    internal fun withSession(done:(String?,String?,String?)->Unit){
        if(!initialized)return done(null,null,"AUTH_RESTORING")
        val user=auth?.currentUser?:return done(null,null,"AUTH_SIGN_IN_REQUIRED")
        val operation=revision
        user.getIdToken(false).addOnCompleteListener{task->
            if(operation!=revision||auth?.currentUser?.uid!=user.uid)done(null,null,"AUTH_CANCELLED")
            else if(!task.isSuccessful)done(null,null,error(task.exception))
            else task.result.token?.takeIf{it.isNotBlank()}?.let{done(user.uid,it,null)}?:done(null,null,"AUTH_TOKEN_MISSING")
        }
    }
    fun error(e:Exception?):String=when(e){
        is FirebaseNetworkException->"AUTH_NETWORK"
        is FirebaseTooManyRequestsException->"AUTH_RATE_LIMIT"
        is FirebaseAuthException->when(e.errorCode){
            "ERROR_INVALID_CREDENTIAL","ERROR_WRONG_PASSWORD","ERROR_USER_NOT_FOUND"->"AUTH_CREDENTIALS"
            "ERROR_INVALID_EMAIL"->"AUTH_INPUT"
            "ERROR_USER_DISABLED"->"AUTH_DISABLED"
            "ERROR_OPERATION_NOT_ALLOWED"->"AUTH_PROVIDER_DISABLED"
            "ERROR_USER_TOKEN_EXPIRED","ERROR_INVALID_USER_TOKEN"->"AUTH_RELOGIN"
            "ERROR_APP_NOT_AUTHORIZED","ERROR_INVALID_API_KEY"->"AUTH_PROJECT_CONFIGURATION"
            else->"AUTH_FAILED"
        }
        else->"AUTH_FAILED"
    }
}
