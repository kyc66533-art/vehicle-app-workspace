package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.widget.EditText
import com.nextdrive.pleos.ai.auth.FirebaseSessionProvider
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import java.io.File
import java.util.UUID

/** Only prepares the TEST UI. A human enters their password in the FLAG_SECURE Firebase form. */
class VoiceReauthChecks(private val inst:Instrumentation){
    fun run():List<String>{
        val ctx=inst.targetContext;ValidationEnvironment.requireIsolated(ctx);val h=BLightHarness(inst)
        val prefix="gleo-ui-"+UUID.randomUUID();val owner=SqliteIssueRepository(ctx,"$prefix.db").use{val s=CaritdaService(it,{true});s.registerDriver("계정 확인 운전자").also(s::selectDriver).id}
        val a=inst.startActivitySync(Intent(ctx,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var ai:PortableAiActivity?=null;val monitor=inst.addMonitor(PortableAiActivity::class.java.name,null,false)
        try{
            h.click(a,"driver_$owner");h.click(a,"start_driving");h.click(a,"settings_help");h.click(a,"ai_account_check")
            ai=monitor.waitForActivityWithTimeout(15000) as? PortableAiActivity?:error("REAUTH_SCREEN_NOT_OPENED")
            val page=ai;val before=FirebaseSessionProvider.subject();check(before!=null)
            val screenDeadline=System.currentTimeMillis()+20000
            while(System.currentTimeMillis()<screenDeadline&&!h.main{page.window.decorView.findViewWithTag<EditText>("portable_reauth_password")?.isEnabled==true})Thread.sleep(70)
            check(h.main{page.window.decorView.findViewWithTag<EditText>("portable_reauth_password")?.isEnabled==true}){"REAUTH_FORM_NOT_READY"}
            h.main{page.window.decorView.findViewWithTag<EditText>("portable_reauth_password").apply{requestFocus();requestRectangleOnScreen(android.graphics.Rect(0,0,width,height),true)}}
            File(ctx.filesDir,"home-reauth-stage.json").writeText("{\"phase\":\"PASSWORD_READY\"}")
            val end=System.currentTimeMillis()+900000
            var done=false
            while(System.currentTimeMillis()<end){
                if(h.main{(h.get(page,"message") as String).startsWith("현재 계정 확인을 갱신했어요.")}){done=true;break};Thread.sleep(150)
            }
            check(done){"USER_REAUTH_NOT_COMPLETED_NO_MODEL_REQUEST"};check(FirebaseSessionProvider.subject()==before){"REAUTH_ACCOUNT_CHANGED"}
            File(ctx.filesDir,"home-reauth-stage.json").writeText("{\"phase\":\"COMPLETE\"}")
            val prepared=VoiceRealModelChecks(inst).run("USER_APPROVED_MAX_TWO_20261112",true)
            return listOf("PASS human reauthenticated same Firebase subject without sign-out or model request")+prepared
        }finally{inst.removeMonitor(monitor);h.main{ai?.finish();a.finish()}}
    }
}
