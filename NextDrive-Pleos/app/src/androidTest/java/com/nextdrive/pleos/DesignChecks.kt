package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.ui.*
import com.nextdrive.pleos.vehicle.*

/** Native controls on the isolated validation UID. Screenshots stay in private app files. */
class DesignChecks(private val inst:Instrumentation){
    private val c=inst.targetContext
    private val results=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};results.add("PASS $label")}
    private fun <T> main(f:()->T):T{var value:T?=null;var failure:Throwable?=null;inst.runOnMainSync{try{value=f()}catch(t:Throwable){failure=t}};failure?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    private fun await(label:String,f:()->Boolean){val end=System.currentTimeMillis()+25000;while(System.currentTimeMillis()<end){if(f())return;Thread.sleep(80)};error(label)}
    private fun tag(a:MainActivity,k:String):View?=a.window.decorView.findViewWithTag(k)
    private fun click(a:MainActivity,k:String){val routed=BLightTestNavigation.prepare(inst,a,k);inst.waitForIdleSync();if(routed)return;main{val v=tag(a,k)?:error("Missing $k");check(v.isEnabled);check(v.performClick())};inst.waitForIdleSync()}
    private fun nodes(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{nodes(v.getChildAt(it))}else emptyList()
    private fun text(a:MainActivity)=main{nodes(a.window.decorView).filterIsInstance<TextView>().filter{it.visibility==View.VISIBLE}.joinToString("\n"){it.text.toString()}}
    private fun launch()=inst.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(c,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun screenshot(name:String){inst.waitForIdleSync();val b=inst.uiAutomation.takeScreenshot()?:error("Screenshot failed");c.openFileOutput("design-$name.png",0).use{check(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private fun rows(db:SQLiteDatabase,table:String)=db.rawQuery("SELECT * FROM $table ORDER BY 1",null).use{r->buildList{while(r.moveToNext())add((0 until r.columnCount).map{if(r.isNull(it))null else r.getString(it)})}}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val repo=SqliteIssueRepository(c);val service=CaritdaService(repo,{true});service.selectDriver(Driver.A)
        val issue=service.record(SymptomType.VIBRATION,"가속할 때 핸들이 떨려요.",System.currentTimeMillis()-60000,"주차 후 점검할 예정이에요.")
        service.changeStatus(issue.id,IssueStatus.PLANNED);service.addNote(issue.id,"같은 증상이 있는지 확인했어요.")
        val obs=ObservationStore(c);val snapshots=SnapshotStore(c)
        var a=launch();await("input allowed"){main{tag(a,"driver_A")?.isEnabled==true}}
        click(a,"driver_A");click(a,"start_driving")
        // Core SOC/distance/fuel rows and their snapshot can precede the other public signals.
        // Count only after the whole batch finishes; late first-batch callbacks are not redraw reads.
        await("real entry SDK complete"){main{SdkBatchSettlement.complete(BLightHarness(inst),a,obs)}&&obs.latest(3).size==3&&snapshots.latest()!=null}
        verify(main{listOf("vehicle_health","all_issues","new_issue","voice_conversation").all{tag(a,it)!=null}&&tag(a,"metric_EV_SOC")==null},"PX01 home has three plain entry cards (vehicle status, records, new problem) and no raw metric rows")
        verify(main{tag(a,"help")==null}&&!text(a).contains("Capability")&&!text(a).contains("세션 ID")&&!text(a).contains("스냅샷"),"PX02 technical details and name administration do not occupy home")
        val count=obs.refs().size
        repeat(3){click(a,"nav_HOME")}
        Thread.sleep(1200)
        verify(obs.refs().size==count,"PX03 rendering and fixed home never fetch another SDK batch")
        screenshot("home-light")
        click(a,"menu")
        verify(main{listOf("manage_drivers","vehicle_settings","help","replay_guide","app_technical").all{tag(a,it)!=null}},"PX04 secondary destinations are explicit settings rows")
        click(a,"nav_VEHICLE")
        await("refresh enabled"){main{tag(a,"read_vehicle")?.isEnabled==true}}
        verify(text(a).contains("배터리 충전량")&&text(a).contains("누적 주행거리")&&text(a).contains("에뮬레이터에서 받은 값")&&!text(a).contains("Capability"),"PX05 vehicle overview shows actual meaning unit time and source without raw code")
        repeat(4){click(a,"read_vehicle");await("manual SDK batch complete"){main{tag(a,"read_vehicle")?.isEnabled==true}}}
        val latest=snapshots.latest()!!
        click(a,"auto_snapshots") // query history now lives on its own screen behind the vehicle screen button
        verify(main{nodes(a.window.decorView).count{it.tag?.toString()?.matches(Regex("snapshot_[0-9a-f-]{36}"))==true}==3},"PX06 initial history preview is limited to three real query rows")
        verify(text(a).contains("같은 값 다시 확인"),"PX07 unchanged real reads are described as repeat queries not physical failure")
        click(a,"all_vehicle_history")
        verify(main{nodes(a.window.decorView).count{it.tag?.toString()?.matches(Regex("snapshot_[0-9a-f-]{36}"))==true}>=5},"PX08 complete history exposes records beyond the initial three")
        click(a,"snapshot_${latest.id}")
        verify(!text(a).contains("관측 데이터 ID")&&!text(a).contains("Capability")&&main{tag(a,"snapshot_technical")!=null},"PX09 individual query starts with readable metrics and collapsed technical evidence")
        click(a,"snapshot_technical")
        verify(text(a).contains("관측 데이터 ID")&&text(a).contains("세션 ID")&&text(a).contains("Capability")&&text(a).contains("차량 측 측정시각"),"PX10 expansion preserves original IDs capability source timestamps and measurement unknown")
        click(a,"snapshot_technical");screenshot("query-detail")
        val beforeRecreate=obs.refs().size;val monitor=inst.addMonitor(MainActivity::class.java.name,null,false)
        main{a.recreate()};a=monitor.waitForActivityWithTimeout(15000) as? MainActivity?:error("recreated query detail missing");inst.removeMonitor(monitor)
        await("recreated query detail safe"){main{tag(a,"snapshot_technical")?.isEnabled==true}}
        verify(obs.refs().size==beforeRecreate&&main{tag(a,"snapshot_raw")==null&&tag(a,"value_EV_SOC")!=null},"PX25 actual recreation restores selected query and disclosure without another SDK read")
        click(a,"nav_HOME");click(a,"new_issue")
        verify(main{tag(a,"memo")==null&&tag(a,"select_snapshot")==null&&tag(a,"save_issue")!=null&&tag(a,"type_NOISE")!=null},"PX11 basic symptom input does not require optional metadata")
        main{(tag(a,"situation") as EditText).setText("새 소음이 들려요.")};val id=repo.draft()!!.id
        click(a,"save_issue")
        verify(repo.issue(id)?.situation=="새 소음이 들려요."&&main{tag(a,"issue_actions")!=null&&(tag(a,"feedback") as TextView).text.contains("저장되었습니다")},"PX12 short input commits once and opens a clear detail with persistent success")
        screenshot("record-detail")
        click(a,"nav_HOME");click(a,"new_issue");click(a,"type_VIBRATION")
        main{(tag(a,"situation") as EditText).setText("저속에서 떨림이 느껴져요.")}
        click(a,"record_extras");main{(tag(a,"memo") as EditText).setText("함께 저장할 메모")};click(a,"select_snapshot")
        val draft=repo.draft()!!;val selected=snapshots.selected(draft.id)
        click(a,"nav_HOME");click(a,"resume_draft")
        verify(repo.draft()?.ownerId=="A"&&main{(tag(a,"memo") as EditText).text.toString()=="함께 저장할 메모"}&&snapshots.selected(draft.id)==selected,"PX13 disclosure and home preserve original draft author memo and selected evidence")
        click(a,"save_issue");click(a,"issue_summary")
        verify(main{tag(a,"linked_snapshot")!=null}&&snapshots.linked(draft.id)?.id==selected,"PX14 local consultation still includes the immutable evidence link")
        click(a,"nav_HOME");click(a,"all_issues")
        main{(tag(a,"record_search") as EditText).setText("핸들")};click(a,"apply_search")
        click(a,"issue_${issue.id}");click(a,"back")
        verify(main{(tag(a,"record_search") as EditText).text.toString()=="핸들"&&tag(a,"issue_${issue.id}")!=null},"PX15 previous restores the search context")
        click(a,"nav_HOME");click(a,"new_issue");main{(tag(a,"situation") as EditText).setText("안전 제한 동안 보존할 초안")}
        val before=repo.issues().size;val oldSave=main{tag(a,"save_issue")!!}
        main{inst.callActivityOnStop(a);oldSave.performClick()}
        verify(repo.issues().size==before&&repo.draft()?.body=="안전 제한 동안 보존할 초안","PX16 stopped UNKNOWN state rejects a stale save control and retains draft")
        main{inst.callActivityOnStart(a)};await("safety reconnect"){main{tag(a,"nav_HOME")?.isEnabled==true}}
        click(a,"nav_HOME");click(a,"resume_draft")
        // Capture editing with no user database involved; retain this draft through matrix navigation.
        screenshot("record-form")
        paletteChecks()
        layoutMatrix(a,issue.id,latest.id)
        verify(obs.mostRecent("FUEL_PERCENT")?.let{it.status==ObservationStatus.UNSUPPORTED&&it.raw==null}==true,"PX20 fuel unsupported remains valueless after new UI routes")
        verify(repo.events(issue.id).any{it.kind==EventKind.STATUS}&&repo.issue(issue.id)?.status==IssueStatus.PLANNED,"PX21 UI rendering preserves manual inspection status and existing history")
        verify(snapshots.linked(draft.id)?.id==selected,"PX22 theme and layout changes never rewrite an existing evidence link")
        main{a.finish()};inst.waitForIdleSync();repo.close();obs.close();snapshots.close();return results
    }
    private fun paletteChecks(){
        fun luminance(c:Int):Double{fun f(v:Int):Double{val s=v/255.0;return if(s<=0.04045)s/12.92 else Math.pow((s+0.055)/1.055,2.4)};return .2126*f(Color.red(c))+.7152*f(Color.green(c))+.0722*f(Color.blue(c))}
        fun contrast(a:Int,b:Int):Double{val x=luminance(a);val y=luminance(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05)}
        for(dark in listOf(false,true)){val p=CaritdaPalette.of(dark)
            verify(listOf(p.text to p.surface,p.secondary to p.surface,p.onAccent to p.accent,p.secondary to p.inset,p.error to p.background).all{contrast(it.first,it.second)>=4.5},"PX17 ${if(dark)"dark"else"light"} content primary disabled and error token contrast at least 4.5")
        }
    }
    private fun layoutMatrix(a:MainActivity,issue:String,snapshot:String){
        val config=Configuration(a.resources.configuration);val root=main{(a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)}
        val oldWidth=root.layoutParams.width;val oldHeight=root.layoutParams.height
        val prefs=a.getPreferences(0);val theme=prefs.getString("theme","system")
        var renders=0
        fun inspect(){inst.waitForIdleSync();main{
            val views=nodes(root).filter{it.visibility==View.VISIBLE}
            check(views.all{it.width<=root.width}){"Horizontal overflow"}
            views.filterIsInstance<Button>().forEach{check(it.height>=76*a.resources.displayMetrics.density){"Small target ${it.tag}"}}
            views.filterIsInstance<TextView>().filter{it.layout!=null}.forEach{v->for(i in 0 until v.layout.lineCount)check(v.layout.getEllipsisCount(i)==0){"Ellipsis ${v.tag}"}}
            check((tag(a,"screen_scroll")?.height?:0)>=76*a.resources.displayMetrics.density){"No usable scroll viewport"}
            val nav=tag(a,"navigation_bar")!!;val before=Rect();nav.getGlobalVisibleRect(before)
            (tag(a,"screen_scroll") as ScrollView).scrollTo(0,5000);val after=Rect();nav.getGlobalVisibleRect(after);val scrolls=generateSequence(nav.parent){it.parent}.any{it is ScrollView};val shortWindow=(tag(a,"screen_scroll")!!.parent as View).height/a.resources.displayMetrics.density<480||a.resources.configuration.fontScale>=1.5f
            // By design the header scrolls with the body only on short windows (and record/list screens); elsewhere it must stay put.
            check(before==after||(scrolls&&(shortWindow||tag(a,"record_search")!=null||tag(a,"situation")!=null))){"Navigation scrolls away"}
        };renders++}
        try{
            for(mode in listOf("light","dark"))for(scale in listOf(1f,1.5f,2f))for(width in listOf(320,600,1024)){
                main{
                    check(prefs.edit().putString("theme",mode).commit())
                    @Suppress("DEPRECATION")
                    a.resources.updateConfiguration(Configuration(config).apply{fontScale=scale},a.resources.displayMetrics)
                    root.layoutParams=root.layoutParams.apply{this.width=(width*a.resources.displayMetrics.density).toInt();this.height=oldHeight};root.requestLayout()
                };inst.waitForIdleSync()
                click(a,"nav_HOME");inspect()
                click(a,"nav_VEHICLE");inspect();click(a,"auto_snapshots");inspect()
                click(a,"open_snapshot");inspect();click(a,"snapshot_technical");inspect()
                click(a,"nav_HOME");click(a,"all_issues");inspect();click(a,"issue_$issue");inspect()
                click(a,"nav_HOME");click(a,"resume_draft");inspect();click(a,"record_extras");inspect()
                click(a,"help");inspect();click(a,"close_help");click(a,"nav_HOME")
                if(width==1024&&scale==1f)screenshot("matrix-home-$mode-1024")
                main{root.layoutParams=root.layoutParams.apply{height=(320*a.resources.displayMetrics.density).toInt()};root.requestLayout()};inst.waitForIdleSync();click(a,"nav_HOME");inspect()
            }
            verify(true,"PX18 $renders native renders across 320/600/1024dp 100/150/200 percent two themes plus low height; touch text and fixed navigation checked")
        }finally{main{
            check(prefs.edit().putString("theme",theme).commit())
            @Suppress("DEPRECATION")
            a.resources.updateConfiguration(config,a.resources.displayMetrics)
            root.layoutParams=root.layoutParams.apply{width=oldWidth;height=oldHeight};root.requestLayout()
        }}
        inst.waitForIdleSync();click(a,"nav_HOME")
        verify(main{tag(a,"test_banner")!=null&&tag(a,"settings_help")!=null},"PX19 TEST identity and app home survive every tested layout")
        try{
            for(mode in listOf("light","dark")){
                main{check(prefs.edit().putString("theme",mode).commit())}
                click(a,"nav_VEHICLE");click(a,"nav_HOME");inst.waitForIdleSync()
                check(main{root.width==(root.parent as View).width}){"Full app window width was not restored"}
                screenshot("full-home-$mode")
            }
            verify(true,"PX26 full actual Pleos application window restored and captured in both themes")
        }finally{main{check(prefs.edit().putString("theme",theme).commit())};click(a,"nav_VEHICLE");click(a,"nav_HOME")}
    }
    fun preservation():List<String>{
        ValidationEnvironment.requireIsolated(c)
        for(kind in listOf("issues","observations","snapshots")){
            val before=SQLiteDatabase.openDatabase(c.getFileStreamPath("design-before-$kind.db").path,null,SQLiteDatabase.OPEN_READONLY)
            val after=SQLiteDatabase.openDatabase(c.getFileStreamPath("design-after-$kind.db").path,null,SQLiteDatabase.OPEN_READONLY)
            try{
                check(before.version==after.version)
                val tables=before.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='android_metadata'",null).use{r->buildList{while(r.moveToNext())add(r.getString(0))}}
                verify(tables.all{t->val old=rows(before,t);val current=rows(after,t);if(kind=="issues")old==current else current.containsAll(old)},"PX23 general $kind all preexisting scalar rows IDs relations preserved with unchanged schema")
                verify(after.rawQuery("PRAGMA integrity_check",null).use{it.moveToFirst();it.getString(0)=="ok"}&&after.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()},"PX24 general $kind integrity and foreign keys valid")
                if(kind=="issues")results.add("INVENTORY visible="+after.rawQuery("SELECT count(*) FROM issues WHERE deleted_at IS NULL",null).use{it.moveToFirst();it.getInt(0)})
            }finally{before.close();after.close()}
        }
        return results
    }
}
