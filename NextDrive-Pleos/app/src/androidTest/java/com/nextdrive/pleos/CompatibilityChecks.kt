package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.ui.*
import com.nextdrive.pleos.vehicle.*
import java.time.ZoneId

/** All fixtures, UI mutations and captured screens belong to the separate validation UID. */
class CompatibilityChecks(private val inst:Instrumentation) {
    private val c=inst.targetContext
    private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,msg:String){check(ok){msg};lines.add("PASS $msg")}
    private fun <T> main(f:()->T):T{var v:T?=null;var failure:Throwable?=null;inst.runOnMainSync{try{v=f()}catch(t:Throwable){failure=t}};failure?.let{throw it};@Suppress("UNCHECKED_CAST")return v as T}
    private fun await(msg:String,f:()->Boolean){val end=System.currentTimeMillis()+25000;while(System.currentTimeMillis()<end){if(f())return;Thread.sleep(80)};error(msg)}
    private fun nodes(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{nodes(v.getChildAt(it))}else emptyList()
    private fun tag(a:MainActivity,k:String):View?=a.window.decorView.findViewWithTag(k)
    private fun text(a:MainActivity)=main{nodes(a.window.decorView).filterIsInstance<TextView>().filter{it.visibility==View.VISIBLE}.joinToString("\n"){it.text.toString()}}
    private fun click(a:MainActivity,k:String){ValidationUiActions.reveal(inst,a,k);main{val v=tag(a,k)?:error("Missing $k");check(v.isEnabled);check(v.performClick())};inst.waitForIdleSync()}
    private fun launch()=inst.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(c,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun screenshot(name:String){inst.waitForIdleSync();val b=inst.uiAutomation.takeScreenshot()?:error("screenshot");c.openFileOutput("compat-$name.png",0).use{check(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private val menuKeys=listOf("change_driver","my_issues","auto_snapshots","help","vehicle_settings")
    private fun menu(a:MainActivity)=main{nodes(tag(a,"more_menu")!!).filterIsInstance<Button>().map{it.tag.toString() to it.text.toString()}}
    private fun dbRows(repo:SqliteIssueRepository)=listOf("drivers","issues","events","settings","drafts").associateWith{t->repo.readableDatabase.rawQuery("SELECT * FROM $t ORDER BY 1",null).use{r->buildList{while(r.moveToNext())add((0 until r.columnCount).map{if(r.isNull(it))null else r.getString(it)})}}}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        policy()
        val settings=VehicleDisplaySettings(c.getSharedPreferences("caritda_vehicle_display",0))
        val repo=SqliteIssueRepository(c);val service=CaritdaService(repo,{true});service.selectDriver(Driver.A)
        repeat(12){service.record(SymptomType.NOISE,"별도 검증 목록 $it",System.currentTimeMillis(),"")}
        val issue=service.record(SymptomType.VIBRATION,"상담 원문 보존",System.currentTimeMillis(),"원래 메모")
        service.addNote(issue.id,"점검 이력 보존");service.recur(issue.id,"재발 이력 보존")
        val obs=ObservationStore(c);val snapshots=SnapshotStore(c)
        var a=launch();await("driver allowed"){main{tag(a,"driver_A")?.isEnabled==true}}
        click(a,"driver_A");click(a,"start_driving")
        await("actual entry complete"){obs.latest(3).size==3&&snapshots.latest()!=null}
        val count=obs.refs().size;val records=dbRows(repo)
        var reference:List<Pair<String,String>>?=null
        for(route in listOf("nav_HOME","nav_ISSUES","nav_VEHICLE")){
            click(a,route)
            if(route=="nav_ISSUES"){
                main{(tag(a,"record_search") as EditText).setText("검증")};click(a,"apply_search");click(a,"filter_MANAGED")
            }
            main{(tag(a,"screen_scroll") as ScrollView).scrollTo(0,250)};inst.waitForIdleSync()
            val position=main{(tag(a,"screen_scroll") as ScrollView).scrollY}
            click(a,"menu");val current=menu(a)
            verify(current.map{it.first}==menuKeys&&(reference==null||reference==current),"CP menu order and names identical from $route")
            reference=current
            verify(main{listOf("metric_EV_SOC","record_search","read_vehicle").none{tag(a,it)!=null}&&nodes(tag(a,"navigation_bar")!!).filterIsInstance<Button>().count{it.isSelected}==1&&tag(a,"menu")!!.isSelected},"CP standalone menu hides original body and selects only menu from $route")
            click(a,"close_menu")
            verify(main{(tag(a,"screen_scroll") as ScrollView).scrollY==position}&&(route!="nav_ISSUES"||main{(tag(a,"record_search") as EditText).text.toString()=="검증"&&(tag(a,"filter_MANAGED") as Button).text.startsWith("✓ ")}),"CP menu close restores original scroll and search/filter from $route")
        }
        verify(obs.refs().size==count&&dbRows(repo)==records,"CP menu navigation causes no additional SDK reads or record mutations")
        click(a,"nav_HOME");click(a,"vehicle_settings")
        for(layout in PowertrainLayout.entries){
            click(a,"layout_${layout.name}");click(a,"nav_HOME")
            val keys=main{nodes(a.window.decorView).mapNotNull{it.tag?.toString()}.filter{it.startsWith("metric_")}.map{it.removePrefix("metric_")}}
            val expected=VehicleInfoPolicy.kinds(layout,{obs.mostRecent(it)},{obs.mostRecent(it,true)})
            verify(keys==expected&&text(a).contains(if(layout==PowertrainLayout.UNKNOWN)"차종 미확인"else"사용자가 정한 화면 배치"),"CP ${layout.name} home uses explicit manual layout and supported signals")
            if(layout==PowertrainLayout.ICE)verify(main{tag(a,"value_EV_SOC")==null&&(tag(a,"value_FUEL_PERCENT") as TextView).text.toString()=="지원되지 않음"},"CP ICE shows unsupported fuel without a zero or invented SOC")
            if(layout==PowertrainLayout.HEV)verify(main{tag(a,"value_FUEL_PERCENT")!=null&&tag(a,"value_EV_SOC")!=null},"CP hybrid presents energy signals separately")
            click(a,"vehicle_settings")
        }
        click(a,"layout_UNKNOWN");click(a,"time_seoul");click(a,"nav_HOME")
        verify(text(a).contains("KST (+09:00)")&&text(a).contains("구동 배터리 충전량")&&text(a).contains("한국 시간"),"CP unknown vehicle explains traction SOC and shows explicit Korean receipt time")
        screenshot("home-kst")
        val raw=obs.refs()
        click(a,"vehicle_settings");click(a,"time_device");click(a,"nav_HOME")
        verify(text(a).contains(ZoneId.systemDefault().id)&&obs.refs()==raw&&dbRows(repo)==records,"CP device time selection changes presentation only and preserves raw timestamps")
        click(a,"new_issue");main{(tag(a,"situation") as EditText).setText("메뉴 이동 중에도 보존할 초안")}
        val draft=repo.draft()!!;click(a,"menu");click(a,"menu")
        verify(main{(tag(a,"situation") as EditText).text.toString()==draft.body}&&repo.draft()==draft,"CP toggle menu preserves authored draft without saving an issue")
        click(a,"change_driver")
        verify(main{tag(a,"save_issue")!=null}&&repo.draft()==draft&&repo.currentDriver()?.id=="A"&&text(a).contains("작성 중인 내용을 먼저"),"CP blocked driver transition returns to original draft with clear feedback")
        click(a,"help");click(a,"close_help")
        verify(main{tag(a,"situation")!=null}&&repo.draft()==draft,"CP menu destination returns directly to original draft")
        click(a,"menu")
        val monitor=inst.addMonitor(MainActivity::class.java.name,null,false);main{a.recreate()};a=monitor.waitForActivityWithTimeout(15000) as? MainActivity?:error("recreate");inst.removeMonitor(monitor)
        await("menu restored"){main{tag(a,"close_menu")?.isEnabled==true}}
        verify(menu(a).map{it.first}==menuKeys,"CP configuration recreation restores independent menu")
        click(a,"close_menu")
        verify(main{(tag(a,"situation") as EditText).text.toString()==draft.body}&&repo.draft()==draft,"CP recreated menu closes to original authored draft")
        click(a,"nav_HOME");click(a,"all_issues");click(a,"issue_${issue.id}")
        val original=repo.issue(issue.id);val events=repo.events(issue.id)
        verify(!text(a).contains("내용 버전")&&text(a).contains("다시 발생 1회"),"CP detail shows recurrence without internal revision")
        click(a,"issue_summary")
        verify(!text(a).contains("내용 버전")&&text(a).contains("재발 기록: 1회")&&text(a).contains("상담 원문 보존")&&text(a).contains("점검 이력 보존")&&text(a).contains("재발 이력 보존")&&repo.issue(issue.id)==original&&repo.events(issue.id)==events,"CP consultation hides revision while preserving original content notes recurrence and raw revision")
        matrix(a)
        click(a,"vehicle_settings");click(a,"layout_HEV");click(a,"time_device")
        check(c.getSharedPreferences("compat_cold",0).edit().putString("draft",draft.id).putString("body",draft.body).putInt("observations",obs.refs().size).commit())
        main{a.finish()};inst.waitForIdleSync();repo.close();obs.close();snapshots.close();return lines
    }
    private fun policy(){
        fun sample(k:String,raw:String,unit:String)=VehicleObservation(k,raw,unit,1767225600000L,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"SYNTHETIC_VALIDATION_ONLY",ObservationStatus.RECEIVED,"isolated in-memory presentation fixture")
        val soc=sample("EV_SOC","65","%");val fuel=sample("FUEL_PERCENT","0","%");val odo=sample("ODOMETER","0.0","km")
        val values=mapOf("EV_SOC" to soc,"FUEL_PERCENT" to fuel,"ODOMETER" to odo)
        verify(VehicleInfoPolicy.kinds(PowertrainLayout.UNKNOWN,{null},{null})==listOf("ODOMETER"),"CP unknown without support assumes no energy source")
        verify(VehicleInfoPolicy.kinds(PowertrainLayout.UNKNOWN,{values[it]},{values[it]})==listOf("EV_SOC","FUEL_PERCENT","ODOMETER"),"CP unknown can display both supported energy signals without classifying vehicle")
        verify(VehicleInfoPolicy.kinds(PowertrainLayout.ICE,{values[it]},{values[it]})==listOf("FUEL_PERCENT","ODOMETER")&&VehicleInfoPolicy.kinds(PowertrainLayout.EV,{values[it]},{values[it]})==listOf("EV_SOC","ODOMETER"),"CP ICE and EV manual layout selection is independent from observed type")
        verify(listOf(PowertrainLayout.HEV,PowertrainLayout.PHEV).all{VehicleInfoPolicy.kinds(it,{values[it]},{values[it]}).size==3},"CP HEV and PHEV reserve independent fuel and traction battery metrics")
        verify(VehiclePresentation.card("FUEL_PERCENT",fuel,fuel,1767225600000L).value=="0 %"&&VehiclePresentation.card("ODOMETER",odo,odo,1767225600000L).value=="0 km","CP valid fuel zero and distance zero are distinct from missing values; synthetic fixture only")
        val failure=fuel.copy(raw=null,unit=null,receivedAt=null,valid=false,supported=false,status=ObservationStatus.UNSUPPORTED)
        verify(!VehiclePresentation.card("FUEL_PERCENT",failure,null,1767225600000L).value.contains("0 %")&&VehiclePresentation.card("FUEL_PERCENT",failure,fuel,1767225600000L).value.contains("이전 값"),"CP unsupported and last failed attempt never turn into current zero")
        verify(VehiclePresentation.card("EV_SOC",soc,fuel,1767225600000L).value.contains("아직")&&VehiclePresentation.card("EV_SOC",soc,soc.copy(source=ObservationSource.TEST_ONLY),1767225600000L).value.contains("아직"),"CP wrong-kind and TEST_ONLY values cannot be displayed as actual SOC")
        val kst=VehicleDisplaySettings.format(1767225600000L,ZoneId.of("Asia/Seoul"));val la=VehicleDisplaySettings.format(1767225600000L,ZoneId.of("America/Los_Angeles"))
        verify(kst.contains("2026.01.01 09:00:00 KST (+09:00)")&&la.contains("2025.12.31 16:00:00 America/Los_Angeles (-08:00)"),"CP identical epoch formats explicit date and zone across Korean and Los Angeles time")
    }
    private fun matrix(a:MainActivity){
        val root=main{a.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)}
        val config=Configuration(a.resources.configuration);val width=root.layoutParams.width;val height=root.layoutParams.height
        val prefs=a.getPreferences(0);val old=prefs.getString("theme","system");var n=0
        try{
            for(mode in listOf("light","dark"))for(scale in listOf(1f,2f))for(dp in listOf(320,600,1024)){
                main{check(prefs.edit().putString("theme",mode).commit());@Suppress("DEPRECATION") a.resources.updateConfiguration(Configuration(config).apply{fontScale=scale},a.resources.displayMetrics);root.layoutParams=root.layoutParams.apply{this.width=(dp*a.resources.displayMetrics.density).toInt()};root.requestLayout()};inst.waitForIdleSync()
                click(a,"menu")
                verify(menu(a).map{it.first}==menuKeys,"CP menu invariance $mode $scale ${dp}dp")
                click(a,"vehicle_settings");inst.waitForIdleSync()
                main{nodes(root).filter{it.visibility==View.VISIBLE}.forEach{v->check(v.width<=root.width);if(v is Button)check(v.height>=76*a.resources.displayMetrics.density);if(v is TextView&&v.layout!=null)for(i in 0 until v.layout.lineCount)check(v.layout.getEllipsisCount(i)==0)}}
                n++
                if(dp==1024&&scale==1f)screenshot("settings-$mode")
            }
            verify(n==12,"CP settings layout: 320/600/1024dp 100/200 percent two themes without ellipsis or small buttons")
        }finally{main{check(prefs.edit().putString("theme",old).commit());@Suppress("DEPRECATION") a.resources.updateConfiguration(config,a.resources.displayMetrics);root.layoutParams=root.layoutParams.apply{this.width=width;this.height=height};root.requestLayout()};inst.waitForIdleSync()}
    }
    fun cold():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val settings=VehicleDisplaySettings(c.getSharedPreferences("caritda_vehicle_display",0));val p=c.getSharedPreferences("compat_cold",0)
        val repo=SqliteIssueRepository(c);val obs=ObservationStore(c)
        verify(settings.layout==PowertrainLayout.HEV&&settings.deviceTime,"CP manual layout and time preference survive force-stop")
        verify(repo.draft()?.id==p.getString("draft",null)&&repo.draft()?.body==p.getString("body",null),"CP original authored draft survives force-stop after menu and settings")
        verify(obs.refs().size==p.getInt("observations",-1),"CP persisted settings and draft restore does not fabricate observations")
        repo.close();obs.close();return lines
    }
}
