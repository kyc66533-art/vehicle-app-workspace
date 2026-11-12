package com.nextdrive.pleos

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nextdrive.pleos.data.RecordMigration
import com.nextdrive.pleos.data.SqliteIssueRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object RecordMigrationChecks {
    private val tables=listOf("drivers","issues","events","settings","drafts")
    private fun columns(db:SQLiteDatabase,table:String)=db.rawQuery("PRAGMA table_info($table)",null).use{c->buildList{while(c.moveToNext())add(c.getString(1))}}
    private fun rows(db:SQLiteDatabase,table:String,cols:List<String>)=db.rawQuery("SELECT ${cols.joinToString(",")} FROM $table ORDER BY 1",null).use{c->buildList{while(c.moveToNext())add(cols.indices.map{if(c.isNull(it))null else c.getString(it)})}}
    fun run(context:Context):List<String>{
        ValidationEnvironment.requireIsolated(context)
        val results=mutableListOf<String>()
        fun verify(ok:Boolean,label:String){check(ok){label};results.add("PASS $label")}
        val source=context.getFileStreamPath("source-v2.db")
        check(source.exists()){ "Production backup copy is required before testing migration" }
        val original=SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READONLY)
        check(original.version==2)
        val cols=tables.associateWith{columns(original,it)}
        val before=tables.associateWith{rows(original,it,cols.getValue(it))}
        val proven=context.getFileStreamPath("proven-test-ids.txt").takeIf{it.exists()}?.readLines()?.filter{it.isNotBlank()}?.toSet().orEmpty()
        val inventory=JSONArray()
        original.rawQuery("SELECT id,situation,reporter_id,reporter_name,status FROM issues ORDER BY id",null).use{c->while(c.moveToNext()){
            inventory.put(JSONObject().apply{
                put("id",c.getString(0));put("excerpt",c.getString(1).take(100));put("reporterId",c.getString(2));put("reporterName",c.getString(3));put("status",c.getString(4))
                put("classification",if(c.getString(0) in proven)"CODEX_PROVEN_BY_LOG" else "UNCONFIRMED_PRESERVE")
                put("possibleTestMarker",c.getString(1).contains("검증용"))
            })
        }}
        context.getFileStreamPath("record-inventory-private.json").writeText(JSONObject().apply{put("records",inventory);put("confirmedUserRecords",0);put("note","Zero positively identified users does not mean no user-authored records; all unknown records are preserved.")}.toString(2))
        val provenCount=(0 until inventory.length()).count{inventory.getJSONObject(it).getString("classification")=="CODEX_PROVEN_BY_LOG"}
        results.add("PASS INVENTORY total=${inventory.length()} provenByLog=$provenCount unconfirmedPreserved=${inventory.length()-provenCount}; no deletions")
        original.close()
        val name="production-copy-${UUID.randomUUID()}.db";source.copyTo(context.getDatabasePath(name))
        val failure=object:SQLiteOpenHelper(context,name,null,3){
            override fun onCreate(db:SQLiteDatabase)=error("unexpected create")
            override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){RecordMigration.migrate(db){error("injected v3 upgrade fault")}}
        }
        verify(runCatching{failure.writableDatabase}.isFailure,"v2 copy fails inside upgrade transaction as injected");failure.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY).use{db->
            verify(db.version==2 && tables.all{columns(db,it)==cols[it] && rows(db,it,cols.getValue(it))==before[it]},"v3 upgrade failure rolls back schema version and every original scalar")
        }
        var repo=SqliteIssueRepository(context,name)
        verify(repo.readableDatabase.version==4 && tables.all{rows(repo.readableDatabase,it,cols.getValue(it))==before[it]},"production backup copy v2 to v4 preserves all original rows fields IDs and relationships")
        verify(repo.issues().all{it.deletedAt==null && it.deletedById==null},"migration does not mark any existing record deleted")
        repo.writableDatabase.beginTransaction();try{RecordMigration.migrate(repo.writableDatabase);repo.writableDatabase.setTransactionSuccessful()}finally{repo.writableDatabase.endTransaction()}
        repo.close();repo=SqliteIssueRepository(context,name)
        verify(tables.all{rows(repo.readableDatabase,it,cols.getValue(it))==before[it]} && repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()},"repeat migration reopen and foreign keys preserve the copied baseline")
        repo.close()
        return results
    }
    fun verifyAfter(context:Context):String {
        ValidationEnvironment.requireIsolated(context)
        val before=SQLiteDatabase.openDatabase(context.getFileStreamPath("source-v2.db").path,null,SQLiteDatabase.OPEN_READONLY)
        val after=SQLiteDatabase.openDatabase(context.getFileStreamPath("production-after-v3.db").path,null,SQLiteDatabase.OPEN_READONLY)
        try {
            check(before.version==2 && after.version==4)
            for(table in tables)check(rows(before,table,columns(before,table))==rows(after,table,columns(before,table))){"Production field changed: $table"}
            check(after.rawQuery("SELECT COUNT(*) FROM issues WHERE deleted_at IS NOT NULL OR deleted_by_id IS NOT NULL",null).use{it.moveToFirst();it.getInt(0)==0})
            check(after.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()})
            return "PRODUCTION_V3_PRESERVED every original driver issue event setting draft field unchanged; zero deletions; foreign keys valid"
        }finally{before.close();after.close()}
    }
}
