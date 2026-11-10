package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.pm.PackageManager
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.safety.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Never prepares, clears, or seeds either application's default databases. */
class IntegrationChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T{var v:T?=null;var error:Throwable?=null;inst.runOnMainSync{try{v=block()}catch(t:Throwable){error=t}};error?.let{throw it};@Suppress("UNCHECKED_CAST")return v as T}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c);val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
        verify(VehicleHealth.labels.size==17,"IN01 bounded public health catalog")
        verify(!VehicleHealth.valid("ENGINE_OIL","ERROR")&&!VehicleHealth.valid("TIRE_LIMIT_FL",Double.NaN),"IN02 unknown/invalid states rejected")
        verify(!VehicleHealth.valid("LOW_BEAM","BROKEN")&&VehicleHealth.valid("LOW_BEAM","ON"),"IN03 lamp switch never converted into physical fault")
        verify(VehicleHealth.value("TIRE_LIMIT_FL","180").contains("실제 압력 아님"),"IN04 low tire threshold distinguished from measured pressure")
        verify(VehicleHealth.text("DOOR_FL",null,null).contains("아직"),"IN05 missing value never becomes closed/zero")
        val prior=VehicleObservation("DOOR_FL","0","position",now-900000,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"fixture",ObservationStatus.RECEIVED,"")
        val failed=VehicleObservation("DOOR_FL",null,null,null,ObservationSource.PLEOS_EMULATOR_SDK,true,false,"fixture",ObservationStatus.TIMEOUT,"")
        verify(VehicleHealth.text("DOOR_FL",failed,prior,now).contains("이전 수신값"),"IN06 current failure kept separate from old success")
        verify(VehicleHealth.text("DOOR_FL",prior,prior,now).contains("오래된"),"IN07 stale health clearly marked")
        verify(runCatching{CaritdaActions.topic("delete")}.isFailure,"IN08 unknown action rejected")
        verify(runCatching{CaritdaActions.draft("a".repeat(801))}.isFailure,"IN09 oversized voice draft rejected")
        verify(CaritdaActions.callback(CaritdaActions.QUERY,"asking","Please confirm locally.").getString("mode")=="silent","IN10 bounded callback schema; no implicit broadcast")
        val repoName="integration-$id.db";val obsName="integration-obs-$id.db"
        SqliteIssueRepository(c,repoName).use{repo->ObservationStore(c,obsName).use{obs->
            val service=CaritdaService(repo,{true});val author=service.registerDriver("통합작성");val next=service.registerDriver("통합인계");service.selectDriver(author)
            val issue=service.record(SymptomType.NOISE,"확인 후 저장하는 소음 fixture",now,"")
            verify(CaritdaActions.query("recent_issues",service,obs).contains(issue.situation),"IN11 local action reads existing repository")
            val events=service.events(issue.id).size
            verify(CaritdaActions.query("tires",service,obs).contains("확인하지 못했어요"),"IN12 unavailable TPMS not invented")
            verify(service.events(issue.id).size==events&&service.issue(issue.id).status==IssueStatus.UNRESOLVED,"IN13 read-only action cannot change status/history")
            service.selectDriver(next);verify(CaritdaActions.query("handover",service,obs).contains(issue.situation),"IN14 handover query preserves author")
            verify(!service.canManage(issue)&&service.acknowledge(issue.id),"IN15 other author cannot manage; explicit acknowledgement works")
            val location=IssueLocation(37.5,127.0,now-1000,now,"VALIDATION_SYNTHETIC")
            val locationName="integration-location-$id.db"
            IssueLocationStore(c,locationName).use{it.save(issue.id,location)}
            IssueLocationStore(c,locationName).use{verify(it.latest(issue.id)==location,"IN16 optional location restores after close/reopen")}
            verify(!location.copy(latitude=Double.NaN).valid()&&!location.copy(measuredAt=now+90000).valid(),"IN17 invalid coordinates/time rejected")
            verify(service.issue(issue.id).situation==issue.situation&&service.events(issue.id).size==events+1,"IN18 sidecar location leaves original issue and events unchanged")
            verify(runCatching{service.deleteOwned(issue.id)}.isFailure,"IN23 non-author deletion denied")
            service.selectDriver(author)
            val edit=JournalDraft(UUID.randomUUID().toString(),author.id,author.label,"EDIT",issue.id,issue.revision)
            service.saveDraft(edit);val revised=service.revise(issue.id,SymptomType.VIBRATION,"정차 중 진동 fixture","확인 메모",edit.id,issue.revision)
            verify(revised.id==issue.id&&revised.reporter.id==author.id&&revised.revision==issue.revision+1,"IN24 revision preserves original ID and author")
            verify(service.issues(RecordFilter.ALL,"진동").single().id==issue.id&&service.issues(RecordFilter.ALL,"확인 메모").size==1,"IN25 revised category/body/memo searchable")
            verify(service.events(issue.id).any{it.kind==EventKind.EDITED&&it.text.contains(issue.situation)},"IN26 edit history retains prior text")
            service.addNote(issue.id,"점검 메모 fixture");IssueStatus.entries.forEach{service.changeStatus(issue.id,it)}
            verify(service.issues(RecordFilter.COMPLETED).size==1&&service.issues(RecordFilter.MANAGED).isEmpty(),"IN27 resolved filters reflect four manual transitions")
            verify(service.events(issue.id).count{it.kind==EventKind.STATUS}==3,"IN28 idempotent same status does not add event")
            service.recur(issue.id,"같은 진동 재발 fixture")
            verify(service.issue(issue.id).status==IssueStatus.UNRESOLVED&&service.events(issue.id).any{it.kind==EventKind.RECURRENCE},"IN29 explicit recurrence reopens without deleting history")
            service.renameDriver(author.id,"작성자변경")
            verify(service.canManage(service.issue(issue.id))&&service.issue(issue.id).reporter.label==author.label,"IN30 rename keeps ownership and original author snapshot")
            val beforeDelete=service.events(issue.id).size;service.deleteOwned(issue.id)
            verify(service.issues().isEmpty()&&repo.issue(issue.id,true)?.deletedAt!=null,"IN31 logical deletion excludes normal lists and preserves original")
            verify(repo.events(issue.id).size==beforeDelete+1,"IN32 logical deletion preserves inspection/recurrence/ack history")
            IssueLocationStore(c,locationName).use{verify(it.latest(issue.id)==location,"IN33 deleting concern cannot delete independent location evidence")}
        }}
        main{
            val input=SpeechInput(c){false};var called=false;input.listen({called=true},{called=true});verify(!called&&!input.busy,"IN19 restricted STT never opens microphone")
            val output=SpeechOutput(c){false};output.speak("시험"){called=true};verify(!called&&!output.busy,"IN20 restricted TTS never starts")
            val loc=OneShotLocation(c){false};loc.read{_,_->called=true};verify(!called&&!loc.busy,"IN21 restricted location never requests")
            val nav=InspectionNavigation(c){false};nav.request(37.5,127.0,"시험"){called=true};verify(!called&&!nav.busy,"IN22 restricted navigation never requests")
        }
        return lines
    }
    fun live():List<String>{
        ValidationEnvironment.requireIsolated(c)
        var gate:CarSafetyGate?=null;val safe=CountDownLatch(1)
        main{gate=CarSafetyGate(c){if(it==EditingState.ALLOWED)safe.countDown()};gate!!.start()}
        try{
            verify(safe.await(12,TimeUnit.SECONDS),"IL01 actual parked CarSafetyGate allowed")
            val store=ObservationStore(c,"integration-live-${UUID.randomUUID()}.db");val latch=CountDownLatch(1);var reader:PleosReadClient?=null
            main{reader=PleosReadClient(c,store){if(reader?.busy==false)latch.countDown()};reader!!.readOnce()}
            verify(latch.await(22,TimeUnit.SECONDS),"IL02 bounded SDK read batch finished")
            main{reader!!.stop()}
            val rows=store.latest(100)
            verify(rows.map{it.kind}.toSet()==VehicleHealth.labels.keys+setOf("EV_SOC","ODOMETER","FUEL_PERCENT"),"IL03 all public reads have explicit terminal results")
            verify(rows.all{it.source==ObservationSource.PLEOS_EMULATOR_SDK},"IL04 actual emulator observations distinct from fixtures")
            verify(rows.filter{it.status!=ObservationStatus.RECEIVED}.all{it.raw==null&&!it.valid},"IL05 failures never become zero values")
            for(row in rows.sortedBy{it.kind})lines.add("SDK ${row.kind} ${row.status} ${row.raw?:"-"} ${row.unit?:"-"} supported=${row.supported}")
            verify(rows.any{it.kind=="EV_SOC"&&it.status==ObservationStatus.RECEIVED},"IL06 actual SOC received")
            verify(rows.any{it.kind=="ODOMETER"&&it.status==ObservationStatus.RECEIVED},"IL07 actual odometer received")
            store.close()
            val navLatch=CountDownLatch(1);var n:NaviHelper?=null;var navResult="NAVI_TIMEOUT"
            main{try{n=NaviHelper(c);n!!.addListener(object:NaviHelperEventListener{
                override fun onRouteStateInfo(routeStateInfo:RouteStateInfo){navResult="NAVI_ROUTE_STATE_RECEIVED routing="+routeStateInfo.isRouting+" terms="+routeStateInfo.termsAgreement;navLatch.countDown()}
                override fun onError(errorCode:NaviErrorCode){navResult="NAVI_ERROR_"+errorCode.name;navLatch.countDown()}
            });n!!.initialize({n!!.getRouteStateInfo()},{navResult="NAVI_CONNECTION_FAILED";navLatch.countDown()})}catch(_:Exception){navResult="NAVI_EXCEPTION";navLatch.countDown()}}
            navLatch.await(12,TimeUnit.SECONDS);main{n?.release()};lines.add("RUNTIME $navResult; no destination mutation")
            if(c.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")==PackageManager.PERMISSION_GRANTED){
                val locLatch=CountDownLatch(1);var location:OneShotLocation?=null;var code="LOCATION_TIMEOUT"
                main{location=OneShotLocation(c){gate?.state==EditingState.ALLOWED};location!!.read{value,status->code=status+" valid="+(value!=null);locLatch.countDown()}}
                locLatch.await(14,TimeUnit.SECONDS);main{location?.stop()};lines.add("RUNTIME $code; no issue link/no coordinates logged")
            }else lines.add("RUNTIME LOCATION_PERMISSION_REQUIRED; no request")
            val ttsLatch=CountDownLatch(1);var output:SpeechOutput?=null;var tts=""
            main{output=SpeechOutput(c){gate?.state==EditingState.ALLOWED};output!!.speak("카잇다 음성 안내 시험입니다. 차량 고장 진단이 아닙니다."){message->tts=message;if(!output!!.busy)ttsLatch.countDown()}}
            ttsLatch.await(22,TimeUnit.SECONDS);val source=main{val s=output!!.source;output!!.stop();s};lines.add("RUNTIME TTS source=$source result=$tts; audible confirmation separate")
        }finally{main{gate?.stop()}}
        return lines
    }
}
