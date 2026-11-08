package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.ui.*
import com.nextdrive.pleos.vehicle.*
import java.util.UUID

/** UI automation and isolated presentation fixtures; no fake readings enter the application DB. */
class OnboardingChecks(private val instrumentation:Instrumentation) {
    private val context=instrumentation.targetContext
    private val results=mutableListOf<String>()
    private fun verify(condition:Boolean,message:String){check(condition){message};results.add("PASS $message")}
    private fun <T> main(action:()->T):T {var value:T?=null;instrumentation.runOnMainSync{value=action()};@Suppress("UNCHECKED_CAST") return value as T}
    private fun await(message:String,condition:()->Boolean){val end=System.currentTimeMillis()+25_000;while(System.currentTimeMillis()<end){if(condition())return;Thread.sleep(80)};error(message)}
    private fun launch()=instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN).apply{setClassName(context,MainActivity::class.java.name);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}) as MainActivity
    private fun finish(activity:MainActivity){main{activity.finish()};instrumentation.waitForIdleSync()}
    private fun tagged(a:MainActivity,tag:String):View?=a.window.decorView.findViewWithTag(tag)
    private fun click(a:MainActivity,tag:String){main{val view=tagged(a,tag)?:error("Missing $tag");check(view.isEnabled){"Disabled $tag"};check(view.performClick())};instrumentation.waitForIdleSync()}
    private fun text(a:MainActivity):String=main{
        fun collect(v:View):String=if(v is ViewGroup)(0 until v.childCount).joinToString("\n"){collect(v.getChildAt(it))} else if(v is TextView && v.visibility==View.VISIBLE)v.text.toString() else ""
        collect(a.window.decorView)
    }
    private fun rows(db:SQLiteDatabase,table:String)=db.rawQuery("SELECT * FROM $table ORDER BY 1",null).use{c->buildList{while(c.moveToNext())add((0 until c.columnCount).map{if(c.isNull(it))null else c.getString(it)})}}
    private fun snapshot(repo:SqliteIssueRepository)=listOf("drivers","issues","events","settings","drafts").associateWith{rows(repo.readableDatabase,it)}
    private fun navigate(a:MainActivity,target:String){if(main{tagged(a,"nav_$target")==null})click(a,"menu");click(a,"nav_$target")}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(context)
        val repo=SqliteIssueRepository(context);val obs=ObservationStore(context)
        val baseline=snapshot(repo);val oldObservations=rows(obs.readableDatabase,"observations")
        val prefs=context.getSharedPreferences("caritda_help",Context.MODE_PRIVATE)
        val saved=prefs.all.toMap()
        var activity:MainActivity?=null
        try {
            policyFixtures();presentationFixtures()
            // Simulate an interrupted first offer, without reinstalling/clearing the user's app.
            check(prefs.edit().putInt("version",1).putString("outcome","pending").commit())
            var a=launch();activity=a
            await("first guide input permitted"){main{tagged(a,"guide_skip")?.isEnabled==true}}
            verify(text(a).contains("내 차 기록을 한곳에서") && text(a).contains("지금은 차량의 이상을 자동으로 찾아주지는 않아요."),"ON first guide is one page with exact purpose and current limitation")
            val oldSkip=main{tagged(a,"guide_skip")!!}
            main{instrumentation.callActivityOnStop(a);oldSkip.performClick()}
            verify(prefs.getString("outcome",null)=="pending" && snapshot(repo)==baseline,"ON UNKNOWN lifecycle cannot complete guide or change records")
            main{instrumentation.callActivityOnStart(a)}
            await("guide reconnects without auto completion"){main{tagged(a,"guide_skip")?.isEnabled==true}}
            click(a,"guide_skip")
            verify(prefs.getString("outcome",null)=="skipped" && snapshot(repo)==baseline && rows(obs.readableDatabase,"observations")==oldObservations,"ON skip changes only guide preference, no driver confirmation event or SDK read")
            verify(main{tagged(a,"start_driving")!=null && tagged(a,"new_issue")==null},"ON skip still requires explicit driver start")
            finish(a);a=launch();activity=a
            await("existing route after skip"){main{tagged(a,"change_driver")?.isEnabled==true}}
            verify(main{tagged(a,"welcome_title")==null},"ON skipped guide is not repeated on relaunch")
            click(a,"help");click(a,"replay_guide")
            verify(main{tagged(a,"close_guide")!=null && tagged(a,"guide_skip")==null},"ON replay offers close and does not repeat setup actions")
            click(a,"close_guide");click(a,"close_help")
            verify(prefs.getString("outcome",null)=="skipped" && snapshot(repo)==baseline,"ON replay and help close preserve preference and original driver screen")
            finish(a)
            check(prefs.edit().putString("outcome","pending").commit())
            a=launch();activity=a;await("guide choose ready"){main{tagged(a,"guide_choose")?.isEnabled==true}}
            click(a,"guide_choose")
            verify(prefs.getString("outcome",null)=="completed" && snapshot(repo)==baseline,"ON choose completes explanation only, no automatic driver selection")
            val driver=repo.currentDriver()?:repo.drivers().first()
            click(a,"driver_${driver.id}");click(a,"start_driving")
            verify(text(a).contains("지금은 차량의 이상을 자동으로 찾아주지는 않아요.") && !text(a).contains("Capability"),"ON home states actual scope and hides engineering details")
            click(a,"all_issues");click(a,"filter_COMPLETED");navigate(a,"HOME");click(a,"all_issues")
            verify(main{(tagged(a,"filter_ALL") as Button).text.startsWith("✓ ")},"ON home record action opens all records instead of hiding a previous selection")
            navigate(a,"HOME")
            click(a,"vehicle_data")
            val basic=text(a)
            verify(listOf("SDK","Capability","RECEIVED","UNSUPPORTED","세션 ID","ERROR_").none{basic.contains(it)} && basic.contains("배터리 충전량") && basic.contains("누적 주행거리"),"ON basic vehicle page separates technical codes and enlarges named values")
            await("entry automatic read completes before manual request"){main{tagged(a,"read_vehicle")?.isEnabled==true}}
            click(a,"read_vehicle")
            verify(main{tagged(a,"read_vehicle")?.isEnabled==false},"ON busy read disables repeated request")
            await("vehicle read completed"){main{tagged(a,"read_vehicle")?.isEnabled==true}}
            val soc=obs.mostRecent("EV_SOC",true)?:error("SOC missing")
            val distance=obs.mostRecent("ODOMETER",true)?:error("odometer missing")
            val now=System.currentTimeMillis()
            verify(text(a).contains(VehiclePresentation.card("EV_SOC",soc,soc,now).value) && text(a).contains(VehiclePresentation.card("ODOMETER",distance,distance,now).value) && text(a).contains("에뮬레이터에서 받은 값"),"ON live emulator SOC and odometer remain exactly sourced")
            click(a,"unavailable")
            verify(text(a).contains("연료 잔량") && text(a).contains("확인할 수 없어요"),"ON unsupported fuel is explained without zero percent")
            click(a,"load_details")
            verify(text(a).contains("SDK 2.3.2") && text(a).contains("Capability") && text(a).contains("세션 ID") && text(a).contains("ERROR_NOT_SUPPORTED_PROPERTY"),"ON loading details retain SDK capability original error and session")
            click(a,"back")
            val received=rows(obs.readableDatabase,"observations")
            Thread.sleep(1300)
            verify(rows(obs.readableDatabase,"observations")==received,"ON display freshness refresh performs no SDK polling")
            navigate(a,"HOME")
            val pending=repo.draft()
            if(pending==null){
                click(a,"new_issue");main{(tagged(a,"situation") as EditText).setText("사용법 복귀 검증용 초안")}
                val draft=repo.draft()!!
                click(a,"help");click(a,"help_topic_2");click(a,"close_help")
                verify(repo.draft()==draft && main{(tagged(a,"situation") as EditText).text.toString()}==draft.body,"ON contextual help returns to the exact authored draft without new writes")
                click(a,"discard_draft")
                await("cancel dialog"){instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("확인")?.any{it.isClickable}==true}
                val node=instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("확인").first{it.isClickable}
                check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync()
            } else results.add("PARTIAL existing user draft was preserved; new scratch draft test skipped")
            click(a,"help")
            val content=text(a)
            verify(listOf("기록할 사람 고르기","차량값 불러보기","느낀 문제 남기기","다른 사람의 기록 읽기","정비할 때 기록 보여주기","자동 확인 기능의 현재 범위").all{content.contains(it)},"ON help contains six task-based entries and replay")
            click(a,"help_topic_5")
            verify(text(a).contains("자동으로 찾아주지는 않아요") && text(a).contains("정상이라는 뜻은 아니에요"),"ON help does not promise automatic diagnosis or normality")
            click(a,"close_help")
            layoutMatrix(a)
            verify(snapshot(repo)==baseline && rows(obs.readableDatabase,"observations").containsAll(oldObservations),"ON all original profile IDs issue history observations and drafts preserved")
            finish(a);activity=null
        }finally{
            activity?.let{finish(it)}
            val editor=prefs.edit().clear();saved.forEach{(key,value)->when(value){is String->editor.putString(key,value);is Int->editor.putInt(key,value);is Boolean->editor.putBoolean(key,value)}};check(editor.commit())
            repo.close();obs.close()
        }
        return results
    }
    private fun policyFixtures(){
        val p=context.getSharedPreferences("guide-test-${UUID.randomUUID()}",Context.MODE_PRIVATE);val state=GuideState(p)
        verify(state.prepare(false),"ON new-install policy offers pending explanation")
        verify(runCatching{state.finish(true,false)}.isFailure && p.getString("outcome",null)=="pending","ON policy refuses completion without input allowance")
        state.finish(false,true)
        verify(!state.prepare(false),"ON completed policy never resets on later prepares")
        val existing=GuideState(context.getSharedPreferences("guide-existing-${UUID.randomUUID()}",Context.MODE_PRIVATE))
        verify(!existing.prepare(true),"ON upgrade policy does not interrupt existing installations")
    }
    private fun presentationFixtures(){
        val now=System.currentTimeMillis();val name="on-projection-${UUID.randomUUID()}.db";val fixture=ObservationStore(context,name)
        val receipt=VehicleObservation("ODOMETER","0.0","km",now-1000,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"FIXTURE_ONLY",ObservationStatus.RECEIVED,"Isolated presentation fixture, not a live SDK result")
        fixture.append(receipt)
        repeat(105){fixture.append(receipt.copy(raw=null,unit=null,receivedAt=null,valid=false,status=ObservationStatus.CONNECTION_FAILED,recordedAt=now+it))}
        val previous=VehiclePresentation.card("ODOMETER",fixture.mostRecent("ODOMETER"),fixture.mostRecent("ODOMETER",true),now)
        verify(previous.value.contains("이전 값") && previous.value.contains("0 km") && previous.attempt.contains("응답을 받지 못했어요") && previous.time.contains("마지막 수신"),"ON fixture separates last failure from historical valid zero after 105 failures")
        val large=receipt.copy(raw="1000000.0")
        verify(VehiclePresentation.card("ODOMETER",large,large,now).value=="1,000,000 km" && fixture.mostRecent("ODOMETER",true)?.raw=="0.0","ON numeric grouping changes display only and preserves original stored raw value")
        verify(VehiclePresentation.card("EV_SOC",null,null,now).value.contains("아직 불러온 값이 없어요"),"ON fixture missing value is not rendered as zero")
        val percent=receipt.copy(kind="EV_SOC",raw="15",unit="%")
        val card=VehiclePresentation.card("EV_SOC",percent,percent,now)
        verify(card.value=="15 %" && !card.attempt.contains("고장") && !card.attempt.contains("정차"),"ON fixture SOC15 does not generate a fault or stop warning")
        verify(!VehiclePresentation.card("ODOMETER",receipt,receipt,now+598_999).stale && VehiclePresentation.card("ODOMETER",receipt,receipt,now+599_000).stale,"ON fixture evaluates exact ten-minute boundary")
        verify(VehiclePresentation.card("ODOMETER",receipt,receipt.copy(receivedAt=now+120_000),now).time.contains("시각을 확인"),"ON fixture future clock does not appear fresh")
        verify(VehiclePresentation.card("ODOMETER",receipt,receipt.copy(source=ObservationSource.TEST_ONLY),now).value.contains("아직"),"ON fixture TEST_ONLY values cannot be presented as vehicle receipts")
        fixture.close()
    }
    private fun layoutMatrix(a:MainActivity){
        val config=Configuration(a.resources.configuration)
        val root=main{(a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)}
        val originalWidth=main{root.layoutParams.width}
        val theme=a.getPreferences(Context.MODE_PRIVATE);val oldTheme=theme.getString("theme","system")
        try {
            for(mode in listOf("light","dark"))for(scale in listOf(1f,1.3f,1.5f,2f))for(width in listOf(320,480,600,840,1024)){
                main{
                    check(theme.edit().putString("theme",mode).commit())
                    @Suppress("DEPRECATION")
                    a.resources.updateConfiguration(Configuration(config).apply{fontScale=scale},a.resources.displayMetrics)
                    root.layoutParams=root.layoutParams.apply{this.width=(width*a.resources.displayMetrics.density).toInt()};root.requestLayout()
                }
                instrumentation.waitForIdleSync()
                navigate(a,"HOME")
                inspectLayout(a)
                navigate(a,"ISSUES");inspectLayout(a)
                navigate(a,"VEHICLE");inspectLayout(a)
                click(a,"change_driver");inspectLayout(a)
                click(a,"help");inspectLayout(a);click(a,"close_help");click(a,"cancel_driver")
            }
            verify(true,"ON 200 rendered screen combinations: five pages x five widths x four font scales x two themes")
        }finally{
            main{
                check(theme.edit().putString("theme",oldTheme).commit())
                @Suppress("DEPRECATION")
                a.resources.updateConfiguration(config,a.resources.displayMetrics)
                root.layoutParams=root.layoutParams.apply{width=originalWidth};root.requestLayout()
            }
        }
    }
    private fun inspectLayout(a:MainActivity){
        instrumentation.waitForIdleSync()
        main{
            val root=(a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
            fun inspect(view:View){
                if(view.visibility!=View.VISIBLE)return
                check(view.width<=root.width){"horizontal overflow ${view.tag}"}
                if(view is TextView && view.layout!=null){for(line in 0 until view.layout.lineCount)check(view.layout.getEllipsisCount(line)==0){"truncated ${view.tag}"}}
                if(view is Button)check(view.height>=76*a.resources.displayMetrics.density){"small touch target ${view.tag}"}
                if(view is ViewGroup)for(index in 0 until view.childCount)inspect(view.getChildAt(index))
            };inspect(root)
        }
    }
}
