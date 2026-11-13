package com.nextdrive.pleos

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.*
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.PortableSession
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.ui.*
import java.util.UUID
import kotlin.math.*

/** Real native UI/SQLite, private UUID fixtures. No STT/model/registration/route effects. */
class ReadabilityUiChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val h=BLightHarness(inst);private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private class Offline:PortableSession{
        override val signedIn=true;override val subject="readability-ui-"+UUID.randomUUID()
        override fun matches(uid:String)=uid==subject
        override fun acquire(done:(String?,String?,String?)->Unit)=error("UI_MUST_NOT_ACQUIRE_ACCOUNT")
        override fun client(store:PortableAiStore,uid:String,bearer:String,allowed:()->Boolean,job:String?,record:String?):PortableAiClient=error("UI_MUST_NOT_CREATE_MODEL_CLIENT")
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val prefix="gleo-ui-"+UUID.randomUUID();val db="$prefix.db"
        val (owner,issue,otherIssue)=SqliteIssueRepository(c,db).use{r->
            val s=CaritdaService(r,{true});val owner=s.registerDriver("운전자 가");val next=s.registerDriver("운전자 나")
            s.selectDriver(owner);val issue=s.record(SymptomType.NOISE,"앞쪽에서 소음이 들려요",System.currentTimeMillis(),"")
            s.selectDriver(next);val other=s.record(SymptomType.VIBRATION,"진동이 느껴져요",System.currentTimeMillis(),"");s.selectDriver(owner)
            Triple(owner,issue,other)
        }
        var a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))as MainActivity
        fun tag(key:String):View?=a.window.decorView.findViewWithTag(key)
        fun text()=h.main{h.text(a.window.decorView)}
        try{
            h.click(a,"driver_${owner.id}");h.click(a,"start_driving")
            h.click(a,"all_issues");h.main{(tag("record_search")as EditText).setText("소음")};h.click(a,"apply_search");h.click(a,"filter_MINE")
            h.click(a,"issue_${issue.id}");h.click(a,"back")
            ck(h.main{(tag("record_search")as EditText).text.toString()=="소음"&&(tag("filter_MINE")as Button).isSelected},"RU01 detail previous preserves the actual query/filter")
            h.click(a,"nav_HOME");h.click(a,"all_issues")
            ck(h.main{(tag("record_search")as EditText).text.isEmpty()&&(tag("filter_ALL")as Button).isSelected&&tag("issue_${otherIssue.id}")!=null},"RU02 home all-record action really displays all records without deleting any search data")
            ck(h.main{tag("restore_record_search")!=null},"RU03 previous search has an explicit visible restore action")
            val monitor=inst.addMonitor(MainActivity::class.java.name,null,false);h.main{a.recreate()};a=(inst.waitForMonitorWithTimeout(monitor,14000)as?MainActivity)?:error("READABILITY_RECREATION_MISSING");inst.removeMonitor(monitor)
            h.click(a,"restore_record_search")
            ck(h.main{(tag("record_search")as EditText).text.toString()=="소음"&&(tag("filter_MINE")as Button).isSelected&&tag("issue_${otherIssue.id}")==null},"RU04 previous query and filter survive Activity recreation and restore together")
            h.click(a,"nav_HOME");h.click(a,"settings_help")
            val expected=listOf("manage_drivers","vehicle_settings","voice_settings_history","help","replay_guide","ai_account_check","app_technical")
            ck(h.main{h.views(a.window.decorView).filterIsInstance<Button>().map{it.tag?.toString()}.filter{it in expected}==expected},"RU05 all seven settings destinations remain real visible rows in stable order")
            h.capture(a,"readability-settings.png")
            h.click(a,"help");ck(text().contains("다음 운전자")&&text().contains("AI 설명은 원할 때"),"RU06 task help uses plain wording while preserving optional AI")
            h.capture(a,"readability-help.png");h.click(a,"nav_HOME");h.click(a,"settings_help");h.click(a,"app_technical")
            ck(h.main{tag("developer_info")!=null&&tag("load_raw")==null},"RU07 developer evidence is initially collapsed rather than removed")
            h.click(a,"developer_info");ck(h.main{tag("load_raw")!=null&&tag("analysis_preview_tool")!=null},"RU08 raw SDK evidence and local JSON tools remain accessible through real folds")
            h.capture(a,"readability-developer.png")
            h.click(a,"nav_HOME");h.click(a,"vehicle_health");h.click(a,"health_expand")
            h.click(a,"health_toggle_DOOR_FL");ck(h.main{tag("drivecheck_item_DOOR_FL")!=null&&tag("drivecheck_item_LOW_BEAM")==null},"RU09 opening doors leaves other technical groups collapsed")
            h.click(a,"health_toggle_LOW_BEAM");ck(h.main{tag("drivecheck_item_LOW_BEAM")!=null&&tag("drivecheck_item_DOOR_FL")==null},"RU10 opening another group closes the previous group without interpreting raw door values")
            h.capture(a,"readability-vehicle.png")
            h.click(a,"nav_HOME");h.click(a,"all_issues");h.click(a,"issue_${issue.id}");h.click(a,"issue_summary")
            ck(h.main{tag("repair_nearby_search")!=null&&!text().contains("카카오")&&text().contains("내비")},"RU11 latest Pleos repair search remains the primary actual consultation action")
            h.capture(a,"readability-repair.png")
            h.click(a,"nav_HOME");h.click(a,"new_issue")
            ck(h.main{SymptomType.entries.all{type->(tag("type_${type.name}")as?Button)?.compoundDrawablesRelative?.get(1)!=null}},"RU12 all four symptom choices have native icons and text with original tags")
            h.click(a,"type_VIBRATION");ck(h.main{(tag("type_VIBRATION")as Button).isSelected},"RU13 a real tile selection still edits the draft type")
            h.capture(a,"readability-record.png")
            h.click(a,"discard_draft");val dialog=h.main{h.get(a,"activeDialog")as AlertDialog}
            ck(h.main{dialog.findViewById<TextView>(android.R.id.message).textSize/a.resources.displayMetrics.scaledDensity>=25f&&dialog.getButton(AlertDialog.BUTTON_NEGATIVE).minHeight>=76*a.resources.displayMetrics.density},"RU14 confirmation uses readable body text and retains a 76dp cancel target")
            h.main{dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()}
            ck(h.main{tag("situation")!=null},"RU15 cancelling the confirmation preserves the original editable draft")
            dialogs(a,prefix)
            contrast(a)
            SqliteIssueRepository(c,db).use{r->ck(r.issue(issue.id)==issue&&r.issue(otherIssue.id)==otherIssue&&r.events(issue.id).size==1&&r.events(otherIssue.id).size==1,"RU16 reading, layout, search restoration and cancelled deletion preserve both issue originals and histories")}
            consent(prefix,issue.id)
        }finally{h.main{a.finish()}}
        lines.add("BOUNDARIES NATIVE_UI_SQL=REAL PHYSICAL_TOUCH_STT_MODEL_ROUTE=NOT_INVOKED FLAG_SECURE=PRESERVED")
        return lines
    }
    @Suppress("DEPRECATION")
    private fun dialogs(a:MainActivity,prefix:String){
        val original=android.content.res.Configuration(a.resources.configuration)
        try{
            for(dark in listOf(false,true))for(width in listOf(320,600))for(font in listOf(1f,2f)){
                h.main{
                    CaritdaTheme.preferences(a,prefix).edit().putString("theme",if(dark)"dark"else"light").commit()
                    a.resources.updateConfiguration(android.content.res.Configuration(original).apply{fontScale=font;screenWidthDp=width;screenHeightDp=480},null)
                    a.window.setLayout((width*a.resources.displayMetrics.density).toInt(),(480*a.resources.displayMetrics.density).toInt())
                    h.draw(a)
                };inst.waitForIdleSync()
                h.click(a,"discard_draft");val dialog=h.main{h.get(a,"activeDialog")as AlertDialog}
                h.main{dialog.window!!.setLayout((width*a.resources.displayMetrics.density).toInt(),ViewGroup.LayoutParams.WRAP_CONTENT)};inst.waitForIdleSync()
                ck(h.main{
                    val title=dialog.findViewById<TextView>(a.resources.getIdentifier("alertTitle","id","android"))
                    val layout=title?.layout
                    val fullTitle=title!=null&&layout!=null&&layout.height<=title.height-title.compoundPaddingTop-title.compoundPaddingBottom+3&&(0 until layout.lineCount).all{layout.getEllipsisCount(it)==0}
                    fullTitle&&listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE).all{which->
                        val button=dialog.getButton(which);val r=android.graphics.Rect();button.getGlobalVisibleRect(r)&&r.width()>=76*a.resources.displayMetrics.density-3&&r.height()>=76*a.resources.displayMetrics.density-3
                    }
                },"RU21 confirmation title and both actions fit ${width}dp / font $font / ${if(dark)"dark"else"light"}")
                h.main{dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()}
            }
        }finally{h.main{a.resources.updateConfiguration(original,null);a.window.setLayout(-1,-1);h.draw(a)};inst.waitForIdleSync()}
    }
    private fun contrast(a:Activity){
        for(dark in listOf(false,true))for(style in ActionStyle.entries){
            val ratio=h.main{
                val ui=CaritdaComponents(a,CaritdaPalette.of(dark));val button=ui.action("읽기", "contrast",style,true){}
                button.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(300),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));button.layout(0,0,button.measuredWidth,button.measuredHeight)
                button.isSelected=true;button.isPressed=true;button.refreshDrawableState()
                check(button.background.current is android.graphics.drawable.LayerDrawable){"PRESSED_STATE_HIDDEN_BY_SELECTION"}
                val bitmap=Bitmap.createBitmap(button.width,button.height,Bitmap.Config.ARGB_8888);button.draw(Canvas(bitmap));val bg=bitmap.getPixel(button.width/2,ui.dp(3));bitmap.recycle()
                val lum1=Color.luminance(button.currentTextColor);val lum2=Color.luminance(bg);(max(lum1,lum2)+.05)/(min(lum1,lum2)+.05)
            }
            ck(ratio>=4.5,"RU17 actual pressed $style button contrast is at least 4.5 on ${if(dark)"dark"else"light"} theme (ratio=$ratio)")
        }
    }
    private fun consent(prefix:String,issueId:String){
        for(issueMode in listOf(true,false)){
            val request=Intent(c,PortableAiActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            if(issueMode)request.putExtra("issue_id",issueId)
            val screen=inst.startActivitySync(request)as PortableAiActivity
            try{
                h.await{(h.get(screen,"gate")as CarSafetyGate).state==EditingState.ALLOWED}
                if(issueMode)h.await{h.get(screen,"issue")!=null}
                val offline=Offline()
                h.main{h.set(screen,"session\u0024delegate",lazy{offline as PortableSession});h.set(screen,"loadedSubject",offline.subject);h.set(screen,"usageRefreshAttempted",true);h.set(screen,"autoConsumed",true);h.render(screen)}
                ck(h.main{h.text(screen.window.decorView).contains("Cloudflare")&&h.text(screen.window.decorView).contains("Gemini")&&!h.text(screen.window.decorView).contains("이 휴대폰")},"RU18 ${if(issueMode)"issue"else"SOC"} consent summary preserves named recipients and AAOS wording")
                h.main{check(screen.window.decorView.findViewWithTag<Button>("portable_consent_more").performClick())}
                ck(h.main{val t=h.text(screen.window.decorView);t.contains("원본 DB").or(issueMode)&&t.contains("철회")&&t.contains(if(issueMode)"하루 뒤 삭제"else"기기 서명")},"RU19 ${if(issueMode)"issue"else"SOC"} complete disclosure retains original scope, retention or authentication, and withdrawal")
                ck(screen.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0,"RU20 secure AI window is retained; no screenshot bypass")
            }finally{h.main{screen.finish()}}
        }
    }
}
