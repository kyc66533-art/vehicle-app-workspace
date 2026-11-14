package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.widget.EditText
import android.widget.TextView
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.ui.VehicleDisplaySettings
import com.nextdrive.pleos.vehicle.*
import java.util.TimeZone
import java.util.UUID

/** Focused CP time/PX03 regressions. No legacy prepare(), existing preferences or cloud calls. */
class SubmissionReadinessChecks(private val inst:Instrumentation) {
    private val c=inst.targetContext
    private val h=BLightHarness(inst)
    private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun await(label:String,condition:()->Boolean){val end=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<end){if(h.main(condition))return;Thread.sleep(60)};error(label)}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val prefix="gleo-ui-"+UUID.randomUUID()
        val repo=SqliteIssueRepository(c,"$prefix.db")
        val owner=CaritdaService(repo,{true}).registerDriver("제출 확인 전용")
        val raw=ObservationStore(c,"$prefix-observations.db")
        val a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        val previousZone=TimeZone.getDefault()
        try{
            h.click(a,"driver_${owner.id}");h.click(a,"start_driving");h.click(a,"nav_VEHICLE")
            h.click(a,"read_vehicle")
            val sdk=h.get(a,"pleos") as PleosReadClient
            await("one-shot and independent Safety initial replies must finish before PX03 baseline"){SdkBatchSettlement.complete(h,a,raw)&&raw.mostRecent("EV_SOC",true)!=null}
            ck(raw.refs().any{it.value.source==ObservationSource.PLEOS_EMULATOR_SDK&&it.value.valid},"actual emulator SDK batch received; no synthetic sensor input")
            val rows=raw.refs()
            val batch=h.get(sdk,"activeSession") as String
            ck(rows.any{it.value.session==batch},"one-shot session identified separately from independent Safety subscription sessions")
            repeat(3){h.click(a,"nav_HOME")}
            Thread.sleep(1200)
            ck(raw.refs()==rows&&!h.main{sdk.busy}&&h.get(sdk,"activeSession")==batch,"PX03 settled rows and one-shot session remain exactly unchanged after three home redraws")
            h.click(a,"menu");h.click(a,"vehicle_settings");h.click(a,"time_device")
            val display=VehicleDisplaySettings(c.getSharedPreferences("$prefix-vehicle-display",0))
            ck(display.deviceTime,"CP device-time choice actually persisted in UUID preferences")
            h.click(a,"nav_VEHICLE")
            val received=raw.mostRecent("EV_SOC",true)!!.receivedAt!!
            for(zone in listOf("Asia/Seoul","America/Los_Angeles","UTC")){
                // Changes only this validation process, never Android's timezone or stored epochs.
                h.main{TimeZone.setDefault(TimeZone.getTimeZone(zone));h.draw(a)}
                val text=h.main{h.text(a.window.decorView)}
                ck(display.zone.id==zone&&text.contains(display.time(received)),"CP actual vehicle screen renders selected device zone $zone with exact receipt time")
                ck(raw.refs()==rows,"CP zone $zone changes no original SDK rows or epochs")
            }
            ck(VehicleDisplaySettings.format(received,java.time.ZoneId.of("Asia/Seoul")).contains("KST (+09:00)"),"Seoul uses documented KST alias; IANA-ID substring was the old false failure")
            ck(repo.issues().isEmpty()&&repo.readableDatabase.rawQuery("SELECT count(*) FROM events",null).use{it.moveToFirst();it.getInt(0)==0},"focused verification creates no issue or handover event")
            lines.add("BOUNDARIES SDK=REAL_EMULATOR TIMEZONES=VALIDATION_PROCESS_FIXTURE MODEL_CALLS=0 ROOT_DATABASES_UNTOUCHED")
        }finally{h.main{TimeZone.setDefault(previousZone);a.finish()};raw.close();repo.close()}
        return lines
    }
}
