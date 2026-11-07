package com.nextdrive.pleos

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import java.util.UUID

object MigrationChecks {
    private const val issueColumns="id,symptom_type,situation,discovered_at,recorded_at,reporter,memo,status,revision,updated_at"
    private const val eventColumns="_id,issue_id,kind,actor,at,body,revision,status"
    private fun snapshot(db:SQLiteDatabase,table:String,columns:String):List<List<String?>> =
        db.rawQuery("SELECT $columns FROM $table ORDER BY 1",null).use{c-> buildList{while(c.moveToNext())add((0 until c.columnCount).map{if(c.isNull(it))null else c.getString(it)})} }
    fun run(context:Context):List<String> {
        ValidationEnvironment.requireIsolated(context)
        val results=mutableListOf<String>()
        fun verify(value:Boolean,label:String){check(value){label};results.add("PASS $label")}
        val name="legacy-fixture-${UUID.randomUUID()}.db"
        val db=context.openOrCreateDatabase(name,0,null)
        // Frozen original v1 schema and enum values, independent from v2 mapper.
        db.execSQL("CREATE TABLE issues(id TEXT PRIMARY KEY NOT NULL,symptom_type TEXT NOT NULL,situation TEXT NOT NULL,discovered_at INTEGER NOT NULL,recorded_at INTEGER NOT NULL,reporter TEXT NOT NULL,memo TEXT NOT NULL,status TEXT NOT NULL,revision INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE events(_id INTEGER PRIMARY KEY AUTOINCREMENT,issue_id TEXT NOT NULL REFERENCES issues(id),kind TEXT NOT NULL,actor TEXT NOT NULL,at INTEGER NOT NULL,body TEXT NOT NULL,revision INTEGER NOT NULL,status TEXT)")
        db.execSQL("CREATE UNIQUE INDEX one_ack_per_revision ON events(issue_id,actor,revision) WHERE kind='ACKNOWLEDGED'")
        db.execSQL("CREATE TABLE settings(name TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)")
        db.execSQL("INSERT INTO settings VALUES('driver','B')")
        db.execSQL("INSERT INTO issues VALUES('legacy','VIBRATION','가속할 때 핸들이 진동함',10,11,'A','기존 메모','UNRESOLVED',2,18)")
        listOf("CREATED","ACKNOWLEDGED","NOTE","STATUS","RECURRENCE").forEachIndexed{i,kind->
            db.execSQL("INSERT INTO events(issue_id,kind,actor,at,body,revision,status) VALUES(?,?,?,?,?,?,?)",
                arrayOf<Any?>("legacy",kind,if(i==0)"A" else "B",12+i,"과거 이력 $i",if(i==4)2 else 1,if(kind=="STATUS")"INSPECTED" else null))
        }
        db.version=1
        val beforeIssues=snapshot(db,"issues",issueColumns);val beforeEvents=snapshot(db,"events",eventColumns);db.close()
        val failing=object:SQLiteOpenHelper(context,name,null,2){
            override fun onCreate(db:SQLiteDatabase)=error("unexpected create")
            override fun onUpgrade(db:SQLiteDatabase,o:Int,n:Int){DriverMigration.migrate(db){error("injected migration fault")}}
        }
        verify(runCatching{failing.writableDatabase}.isFailure,"migration fault injected before commit")
        failing.close()
        val rolled=SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY)
        verify(rolled.version==1 && snapshot(rolled,"issues",issueColumns)==beforeIssues && snapshot(rolled,"events",eventColumns)==beforeEvents,"upgrade rollback preserves v1 rows and version")
        verify(rolled.rawQuery("PRAGMA table_info(issues)",null).use{it.columnCount>0;var added=false;while(it.moveToNext())if(it.getString(1)=="reporter_id")added=true;!added},"rollback removes partial schema additions")
        rolled.close()
        var repo=SqliteIssueRepository(context,name)
        verify(repo.issues().single().reporter==Driver.A && repo.events("legacy").drop(1).all{it.actor==Driver.B},"A/B map to permanent identities and historical names")
        verify(snapshot(repo.readableDatabase,"issues",issueColumns)==beforeIssues && snapshot(repo.readableDatabase,"events",eventColumns)==beforeEvents,"all original scalar fields event order and relationships unchanged")
        verify(repo.currentDriver()?.id=="B" && repo.issue("legacy")?.revision==2,"recent driver and recurrence preserved")
        repo.writableDatabase.beginTransaction();try{DriverMigration.migrate(repo.writableDatabase);repo.writableDatabase.setTransactionSuccessful()}finally{repo.writableDatabase.endTransaction()}
        verify(repo.events("legacy").size==5 && repo.drivers().size==2,"repeated migration adds no duplicate driver or event")
        repo.close();repo=SqliteIssueRepository(context,name)
        val service=CaritdaService(repo,{true})
        verify(!service.confirmed(),"new session does not auto-confirm recent driver")
        val added=service.registerDriver("민수")
        verify(repo.currentDriver()?.id=="B","registration and pending candidate do not switch driver")
        verify(runCatching{service.registerDriver("  민수  ")}.isFailure,"duplicate active name rejected")
        service.selectDriver(Driver.B);service.renameDriver("A","예전 운전자")
        verify(repo.issue("legacy")!!.reporter.label=="운전자 A" && ConsultationSummary.build(repo.issue("legacy")!!,repo.events("legacy")).contains("운전자 A"),"rename preserves historic author snapshot in consultation")
        service.archiveDriver("A")
        verify(repo.drivers().none{it.id=="A"} && repo.issue("legacy")!=null,"archive preserves referenced history")
        verify(runCatching{service.archiveDriver("B")}.isFailure,"current driver cannot be archived")
        service.selectDriver(added)
        val draft=JournalDraft(UUID.randomUUID().toString(),added.id,added.label,"RECORD",body="보존할 초안")
        service.saveDraft(draft)
        verify(runCatching{service.selectDriver(Driver.B)}.isFailure,"dirty draft blocks reassignment")
        repo.close();repo=SqliteIssueRepository(context,name)
        verify(repo.draft()?.ownerId==added.id && repo.draft()?.body=="보존할 초안","draft survives SQLite reopen with original author")
        val resumed=CaritdaService(repo,{true});resumed.selectDriver(added)
        val saved=resumed.record(SymptomType.NOISE,"보존할 초안",System.currentTimeMillis(),"",draft.id)
        resumed.record(SymptomType.NOISE,"보존할 초안",System.currentTimeMillis(),"",draft.id)
        verify(repo.issues().size==2 && repo.draft()==null && saved.reporter.id==added.id,"draft commit clears cache atomically and repeated save is idempotent")
        var allowed=false;val locked=CaritdaService(repo,{allowed})
        verify(runCatching{locked.registerDriver("차단");false}.isFailure && runCatching{locked.selectDriver(Driver.B)}.isFailure,"restricted driver changes blocked")
        repo.close()
        val clean=SqliteIssueRepository(context,"fresh-${UUID.randomUUID()}.db")
        verify(clean.drivers().isEmpty() && clean.currentDriver()==null,"fresh install has no invented A/B profiles")
        clean.close();return results
    }
    fun productionBaseline(context:Context):String {
        ValidationEnvironment.requireIsolated(context)
        val backup=java.io.File(context.filesDir,"ui-before-v1.db")
        check(backup.exists()){ "Private pre-migration baseline missing" }
        val old=SQLiteDatabase.openDatabase(backup.path,null,SQLiteDatabase.OPEN_READONLY)
        val issues=snapshot(old,"issues",issueColumns);val events=snapshot(old,"events",eventColumns);old.close()
        val repo=SqliteIssueRepository(context)
        check(snapshot(repo.readableDatabase,"issues",issueColumns).containsAll(issues))
        check(snapshot(repo.readableDatabase,"events",eventColumns).containsAll(events))
        repo.close()
        val obsOld=SQLiteDatabase.openDatabase(java.io.File(context.filesDir,"ui-observations-before.db").path,null,SQLiteDatabase.OPEN_READONLY)
        val obs=SQLiteDatabase.openDatabase(context.getDatabasePath("caritda-observations.db").path,null,SQLiteDatabase.OPEN_READONLY)
        check(snapshot(obs,"observations","*").containsAll(snapshot(obsOld,"observations","*")));obsOld.close();obs.close()
        return "PRODUCTION_MIGRATION_PASS issues=${issues.size} events=${events.size} observations unchanged"
    }
}
