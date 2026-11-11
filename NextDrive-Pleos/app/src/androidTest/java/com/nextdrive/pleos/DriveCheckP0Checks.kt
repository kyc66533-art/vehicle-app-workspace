package com.nextdrive.pleos

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.widget.*
import android.view.View
import android.view.ViewGroup
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.safety.*
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** All writers select unique validation stores. Backend fixtures never call platform services. */
class DriveCheckP0Checks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,name:String){check(ok){name};lines.add("PASS $name")}
    private fun <T> main(block:()->T):T{var value:T?=null;var error:Throwable?=null;inst.runOnMainSync{try{value=block()}catch(t:Throwable){error=t}};error?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    private fun await(block:()->Boolean){val end=System.currentTimeMillis()+14000;while(System.currentTimeMillis()<end){if(main(block))return;Thread.sleep(50)};error("DRIVECHECK_UI_TIMEOUT")}
    private class Navi:NavigationBackend{
        lateinit var events:NavigationBackend.Events;var routing=false;var mode=NaviMode.SAFE_GUIDING;var terms=true;var requests=0;var released=0;var queries=0
        override fun initialize(events:NavigationBackend.Events){this.events=events;events.connected()}
        override fun checkState()=events.state(routing,terms,mode)
        override fun request(target:NavigationTarget){requests++}
        override fun checkDestination(){queries++}
        override fun release(){released++}
    }
    private class Location:LocationBackend{
        var supported=true;var reads=0;var released=0;lateinit var result:(IssueLocation?,String)->Unit
        override fun supported()=supported
        override fun read(done:(IssueLocation?,String)->Unit){reads++;result=done}
        override fun release(){released++}
    }
    fun core():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val target=NavigationTarget(37.48544722,127.03636666,"공식 좌표 fixture")
        verify(target.valid()&&!target.copy(latitude=Double.NaN).valid()&&!target.copy(longitude=181.0).valid()&&!target.copy(name=" ").valid(),"P001 invalid destination coordinates/name rejected")
        var factoryCalls=0
        val restricted=main{InspectionNavigation(c,{factoryCalls++;Navi()},100){false}}
        main{restricted.request(target.latitude,target.longitude,target.name){error("RESTRICTED_CALLBACK")}}
        verify(factoryCalls==0&&!restricted.busy,"P002 restricted UI never creates SDK or sends a destination")
        fun navigation(fake:Navi,timeout:Long=3000,report:(String)->Unit={}):InspectionNavigation=
            main{InspectionNavigation(c,{fake},timeout){true}.also{it.request(target.latitude,target.longitude,target.name,report)}}
        val existing=Navi().apply{routing=true};val old=navigation(existing);await{!old.busy}
        verify(existing.requests==0&&existing.released==1&&old.events.last().code=="NAVI_EXISTING_ROUTE","P003 existing route is never replaced or cancelled")
        val terms=Navi().apply{this.terms=false};val noTerms=navigation(terms);await{!noTerms.busy}
        verify(terms.requests==0&&noTerms.events.last().code=="NAVI_TERMS_REQUIRED","P004 missing navigation terms block the request")
        val fake=Navi();val nav=navigation(fake);await{fake.requests==1}
        main{nav.request(target.latitude,target.longitude,target.name){error("DUPLICATE_REQUEST")}}
        verify(fake.requests==1&&nav.busy,"P005 repeated tap while pending has one destination dispatch")
        main{fake.events.searchStarted()};await{fake.queries==1}
        verify(nav.busy&&!nav.guidanceConfirmed&&nav.events.any{it.code=="NAVI_ROUTE_SEARCH_STARTED"},"P006 onRouteStarted is search-start, never guidance success")
        main{fake.events.searchCompleted();fake.events.destination(target.latitude,target.longitude)};await{nav.destinationVerified}
        verify(nav.busy&&!nav.guidanceConfirmed,"P007 matching destination and search completion still require actual guidance state")
        main{fake.events.navigation(NavigationStatus.FREE_DRIVE)};Thread.sleep(80)
        verify(nav.busy&&!nav.guidanceConfirmed,"P008 map-only FREE_DRIVE never becomes guidance")
        main{fake.mode=NaviMode.ROUTE_GUIDING;fake.events.navigation(NavigationStatus.ROUTE_DRIVE)};await{!nav.busy}
        verify(nav.guidanceConfirmed&&fake.released==1&&nav.events.last().code=="NAVI_GUIDANCE_ACTIVE","P009 matching destination plus ROUTE_DRIVE confirms guidance and releases listener")
        val eventCount=nav.events.size;main{fake.events.searchStarted();fake.events.cancelled()};Thread.sleep(80)
        verify(nav.events.size==eventCount,"P010 late callbacks cannot change a completed navigation result")
        val wrong=Navi();val mismatch=navigation(wrong);await{wrong.requests==1};main{wrong.events.destinationChanged()};await{wrong.queries==1};main{wrong.events.destination(0.0,0.0)};await{!mismatch.busy}
        verify(!mismatch.guidanceConfirmed&&mismatch.events.last().code=="NAVI_DESTINATION_MISMATCH","P011 unrelated destination cannot be attributed to this request")
        val timed=Navi();val timeout=navigation(timed,120);await{!timeout.busy}
        verify(timed.released==1&&timeout.events.last().code=="NAVI_TIMEOUT"&&!timeout.guidanceConfirmed,"P012 timeout is terminal failure and releases resources")
        val cancelled=Navi();val cancelledNav=navigation(cancelled);await{cancelled.requests==1};main{cancelledNav.cancel();cancelled.events.navigation(NavigationStatus.ROUTE_DRIVE)};Thread.sleep(80)
        verify(cancelled.released==1&&!cancelledNav.busy&&cancelledNav.events.last().code=="NAVI_MONITOR_CANCELLED","P013 user stops monitoring only; backend offers no cancelRoute capability")
        val error=Navi();val errored=navigation(error);await{error.requests==1};main{error.events.failed("NAVI_ERROR_CAUSE_SEARCH_ROUTE")};await{!errored.busy}
        verify(error.released==1&&errored.events.last().code=="NAVI_ERROR_CAUSE_SEARCH_ROUTE","P014 provider failure stays distinct from transport submission")
        val denied=object:ContextWrapper(c){override fun checkSelfPermission(permission:String)=if(permission=="pleos.car.permission.NAVI_ROUTE_SEARCH")PackageManager.PERMISSION_DENIED else super.checkSelfPermission(permission)}
        main{InspectionNavigation(denied,{factoryCalls++;Navi()},100){true}.request(target.latitude,target.longitude,target.name){}}
        verify(factoryCalls==0,"P015 denied permission rejected without changing actual device permissions")
        val activeMode=Navi().apply{mode=NaviMode.ROUTE_GUIDING};val modeGuard=navigation(activeMode);await{!modeGuard.busy}
        verify(activeMode.requests==0&&modeGuard.events.last().code=="NAVI_EXISTING_ROUTE","P034 active guidance mode is protected even when isRouting is false")

        val now=System.currentTimeMillis();val point=IssueLocation(37.0,127.0,now-1000,now,"VALIDATION_FIXTURE")
        verify(point.valid()&&!point.copy(latitude=91.0).valid()&&!point.copy(receivedAt=0).valid()&&!point.copy(measuredAt=now+61000).valid(),"P016 location validity includes both clocks and coordinate bounds")
        verify(LocationPresentation.freshness(point,now).contains("최근")&&LocationPresentation.freshness(point.copy(measuredAt=now-600000),now).contains("오래된")&&LocationPresentation.freshness(point.copy(measuredAt=now+1),now).contains("시각"),"P017 fresh, stale and future-clock location are separate")
        val storeName="drivecheck-location-${UUID.randomUUID()}.db"
        IssueLocationStore(c,storeName).use{store->
            verify(store.save("issue",point)&&!store.save("issue",point)&&store.count("issue")==1,"P018 repeated confirmation stores one exact location")
            store.save("issue",point.copy(measuredAt=now-700000,receivedAt=now+1))
            verify(store.count("issue")==2&&store.history("issue").contains(point)&&store.count("other")==0,"P019 append-only history preserves older point and issue isolation")
        }
        IssueLocationStore(c,storeName).use{verify(it.count("issue")==2&&it.history("issue").last()==point,"P020 both original coordinates/timestamps restore after database reopen")}
        val locationContext=object:ContextWrapper(c){override fun checkSelfPermission(permission:String)=if(permission=="pleos.car.permission.FUSED_LOCATION")PackageManager.PERMISSION_GRANTED else super.checkSelfPermission(permission)}
        val location=Location();var deliveries=0
        val reader=main{OneShotLocation(locationContext,{location}){true}.also{it.read{_,_->deliveries++};it.read{_,_->deliveries++}}}
        verify(location.reads==1,"P021 one-shot duplicate request never opens a second provider")
        main{reader.cancel();location.result(point,"LOCATION_RECEIVED")};Thread.sleep(80)
        verify(deliveries==1&&reader.lastCode=="LOCATION_CANCELLED"&&location.released==1,"P022 cancelled location ignores late success and releases provider")
        val unsupported=Location().apply{supported=false};var code=""
        val missing=main{OneShotLocation(locationContext,{unsupported}){true}.also{it.read{_,result->code=result}}};await{!missing.busy}
        verify(code=="LOCATION_SERVICE_UNAVAILABLE"&&unsupported.reads==0&&unsupported.released==1,"P023 unsupported location is not a timeout or permission failure")
        val noLocationPermission=object:ContextWrapper(c){override fun checkSelfPermission(permission:String)=PackageManager.PERMISSION_DENIED}
        main{OneShotLocation(noLocationPermission,{error("MUST_NOT_CREATE")}){true}.read{_,result->code=result}}
        verify(code=="LOCATION_PERMISSION_DENIED","P024 denied location permission never opens a provider")
        val silent=Location()
        val waiting=main{OneShotLocation(locationContext,{silent}){true}.also{it.read{_,result->code=result}}};await{!waiting.busy}
        verify(code=="LOCATION_TIMEOUT"&&silent.released==1,"P033 location timeout remains failure and releases the waiting provider")
        return lines
    }

    fun locationUi():List<String>{
        ValidationEnvironment.requireIsolated(c)
        check(c.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")==PackageManager.PERMISSION_GRANTED){"LOCATION_PERMISSION_REQUIRED"}
        val prefix="gleo-ui-"+UUID.randomUUID();val db=prefix+".db"
        val (author,issue)=SqliteIssueRepository(c,db).use{repo->val service=CaritdaService(repo,{true});val author=service.registerDriver("위치검증");service.selectDriver(author);author to service.record(SymptomType.OTHER,"위치를 연결할 사용자 기록 fixture",System.currentTimeMillis(),"")}
        var a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        fun click(tag:String){if(BLightTestNavigation.prepare(inst,a,tag))return;await{a.window.decorView.findViewWithTag<Button>(tag)?.isEnabled==true};main{check(a.window.decorView.findViewWithTag<Button>(tag).performClick())}}
        fun dialog():AlertDialog=a.javaClass.getDeclaredField("activeDialog").apply{isAccessible=true}.get(a) as AlertDialog
        fun confirm(yes:Boolean){await{dialog().isShowing};main{dialog().getButton(if(yes)AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick()}}
        try{
            click("driver_${author.id}");click("start_driving");click("all_issues");click("issue_${issue.id}")
            val fake=Location();val reader=main{OneShotLocation(a,{fake}){true}}
            main{a.javaClass.getDeclaredField("locationReader\$delegate").apply{isAccessible=true}.set(a,lazy{reader})}
            val now=System.currentTimeMillis();val point=IssueLocation(37.0,127.0,now-700000,now,"VALIDATION_FIXTURE")
            click("attach_location");confirm(true);await{fake.reads==1};main{fake.result(point,"LOCATION_RECEIVED")};await{!reader.busy};confirm(false)
            IssueLocationStore(c,prefix+"-locations.db").use{verify(it.count(issue.id)==0,"P025 cancel at actual confirmation View never stores location")}
            click("attach_location");confirm(true);await{fake.reads==2};main{fake.result(point,"LOCATION_RECEIVED")};await{!reader.busy};confirm(true)
            IssueLocationStore(c,prefix+"-locations.db").use{verify(it.contains(issue.id,point)&&it.count(issue.id)==1,"P026 actual consent View links a stale point only after explicit confirmation")}
            click("attach_location");confirm(true);await{fake.reads==3};main{fake.result(point.copy(receivedAt=now+1),"LOCATION_RECEIVED")};await{!reader.busy};confirm(true)
            IssueLocationStore(c,prefix+"-locations.db").use{verify(it.count(issue.id)==2&&it.history(issue.id).contains(point),"P027 new attachment preserves all previous location evidence")}
            val monitor=inst.addMonitor(MainActivity::class.java.name,null,false);main{a.recreate()};a=(inst.waitForMonitorWithTimeout(monitor,14000) as? MainActivity)?:error("RECREATION_MISSING");inst.removeMonitor(monitor)
            await{a.window.decorView.findViewWithTag<View>("linked_location")!=null}
            verify(main{a.window.decorView.findViewWithTag<View>("linked_location")!=null},"P028 recreated issue View restores linked location independently of issue fields")
            SqliteIssueRepository(c,db).use{verify(it.issue(issue.id)==issue&&it.events(issue.id).size==1,"P029 location flow never rewrites symptom, author or manual status/history")}
            click("location_history")
            verify(main{a.window.decorView.findViewWithTag<View>("location_history_item")!=null},"P030 previous linked points remain accessible in the original issue screen")
            check(c.getSharedPreferences("drivecheck-location-cold-pointer",0).edit().putString("issueDb",db).putString("locationDb",prefix+"-locations.db").putString("issue",issue.id).commit())
        }finally{main{a.finish()}}
        return lines
    }
    fun navigationUi():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val prefix="gleo-ui-"+UUID.randomUUID();val db=prefix+".db"
        val (author,issue)=SqliteIssueRepository(c,db).use{repo->val service=CaritdaService(repo,{true});val driver=service.registerDriver("경로검증");service.selectDriver(driver);driver to service.record(SymptomType.OTHER,"사용자가 확인한 정비 장소 fixture",System.currentTimeMillis(),"")}
        var a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        fun click(tag:String){if(BLightTestNavigation.prepare(inst,a,tag))return;await{a.window.decorView.findViewWithTag<Button>(tag)?.isEnabled==true};main{a.window.decorView.findViewWithTag<Button>(tag).performClick()}}
        fun dialog()=a.javaClass.getDeclaredField("activeDialog").apply{isAccessible=true}.get(a) as AlertDialog
        fun text(v:View):String=when(v){is TextView->v.text.toString();is ViewGroup->(0 until v.childCount).joinToString(" "){text(v.getChildAt(it))};else->""}
        val fake=Navi();var created=0
        val nav=main{InspectionNavigation(a,{created++;fake},30000){true}}
        try{
            click("driver_${author.id}");click("start_driving");click("all_issues");click("issue_${issue.id}");click("issue_summary")
            main{a.javaClass.getDeclaredField("navigation\$delegate").apply{isAccessible=true}.set(a,lazy{nav})}
            click("inspection_navigation")
            main{dialog().window!!.decorView.findViewWithTag<EditText>("navigation_name").setText("직접 확인한 장소");dialog().window!!.decorView.findViewWithTag<EditText>("navigation_latitude").setText("NaN");dialog().window!!.decorView.findViewWithTag<EditText>("navigation_longitude").setText("127.0");dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()}
            verify(main{dialog().isShowing}&&created==0,"P035 invalid destination stays editable without constructing or dispatching an SDK request")
            main{dialog().window!!.decorView.findViewWithTag<EditText>("navigation_latitude").setText("37.0");dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()};await{fake.requests==1}
            verify(main{a.window.decorView.findViewWithTag<Button>("inspection_navigation")?.isEnabled==false},"P036 actual pending View blocks a duplicate destination request")
            main{fake.events.searchStarted()};await{fake.queries==1}
            verify(main{a.window.decorView.findViewWithTag<View>("navigation_progress")?.let{text(it).contains("경로 탐색이 시작")}==true}&&!nav.guidanceConfirmed,"P037 actual progress View does not label search start as guidance success")
            click("cancel_navigation_monitor")
            verify(!nav.busy&&fake.released==1&&nav.events.last().code=="NAVI_MONITOR_CANCELLED","P038 explicit monitoring cancel releases the listener without a route cancellation capability")
            click("inspection_navigation")
            verify(main{dialog().window!!.decorView.findViewWithTag<EditText>("navigation_latitude").text.toString()=="37.0"&&dialog().window!!.decorView.findViewWithTag<EditText>("navigation_name").text.toString()=="직접 확인한 장소"},"P039 cancelled destination monitoring retains the user's typed destination")
            val monitor=inst.addMonitor(MainActivity::class.java.name,null,false);main{a.recreate()};a=(inst.waitForMonitorWithTimeout(monitor,14000) as? MainActivity)?:error("NAVIGATION_RECREATION_MISSING");inst.removeMonitor(monitor)
            click("inspection_navigation")
            verify(main{dialog().window!!.decorView.findViewWithTag<EditText>("navigation_latitude").text.toString()=="37.0"&&a.window.decorView.findViewWithTag<View>("navigation_progress")!=null},"P040 configuration restore keeps destination edits and the truthful request timeline")
            SqliteIssueRepository(c,db).use{verify(it.issue(issue.id)==issue&&it.events(issue.id).size==1,"P041 navigation never rewrites the issue, author or manual history")}
        }finally{main{a.finish();nav.stop()}}
        return lines
    }
    fun coldLocation():List<String>{
        ValidationEnvironment.requireIsolated(c);val pointer=c.getSharedPreferences("drivecheck-location-cold-pointer",0)
        val db=pointer.getString("issueDb",null)?:error("RUN_LOCATION_UI_FIRST");check(Regex("gleo-ui-[a-f0-9-]{36}\\.db").matches(db));val id=pointer.getString("issue",null)!!
        IssueLocationStore(c,pointer.getString("locationDb",null)!!).use{verify(it.count(id)==2&&it.history(id).all{point->point.valid()},"P031 append-only location history survives actual process restart")}
        SqliteIssueRepository(c,db).use{verify(it.issue(id)!=null&&it.events(id).size==1,"P032 location cold restore preserves original journal history")}
        return lines
    }
}
