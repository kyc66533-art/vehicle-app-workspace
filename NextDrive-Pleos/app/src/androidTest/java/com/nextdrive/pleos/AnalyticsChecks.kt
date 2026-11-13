package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nextdrive.pleos.analytics.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.ui.*
import org.json.JSONObject

class AnalyticsChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,s:String){check(ok){s};lines.add("PASS $s")}
    private fun <T> main(f:()->T):T{var v:T?=null;var err:Throwable?=null;inst.runOnMainSync{try{v=f()}catch(t:Throwable){err=t}};err?.let{throw it};@Suppress("UNCHECKED_CAST")return v as T}
    private fun await(s:String,seconds:Int=30,f:()->Boolean){val end=System.currentTimeMillis()+seconds*1000;while(System.currentTimeMillis()<end){if(f())return;Thread.sleep(100)};error(s)}
    private fun tag(a:MainActivity,k:String):View?=a.window.decorView.findViewWithTag(k)
    private fun click(a:MainActivity,k:String){
        // The B light UI has no hidden nav_*/menu Views: BLightTestNavigation follows the real visible buttons instead.
        val routed=BLightTestNavigation.prepare(inst,a,k);inst.waitForIdleSync()
        if(routed)return
        // The local JSON preview tool lives on the app-information (LOAD_DETAIL) screen inside a folded section: follow the real buttons there.
        if(k=="analysis_ai"&&main{tag(a,k)==null}){click(a,"nav_VEHICLE");click(a,"load_details");if(main{tag(a,"analysis_preview_tool")==null})click(a,"developer_info");if(main{tag(a,k)==null})click(a,"analysis_preview_tool")}
        main{val v=tag(a,k)?:error(k);check(v.isEnabled);v.performClick()};inst.waitForIdleSync()}
    private fun nodes(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{nodes(v.getChildAt(it))}else emptyList()
    private fun text(a:MainActivity)=main{nodes(a.window.decorView).filterIsInstance<TextView>().joinToString("\n"){it.text.toString()}}
    private fun launch()=inst.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(c,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun shot(name:String){inst.waitForIdleSync();inst.uiAutomation.takeScreenshot()?.let{b->c.openFileOutput("analytics-$name.png",0).use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
    private fun rows(db:SQLiteDatabase,t:String)=db.rawQuery("SELECT * FROM $t ORDER BY 1",null).use{r->buildList{while(r.moveToNext())add((0 until r.columnCount).map{if(r.isNull(it))null else r.getString(it)})}}
    private fun copy(name:String)=SQLiteDatabase.openDatabase(c.getFileStreamPath(name).path,null,SQLiteDatabase.OPEN_READONLY)
    fun inventory():List<String>{ValidationEnvironment.requireIsolated(c)
        copy("analytics-before-issues.db").use{db->lines.add("INVENTORY visible="+db.rawQuery("SELECT count(*) FROM issues WHERE deleted_at IS NULL",null).use{it.moveToFirst();it.getInt(0)})}
        val general=c.getFileStreamPath("analytics-general-observations.db")
        if(general.exists())SQLiteDatabase.openDatabase(general.path,null,SQLiteDatabase.OPEN_READONLY).use{db->db.rawQuery("SELECT id,raw,source,session,detail FROM observations WHERE kind='EV_SOC' ORDER BY id",null).use{r->while(r.moveToNext())lines.add("GENERAL_SOC "+(0 until r.columnCount).joinToString(","){r.getString(it).orEmpty()})}}
        for(prefix in listOf("general","validation")){
            val file=c.getFileStreamPath("analytics-$prefix-notices.db");if(!file.exists())continue
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use{db->
                lines.add("INVENTORY $prefix episodes="+rows(db,"episodes").size+" changes="+rows(db,"changes").size)
                db.rawQuery("SELECT id,entered,last_at,raw,phase,store_id,row_id FROM episodes ORDER BY last_at DESC",null).use{r->while(r.moveToNext())lines.add("NOTICE $prefix id=${r.getString(0)} entered=${r.getLong(1)} last=${r.getLong(2)} value=${r.getString(3)} phase=${r.getString(4)} ref=${r.getString(5)}:${r.getLong(6)}")}
                lines.add("CHANGES $prefix "+rows(db,"changes").joinToString(" | "){it.joinToString(",")})
                if(prefix=="validation")copy("analytics-validation-observations.db").use{raw->
                    val ids=db.rawQuery("SELECT row_id FROM changes ORDER BY id",null).use{r->buildList{while(r.moveToNext())add(r.getLong(0))}}
                    ids.forEach{id->raw.rawQuery("SELECT id,raw,received_at,source,status,session,detail FROM observations WHERE id=?",arrayOf(id.toString())).use{r->check(r.moveToFirst());lines.add("EVIDENCE "+(0 until r.columnCount).joinToString(","){r.getString(it)})}}
                }
            }
        };return lines
    }
    fun preservation():List<String>{ValidationEnvironment.requireIsolated(c)
        for(kind in listOf("issues","observations","snapshots","notices"))copy("analytics-before-$kind.db").use{before->copy("analytics-after-$kind.db").use{after->
            val tables=before.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='android_metadata'",null).use{r->buildList{while(r.moveToNext())add(r.getString(0))}}
            verify(before.version==after.version&&tables.all{t->if(kind=="issues"||kind=="notices")rows(before,t)==rows(after,t)else rows(after,t).containsAll(rows(before,t))},"PRESERVE all $kind rows/scalars and schema version")
            verify(after.rawQuery("PRAGMA integrity_check",null).use{it.moveToFirst();it.getString(0)=="ok"}&&after.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()},"PRESERVE $kind integrity and FK")
        }};return lines
    }
    fun run(contractsOnly:Boolean=false):List<String>{ValidationEnvironment.requireIsolated(c)
        val now=System.currentTimeMillis();var row=0L
        fun point(n:Double,at:Long=now-60000,session:String="SYNTHETIC_VALIDATION_ONLY")=ObservationRef("fixture-analytics-${c.packageName}",++row,VehicleObservation("EV_SOC",n.toString(),"%",at,ObservationSource.PLEOS_EMULATOR_SDK,true,true,session,ObservationStatus.RECEIVED,"SYNTHETIC validation fixture",now))
        val refs=listOf(point(80.0,now-50000),point(60.0,now-40000),point(18.0,now-30000),point(18.0,now-20000),point(45.0,now-10000))
        val a=VehicleAnalytics.analyze("EV_SOC",refs,now-86400000,now)
        verify(a.points.size==5&&a.delta==27.0&&a.values.min()==18.0&&a.values.max()==80.0&&a.values.average()==44.2,"AN A-C actual-time fixture statistics and +27 percentage points")
        verify(a.insights.any{it.kind=="CHANGE"&&it.delta==-20.0}&&a.insights.any{it.kind=="CHANGE"&&it.delta==-42.0},"AN A-B -20/-42 percentage-point changes have original references")
        verify(a.insights.any{it.kind=="TREND"}&&a.insights.any{it.kind=="LARGE_CHANGE"}&&a.insights.none{it.title.contains("고장")},"AN trends and exploratory changes do not diagnose failure")
        val repeat=VehicleAnalytics.analyze("EV_SOC",refs.take(3)+listOf(point(18.0,now-20000),point(18.0,now-10000)),0,now)
        verify(repeat.quality["같은 값 반복 수신"]==2&&repeat.insights.size==VehicleAnalytics.analyze("EV_SOC",refs.take(3),0,now).insights.size,"AN D raw repeated receipts never add duplicate change insights")
        val name="analytics-fixture-${java.util.UUID.randomUUID()}.db";AnalysisStore(c,name).use{db->db.remember(a);val n=db.count();repeat(3){db.remember(a)};verify(db.count()==n,"AN durable insight IDs make replay idempotent")}
        val bad=refs.last().copy(rowId=++row,value=refs.last().value.copy(raw="101"))
        val reversed=point(30.0,now-200000)
        val failed=refs.last().copy(rowId=++row,value=VehicleObservation("EV_SOC",null,null,null,ObservationSource.PLEOS_EMULATOR_SDK,false,false,"fixture",ObservationStatus.UNSUPPORTED,"fixture",now))
        val e=VehicleAnalytics.analyze("EV_SOC",refs+bad+failed+reversed+point(40.0,now+120000),0,now)
        verify(e.quality.containsKey("유효 범위·단위 오류")&&e.quality.containsKey("미지원")&&e.quality.containsKey("시각 오류")&&e.points.none{it.number==0.0},"AN E range unsupported and future errors never become zero")
        verify(VehicleAnalytics.analyze("EV_SOC",refs+reversed,0,now).quality["시간 역순"]==1,"AN E insertion-order regression is excluded instead of silently sorted")
        val split=VehicleAnalytics.analyze("EV_SOC",refs+point(50.0,now,"other-session"),0,now)
        verify(split.delta==null&&split.points.last().segment!=split.points[split.points.lastIndex-1].segment,"AN session boundaries break interpolation and deltas")
        verify(VehicleAnalytics.analyze("EV_SOC",refs,0,now,gaps=listOf(now-25000)).quality.containsKey("비교 불가·관측 공백"),"AN observation lifecycle gaps break graph segments")
        verify(VehicleAnalytics.analyze("EV_SOC",listOf(point(20.0,now-700000)),0,now).stale,"AN old history remains history and cannot become a fresh current value")
        verify(VehicleAnalytics.analyze("EV_SOC",listOf(refs.first().copy(value=refs.first().value.copy(source=ObservationSource.TEST_ONLY))),0,now).points.isEmpty(),"AN TEST_ONLY never enters SDK analysis")
        val actual=refs.last().copy(rowId=++row,value=refs.last().value.copy(source=ObservationSource.PLEOS_VEHICLE_SDK))
        verify(VehicleAnalytics.analyze("EV_SOC",refs+actual,0,now).points.size==1,"AN emulator and physical SDK environments are never pooled")
        val c1=SocChange("fixture-episode","USER_LOW_SOC",refs[2].storeId,refs[2].rowId,refs[2].value.receivedAt!!,"18",20,1)
        val c2=SocChange("fixture-episode","CONDITION_EXIT",refs[4].storeId,refs[4].rowId,refs[4].value.receivedAt!!,"45",20,1)
        val events=VehicleAnalytics.analyze("EV_SOC",refs,0,now,listOf(c1,c2))
        verify(events.events.size==2&&events.insights.count{it.kind=="USER_LOW_SOC"}==1&&events.insights.count{it.kind=="CONDITION_EXIT"}==1,"AN threshold insights reuse LowSocStore evidence, not a competing detector")
        val payload=AnalysisExplanation.payload(a);val consent=AnalysisConsent(payload)
        val response=JSONObject().put("origin","MODEL").put("evidence_digest",consent.digest).put("facts",JSONObject(payload)).put("explanation","최근 수신값을 차량 표시와 함께 확인해 주세요.").put("action","CHECK_VEHICLE_DISPLAY")
        verify(runCatching{AnalysisExplanation.validate(response.toString(),consent,payload)}.isFailure,"AI unconsented response rejected locally without network")
        consent.accept();verify(AnalysisExplanation.validate(response.toString(),consent,payload).isNotBlank(),"AI structured fixture can match exact approved evidence; not a model call")
        response.getJSONObject("facts").put("latest",999);verify(runCatching{AnalysisExplanation.validate(response.toString(),consent,payload)}.isFailure,"AI mismatched numeric facts rejected")
        response.put("facts",JSONObject(payload)).put("explanation","충전량은 999%예요.");verify(runCatching{AnalysisExplanation.validate(response.toString(),consent,payload)}.isFailure,"AI invented numeric prose rejected")
        response.put("explanation","최근 충전량은 80%예요.");verify(runCatching{AnalysisExplanation.validate(response.toString(),consent,payload)}.isFailure,"AI swapped but otherwise existing numbers rejected; numeric facts are rendered locally")
        response.put("explanation","수리가 완료되어 안전합니다.");verify(runCatching{AnalysisExplanation.validate(response.toString(),consent,payload)}.isFailure,"AI fabricated repair and safety decisions rejected")
        response.put("origin","MOCK");verify(runCatching{AnalysisExplanation.validate(response.toString(),consent,payload)}.isFailure,"AI Mock cannot impersonate a model response")
        consent.revoke();verify(!consent.permits(payload)&&!consent.permits(payload+" "),"AI cancellation revokes frozen-payload approval")
        verify(listOf("VIN","driver","latitude","store_id","row_id","situation").none{payload.contains(it)},"AI preview excludes personal and local database identifiers")
        for(dark in listOf(false,true)){val p=CaritdaPalette.of(dark);verify(contrast(p.text,p.background)>=4.5&&contrast(p.text,p.inset)>=4.5&&contrast(p.border,p.background)>=3&&contrast(p.border,p.inset)>=3,"UI ${if(dark)"dark"else"light"} normal text and button boundary contrast targets")}
        if(contractsOnly)return lines // independent fixture DB only; no default DB reset or SDK/UI entry
        val raw=ObservationStore(c);var activity=launch();await("parked UI"){main{tag(activity,"driver_A")?.isEnabled==true}};click(activity,"driver_A");click(activity,"start_driving");await("entry complete"){raw.latest(3).size==3}
        click(activity,"nav_VEHICLE");val count=raw.refs().size;click(activity,"soc_notices")
        verify(text(activity).contains("최근 충전량")&&main{tag(activity,"soc_current_value")!=null&&tag(activity,"soc_history")!=null},"UI latest actual SOC separate from collapsed historical episodes")
        click(activity,"back");click(activity,"vehicle_analytics");await("automatic worker report"){main{tag(activity,"analysis_statistics")!=null}}
        verify(main{tag(activity,"analysis_chart")?.contentDescription?.isNotBlank()==true}&&text(activity).contains("AI가 쓴 글이 아니에요"),"UI actual SDK chart has text alternative and honest local explanation")
        click(activity,"analysis_ODOMETER");await("ODO chart"){text(activity).contains("최근 수신 누적거리")};verify(text(activity).contains("0 km"),"UI real valid zero-distance is not missing data")
        click(activity,"analysis_EV_SOC");click(activity,"analysis_ai");verify(text(activity).contains("밖으로 보내지 않아요")&&main{tag(activity,"ai_payload_preview")!=null},"UI AI preview discloses payload and unavailable actual connection")
        click(activity,"ai_consent");click(activity,"ai_cancel");verify(raw.refs().size==count,"UI consent cancel and chart navigation never trigger SDK reads")
        shot("chart-light");main{activity.getPreferences(0).edit().putString("theme","dark").commit()};click(activity,"nav_VEHICLE");click(activity,"vehicle_analytics");await("dark chart"){main{tag(activity,"analysis_chart")!=null}};shot("chart-dark")
        matrix(activity)
        main{activity.finish()};raw.close();return lines
    }
    private fun matrix(a:MainActivity){
        val old=android.content.res.Configuration(a.resources.configuration);val root=main{(a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)};val width=root.layoutParams.width;val height=root.layoutParams.height;val prefs=a.getPreferences(0);val theme=prefs.getString("theme","system");var renders=0
        fun inspect(){inst.waitForIdleSync();main{val views=nodes(root).filter{it.visibility==View.VISIBLE};check(views.all{it.width<=root.width});views.filterIsInstance<Button>().forEach{check(it.height>=76*a.resources.displayMetrics.density)};views.filterIsInstance<TextView>().filter{it.layout!=null}.forEach{v->for(i in 0 until v.layout.lineCount)check(v.layout.getEllipsisCount(i)==0)};check((tag(a,"screen_scroll")?.height?:0)>=76*a.resources.displayMetrics.density)};renders++}
        try{for(mode in listOf("light","dark"))for(scale in listOf(1f,1.5f,2f))for(w in listOf(320,600,1024)){
            main{prefs.edit().putString("theme",mode).commit();@Suppress("DEPRECATION") a.resources.updateConfiguration(android.content.res.Configuration(old).apply{fontScale=scale},a.resources.displayMetrics);root.layoutParams=root.layoutParams.apply{this.width=(w*a.resources.displayMetrics.density).toInt();this.height=height};root.requestLayout()};inst.waitForIdleSync()
            click(a,"nav_HOME");click(a,"nav_VEHICLE");click(a,"soc_notices");inspect();click(a,"nav_HOME");click(a,"nav_VEHICLE");click(a,"vehicle_analytics");await("matrix chart"){main{tag(a,"analysis_chart")!=null}};inspect();click(a,"analysis_ai");inspect()
        };verify(renders==54,"UI 54 native SOC/chart/consent renders: 320/600/1024dp, 100/150/200 percent, both themes")
        }finally{main{prefs.edit().putString("theme",theme).commit();@Suppress("DEPRECATION") a.resources.updateConfiguration(old,a.resources.displayMetrics);root.layoutParams=root.layoutParams.apply{this.width=width;this.height=height};root.requestLayout()};inst.waitForIdleSync();click(a,"nav_HOME")}
    }
    private fun contrast(a:Int,b:Int):Double {fun l(c:Int):Double {fun f(n:Int):Double{val v=n/255.0;return if(v<=0.04045)v/12.92 else Math.pow((v+0.055)/1.055,2.4)};return .2126*f(Color.red(c))+.7152*f(Color.green(c))+.0722*f(Color.blue(c))};val x=l(a);val y=l(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05)}
    private fun stage(s:String){c.openFileOutput("analytics-live-stage.txt",0).use{it.write(s.toByteArray())}}
    fun live():List<String>{ValidationEnvironment.requireIsolated(c)
        val raw=ObservationStore(c);val soc=LowSocStore(c);val a=launch();await("parked actual live UI"){main{tag(a,"driver_A")?.isEnabled==true}};click(a,"driver_A");click(a,"start_driving");await("actual80"){raw.mostRecent("EV_SOC",true)?.raw=="80"}
        click(a,"nav_VEHICLE");click(a,"soc_notices");main{(tag(a,"soc_threshold") as EditText).setText("20")};click(a,"enable_soc");await("SOC get80"){raw.refs().any{it.value.detail.contains("SOC GET")&&it.value.raw=="80"}}
        click(a,"nav_VEHICLE");click(a,"vehicle_analytics");stage("READY_80")
        await("callback60",120){raw.refs().any{it.value.raw=="60"&&it.value.detail.contains("CALLBACK")}};await("graph60"){main{(tag(a,"value_analysis_latest") as? TextView)?.text?.contains("60 %")==true}}
        verify(main{(tag(a,"analysis_delta") as TextView).text.contains("-20")}&&soc.notices().isEmpty(),"LIVE A actual80 to60 updates graph delta -20 with no low event");stage("AT_60")
        await("callback18",120){soc.notices().singleOrNull()?.phase=="LOW"};await("graph18"){main{(tag(a,"value_analysis_latest") as TextView).text.contains("18 %")}}
        verify(main{(tag(a,"analysis_delta") as TextView).text.contains("-42")},"LIVE B actual60 to18 updates graph delta -42")
        verify(text(a).contains("설정한 기준"),"LIVE B evidence-linked low insight automatically appears")
        val id=soc.notices().single().id;stage("AT_18");Thread.sleep(1200)
        val before=AnalysisStore(c).use{it.count()};click(a,"nav_VEHICLE");click(a,"read_vehicle");await("repeat18 ready"){main{tag(a,"read_vehicle")?.isEnabled==true}}
        verify(soc.notices().single().id==id&&soc.changeCount()==1,"LIVE D repeated SDK18 keeps one low episode")
        click(a,"vehicle_analytics");await("repeated chart"){main{tag(a,"analysis_statistics")!=null}};verify(raw.mostRecent("EV_SOC",true)?.raw=="18","LIVE D repeated actual receipt retained as raw, not a new fault");stage("REPEAT_DONE")
        await("callback45",120){soc.notices().singleOrNull()?.phase=="EXITED"};await("graph45"){main{(tag(a,"value_analysis_latest") as TextView).text.contains("45 %")}}
        verify(soc.notices().single().id==id&&soc.changeCount()==2&&text(a).contains("해제"),"LIVE C actual45 updates same episode and automatic insight")
        verify(raw.refs().filter{it.value.raw in setOf("60","18","45")}.all{it.value.source==ObservationSource.PLEOS_EMULATOR_SDK},"LIVE all changed samples retain emulator SDK source")
        click(a,"nav_VEHICLE");click(a,"soc_notices");verify(main{(tag(a,"soc_current_value") as TextView).text.contains("45 %")},"LIVE current SOC is not replaced by old exit values")
        click(a,"soc_history");verify(text(a).contains("안내 진입")&&text(a).contains("조건 해제"),"LIVE one history card retains entry18 and exit45")
        c.getSharedPreferences("analytics_cold",0).edit().putString("episode",id).putInt("insights",AnalysisStore(c).use{it.count()}).commit();shot("live-exit");main{a.finish()};stage("STOPPED");raw.close();soc.close();return lines
    }
    fun cold():List<String>{ValidationEnvironment.requireIsolated(c);val p=c.getSharedPreferences("analytics_cold",0)
        LowSocStore(c).use{verify(it.notices().single().id==p.getString("episode",null)&&it.notices().single().phase=="EXITED","COLD SDK entry/exit episode persists")}
        AnalysisStore(c).use{verify(it.count()>=p.getInt("insights",1),"COLD automatic insight evidence persists after process stop")};return lines
    }
}
