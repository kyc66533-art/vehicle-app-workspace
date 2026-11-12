package com.nextdrive.pleos.voice

import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.nextdrive.pleos.MainActivity

/** Optional, system-bound role entry. No hotword detector, microphone or background collection. */
class CaritdaVoiceInteractionService:VoiceInteractionService(){
    override fun onReady(){super.onReady();setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)}
}

class CaritdaVoiceSessionService:VoiceInteractionSessionService(){
    override fun onNewSession(args:Bundle?):VoiceInteractionSession=object:VoiceInteractionSession(this){
        override fun onShow(args:Bundle?,showFlags:Int){
            super.onShow(args,showFlags)
            // Only the user-selected OS assistant can enter. Main still confirms the driver and
            // applies CarSafetyGate; selection never implies recording/AI/microphone consent.
            if(Build.VERSION.SDK_INT>=29&&getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)&&!getSystemService(KeyguardManager::class.java).isKeyguardLocked){
                runCatching{startVoiceActivity(Intent(this@CaritdaVoiceSessionService,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
            }
            finish()
        }
    }
}
