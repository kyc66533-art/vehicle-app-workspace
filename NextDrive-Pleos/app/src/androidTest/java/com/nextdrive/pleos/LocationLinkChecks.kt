package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.pm.PackageManager
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.SymptomType
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One authorized emulator last-known sample, linked only to a unique validation fixture.
 * Does not set GPS, emit coordinates, touch normal records or make external requests. */
class LocationLinkChecks(private val inst:Instrumentation){
    private fun <T> main(block:()->T):T{var v:T?=null;inst.runOnMainSync{v=block()};@Suppress("UNCHECKED_CAST")return v as T}
    fun run():List<String>{
        val c=inst.targetContext;ValidationEnvironment.requireIsolated(c)
        check(c.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")==PackageManager.PERMISSION_GRANTED){"LOCATION_PERMISSION_REQUIRED"}
        val ready=CountDownLatch(1);var gate:CarSafetyGate?=null;var reader:OneShotLocation?=null
        main{gate=CarSafetyGate(c){if(it==EditingState.ALLOWED)ready.countDown()};gate!!.start()}
        try{
            check(ready.await(12,TimeUnit.SECONDS)){"SAFETY_UNAVAILABLE"}
            val latch=CountDownLatch(1);var value:IssueLocation?=null;var status="LOCATION_TIMEOUT"
            main{reader=OneShotLocation(c){gate?.state==EditingState.ALLOWED};reader!!.read{v,code->value=v;status=code;latch.countDown()}}
            check(latch.await(14,TimeUnit.SECONDS)&&value?.valid()==true){status};val location=value!!
            val lines=mutableListOf("PASS LL01 actual FusedLocation callback contains a valid last-known coordinate and timestamp")
            check(location.source=="PLEOS_FUSED_LAST_KNOWN");lines.add("PASS LL02 last-known source retained; not symptom-time or simulated app-generated GPS")
            val id=UUID.randomUUID().toString();val name="gleo-location-$id.db"
            val issue=SqliteIssueRepository(c,"gleo-location-issue-$id.db").use{val s=CaritdaService(it,{true});s.selectDriver(s.registerDriver("위치연결 fixture"));s.record(SymptomType.OTHER,"위치 연결 저장 확인 fixture",System.currentTimeMillis(),"")}
            IssueLocationStore(c,name).use{it.save(issue.id,location)}
            IssueLocationStore(c,name).use{check(it.latest(issue.id)==location);check(it.latest("another-issue")==null)}
            lines.add("PASS LL03 actual SDK value persists after sidecar reopen for only the selected fixture issue")
            SqliteIssueRepository(c,"gleo-location-issue-$id.db").use{check(it.issue(issue.id)==issue&&it.events(issue.id).size==1)}
            lines.add("PASS LL04 optional location link preserves symptom and manual state")
            return lines
        }finally{main{reader?.stop();gate?.stop()}}
    }
}
