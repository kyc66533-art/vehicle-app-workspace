package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.view.View
import android.widget.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import java.util.UUID

/** Synthetic cases use named disposable DBs; live cases use the actual SDK in validation only. */
class AutomaticChecks(private val inst:Instrumentation){
    private val c=inst.targetContext
    private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(f:()->T):T{var r:T?=null;inst.runOnMainSync{r=f()};@Suppress("UNCHECKED_CAST")return r as T}
    private fun await(label:String,f:()->Boolean){val until=System.currentTimeMillis()+25000;while(System.currentTimeMillis()<until){if(f())return;Thread.sleep(80)};error(label)}
    private fun tag(a:MainActivity,k:String):View?=a.window.decorView.findViewWithTag(k)
    private fun click(a:MainActivity,k:String){main{val v=tag(a,k)?:error("Missing $k");check(v.isEnabled){"Disabled $k"};v.performClick()};inst.waitForIdleSync()}
    private fun launch()=inst.startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(c,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun rows(db:SQLiteDatabase,table:String)=db.rawQuery("SELECT * FROM $table ORDER BY 1",null).use{r->buildList{while(r.moveToNext())add((0 until r.columnCount).map{if(r.isNull(it))null else r.getString(it)})}}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        migration()
        val state=EntryReadState()
        verify(!state.claim(true,false,true,false)&&!state.claim(false,true,true,false)&&!state.claim(true,true,false,false),"AM safety invisible and welcome never initiate a query")
        verify(state.claim(true,true,true,false)&&!(1..100).any{state.claim(true,true,true,false)},"AM one entry claim despite repeated redraw or state callbacks")
        verify(!EntryReadState().claim(true,true,true,true),"AM concurrent SDK request cannot claim automatic entry")
        fixtures()
        val repo=SqliteIssueRepository(c);val obs=ObservationStore(c);val cards=SnapshotStore(c)
        val before=listOf("drivers","issues","events","settings","drafts").associateWith{rows(repo.readableDatabase,it)}
        var a=launch()
        await("entry read actual three outcomes"){obs.latest().size>=3&&cards.latest()!=null}
        verify(main{tag(a,"test_banner") is TextView},"AM internal TEST banner exists before driver confirmation")
        verify(c.packageManager.getApplicationLabel(c.applicationInfo).toString()=="[TEST] CARITDA 검증 전용","AM validation label explicitly TEST")
        val batch=obs.latest(3)
        verify(batch.map{it.kind}.toSet()==setOf("EV_SOC","ODOMETER","FUEL_PERCENT")&&batch.map{it.session}.distinct().size==1,"AM automatic entry makes one real SDK batch")
        verify(batch.filter{it.kind!="FUEL_PERCENT"}.all{it.status==ObservationStatus.RECEIVED&&it.source==ObservationSource.PLEOS_EMULATOR_SDK&&it.receivedAt!=null&&it.valid},"AM actual SOC and odometer received and stored with original metadata")
        lines.add("EVIDENCE "+batch.joinToString{"${it.kind}=${it.raw} ${it.unit} ${it.status} at=${it.receivedAt} source=${it.source} session=${it.session}"})
        verify(batch.single{it.kind=="FUEL_PERCENT"}.let{it.raw==null&&it.status==ObservationStatus.UNSUPPORTED},"AM unsupported fuel remains valueless")
        verify(before==before.keys.associateWith{rows(repo.readableDatabase,it)},"AM auto snapshot before driver start changes no driver issue event or draft")
        click(a,"driver_A");click(a,"start_driving")
        repeat(3){click(a,"nav_HOME");click(a,"vehicle_data");click(a,"nav_HOME")}
        Thread.sleep(1200)
        verify(obs.latest(100).size==3&&cards.cards().size==1,"AM screen navigation and freshness redraw never poll or duplicate cards")
        val monitor=inst.addMonitor(MainActivity::class.java.name,null,false)
        main{a.recreate()};a=monitor.waitForActivityWithTimeout(15000) as? MainActivity?:error("recreated activity missing");inst.removeMonitor(monitor)
        await("recreated app safe"){main{tag(a,"auto_snapshots")?.isEnabled==true}}
        Thread.sleep(1000)
        verify(obs.refs().size==3,"AM actual Activity recreation retains foreground entry claim")
        click(a,"auto_snapshots");click(a,"hide_snapshot")
        verify(cards.cards().isEmpty()&&cards.cards(true).size==1,"AM hide preserves one card without resolving issues")
        click(a,"toggle_hidden");click(a,"hide_snapshot");click(a,"nav_HOME")
        verify(cards.cards().size==1,"AM hidden card can be shown again")
        click(a,"new_issue");main{(tag(a,"situation") as EditText).setText("AM 격리 증상: 가속할 때 앞쪽에서 소음")}
        val draft=repo.draft()!!
        click(a,"select_snapshot")
        val frozen=cards.selected(draft.id)!!
        click(a,"save_issue")
        verify(repo.issue(draft.id)!=null&&cards.linked(draft.id)?.id==frozen&&main{tag(a,"linked_snapshot")!=null},"AM symptom commit links explicit immutable snapshot and opens same issue detail")
        val original=repo.issue(draft.id)!!;val events=repo.events(draft.id)
        cards.hide(cards.linked(draft.id)!!.cardId,true)
        verify(repo.issue(draft.id)==original&&repo.events(draft.id)==events&&cards.linked(draft.id)?.id==frozen,"AM card visibility never mutates manual status text author or history")
        cards.hide(cards.linked(draft.id)!!.cardId,false)
        // A real SQLite link failure follows a committed human record, then same ID retry.
        click(a,"nav_HOME");click(a,"new_issue")
        main{(tag(a,"situation") as EditText).setText("AM 격리 연결 실패 후 보존")}
        val retryId=repo.draft()!!.id
        click(a,"select_snapshot")
        cards.writableDatabase.execSQL("CREATE TRIGGER am_link_fault BEFORE INSERT ON links BEGIN SELECT RAISE(ABORT,'isolated link failure'); END")
        click(a,"save_issue")
        verify(repo.issue(retryId)!=null&&cards.pending(retryId)&&main{(tag(a,"feedback") as TextView).text.contains("증상은 저장됐지만 차량 정보 연결에 실패")},"AM actual UI link failure preserves human commit and displays partial success")
        cards.writableDatabase.execSQL("DROP TRIGGER am_link_fault");click(a,"link_snapshot")
        await("link confirmation"){inst.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("확인")?.any{it.isClickable&&it.text?.toString()=="확인"}==true}
        val positive=inst.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("확인").first{it.isClickable&&it.text?.toString()=="확인"}
        check(positive.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));inst.waitForIdleSync();cards.completeLink(retryId)
        verify(cards.linked(retryId)!=null&&repo.issues().size==2&&main{tag(a,"linked_snapshot")!=null},"AM actual UI retries same issue ID idempotently without duplicate symptom")
        c.getSharedPreferences("am_cold",0).edit().putString("issue",draft.id).putString("snapshot",frozen).putString("identity",obs.identity()).commit()
        main{a.finish()};inst.waitForIdleSync();repo.close();obs.close();cards.close()
        return lines
    }
    fun preservation():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val before=SQLiteDatabase.openDatabase(c.getFileStreamPath("automatic-observations-v1.db").path,null,SQLiteDatabase.OPEN_READONLY)
        val after=SQLiteDatabase.openDatabase(c.getFileStreamPath("automatic-observations-after.db").path,null,SQLiteDatabase.OPEN_READONLY)
        verify(before.version==1&&after.version==2&&rows(after,"observations").take(rows(before,"observations").size)==rows(before,"observations"),"AM general app upgrade preserves every old observation scalar and appends real SDK outcomes")
        verify(after.rawQuery("PRAGMA integrity_check",null).use{it.moveToFirst();it.getString(0)=="ok"},"AM general observation DB integrity after automatic read")
        before.close();after.close();return lines
    }
    fun cold():List<String>{
        ValidationEnvironment.requireIsolated(c)
        val p=c.getSharedPreferences("am_cold",0);val obs=ObservationStore(c);val cards=SnapshotStore(c);val repo=SqliteIssueRepository(c)
        verify(obs.identity()==p.getString("identity",null),"AM observation store identity stable across actual force-stop")
        verify(cards.linked(p.getString("issue",null)!!)?.id==p.getString("snapshot",null),"AM immutable symptom link survives process restart")
        val old=obs.refs().map{it.rowId}.toSet();val oldLinked=cards.linked(p.getString("issue",null)!!)!!
        val a=launch();await("new entry new real query"){obs.refs().count{it.rowId !in old}==3}
        await("derived reconcile"){cards.cards().size==1}
        verify(cards.cards().size==1&&cards.linked(p.getString("issue",null)!!)==oldLinked,"AM restart updates one card and never replaces linked historical evidence")
        main{inst.callActivityOnStop(a)}
        val n=obs.refs().size;Thread.sleep(1500)
        verify(obs.refs().size==n,"AM stopped app adds no background polling observations")
        main{a.finish()};repo.close();obs.close();cards.close();return lines
    }
    private fun migration(){
        val source=c.getFileStreamPath("automatic-observations-v1.db");check(source.exists()){"Fresh production observation backup copy required"}
        val name="am-migration-${UUID.randomUUID()}.db";source.copyTo(c.getDatabasePath(name))
        val before=SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READONLY).use{check(it.version==1);rows(it,"observations")}
        val faulty=object:SQLiteOpenHelper(c,name,null,2){override fun onCreate(db:SQLiteDatabase)=error("unexpected");override fun onUpgrade(db:SQLiteDatabase,o:Int,n:Int){ObservationMigration.migrate(db){error("isolated migration fault")}}}
        verify(runCatching{faulty.writableDatabase}.isFailure,"AM observation migration injected fault rolls back");faulty.close()
        SQLiteDatabase.openDatabase(c.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY).use{verify(it.version==1&&rows(it,"observations")==before,"AM failed migration preserves every source scalar and version")}
        var store=ObservationStore(c,name);val identity=store.identity()
        verify(store.readableDatabase.version==2&&rows(store.readableDatabase,"observations")==before,"AM successful v1-v2 copy migration preserves all original observations")
        store.writableDatabase.beginTransaction();try{ObservationMigration.migrate(store.writableDatabase);store.writableDatabase.setTransactionSuccessful()}finally{store.writableDatabase.endTransaction()};store.close();store=ObservationStore(c,name)
        verify(store.identity()==identity&&rows(store.readableDatabase,"observations")==before,"AM repeated migration reopen stable identity and unchanged rows")
        store.close()
    }
    private fun fixtures(){
        val raw=ObservationStore(c,"am-fixture-${UUID.randomUUID()}.db");val cards=SnapshotStore(c,"am-cards-${UUID.randomUUID()}.db")
        var now=System.currentTimeMillis()
        fun batch(soc:String="100",distance:String="0.0",fail:Boolean=false,source:ObservationSource=ObservationSource.PLEOS_EMULATOR_SDK){val session=UUID.randomUUID().toString();listOf("EV_SOC","ODOMETER","FUEL_PERCENT").forEach{kind->val good=kind!="FUEL_PERCENT"&&!(kind=="EV_SOC"&&fail);raw.append(VehicleObservation(kind,if(good)if(kind=="EV_SOC")soc else distance else null,if(good)if(kind=="ODOMETER")"km"else"%"else null,if(good)now else null,source,if(good)true else false,good,session,if(good)ObservationStatus.RECEIVED else ObservationStatus.UNSUPPORTED,"SYNTHETIC_VALIDATION_ONLY",now++))};cards.reconcile(raw)}
        batch();val first=cards.latest()!!;repeat(99){batch()}
        verify(cards.cards().size==1&&raw.refs().size==300&&cards.history(first.cardId).isEmpty(),"AM 100 identical synthetic batches preserve raw rows but one card and no value-change events")
        batch("99","1.0");verify(cards.history(first.cardId).size==1&&cards.get(first.id)==first,"AM changed values append immutable evidence without inferring failure or change occurrence time")
        batch(fail=true);val failed=cards.latest()!!;val soc=failed.evidence.filter{it.value.kind=="EV_SOC"}
        verify(soc.size==2&&soc.first().value.raw==null&&soc.last().value.raw=="99", "AM per-signal latest failure and prior success retain distinct evidence")
        verify(failed.evidence.first{it.value.kind=="ODOMETER"}.value.receivedAt!=soc.last().value.receivedAt,"AM partial success never lends another signal its timestamp")
        val count=cards.cards().size;batch(source=ObservationSource.TEST_ONLY)
        verify(cards.cards().size==count,"AM synthetic TEST_ONLY source never produces a real SDK card")
        listOf("NaN","-1","101").forEach{number->verify(!SnapshotStore.accepted(VehicleObservation("EV_SOC",number,"%",now,ObservationSource.TEST_ONLY,true,true,"fixture",ObservationStatus.RECEIVED,"synthetic")),"AM invalid scalar $number rejected by derived value gate")}
        val original=cards.latest()!!;cards.writableDatabase.execSQL("CREATE TRIGGER am_derived_fault BEFORE INSERT ON snapshots BEGIN SELECT RAISE(ABORT,'isolated derived fault'); END")
        verify(runCatching{batch("80")}.isFailure&&raw.refs().size>300&&cards.latest()==original,"AM raw commit survives derived failure without misleading card update")
        cards.writableDatabase.execSQL("DROP TRIGGER am_derived_fault");cards.reconcile(raw);cards.reconcile(raw)
        verify(cards.cards().size==1&&cards.latest()!=original,"AM restart-style replay repairs derived gap idempotently")
        val other=ObservationStore(c,"am-other-${UUID.randomUUID()}.db")
        verify(other.identity()!=raw.identity(),"AM distinct observation stores never share reference namespace")
        other.close();batch(source=ObservationSource.PLEOS_VEHICLE_SDK)
        verify(cards.cards().size==2,"AM emulator and vehicle environments cannot collapse into one summary card")
        val newer=cards.latest()!!
        cards.prepareLink("isolated-selection-retry",first.id);cards.prepareLink("isolated-selection-retry",newer.id);cards.completeLink("isolated-selection-retry")
        verify(cards.linked("isolated-selection-retry")?.id==newer.id,"AM pending selection can change before issue commit retry")
        cards.prepareLink("isolated-selection-retry",first.id);cards.completeLink("isolated-selection-retry")
        verify(cards.linked("isolated-selection-retry")?.id==newer.id,"AM committed immutable link cannot be rewritten by later pending selection")
        raw.close();cards.close()
    }
}
