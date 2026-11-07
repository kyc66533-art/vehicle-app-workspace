package com.nextdrive.pleos.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nextdrive.pleos.model.*
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** Additive v1→v2 migration. SQLiteOpenHelper supplies the upgrade transaction. */
object DriverMigration {
    fun createV1(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE issues (id TEXT PRIMARY KEY NOT NULL, symptom_type TEXT NOT NULL,
            situation TEXT NOT NULL, discovered_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL,
            reporter TEXT NOT NULL, memo TEXT NOT NULL, status TEXT NOT NULL, revision INTEGER NOT NULL, updated_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, issue_id TEXT NOT NULL REFERENCES issues(id),
            kind TEXT NOT NULL, actor TEXT NOT NULL, at INTEGER NOT NULL, body TEXT NOT NULL, revision INTEGER NOT NULL, status TEXT)""")
        db.execSQL("CREATE INDEX events_by_issue ON events(issue_id,_id)")
        db.execSQL("CREATE UNIQUE INDEX one_ack_per_revision ON events(issue_id,actor,revision) WHERE kind='ACKNOWLEDGED'")
        db.execSQL("CREATE TABLE settings(name TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)")
        db.execSQL("INSERT INTO settings VALUES('driver','A')")
    }
    fun migrate(db: SQLiteDatabase, legacy: Boolean = true, afterBackfill: () -> Unit = {}) {
        db.execSQL("CREATE TABLE IF NOT EXISTS drivers(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,name_key TEXT NOT NULL,archived INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS active_driver_name ON drivers(name_key) WHERE archived=0")
        if (legacy) listOf(Driver.A, Driver.B).forEach {
            db.execSQL("INSERT OR IGNORE INTO drivers(id,name,name_key) VALUES(?,?,?)", arrayOf(it.id,it.label,it.label.lowercase(Locale.ROOT)))
        }
        fun add(table: String, column: String, type: String) {
            val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                var found=false; while(c.moveToNext()) if(c.getString(1)==column) found=true; found
            }
            if(!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
        }
        add("issues","reporter_id","TEXT REFERENCES drivers(id)")
        add("issues","reporter_name","TEXT")
        add("events","actor_id","TEXT REFERENCES drivers(id)")
        add("events","actor_name","TEXT")
        add("events","draft_id","TEXT")
        db.execSQL("UPDATE issues SET reporter_id=reporter,reporter_name=CASE reporter WHEN 'A' THEN '운전자 A' WHEN 'B' THEN '운전자 B' END WHERE reporter_id IS NULL")
        db.execSQL("UPDATE events SET actor_id=actor,actor_name=CASE actor WHEN 'A' THEN '운전자 A' WHEN 'B' THEN '운전자 B' END WHERE actor_id IS NULL")
        check(db.rawQuery("SELECT COUNT(*) FROM issues WHERE reporter_id IS NULL OR reporter_name IS NULL",null).use{it.moveToFirst();it.getInt(0)}==0)
        check(db.rawQuery("SELECT COUNT(*) FROM events WHERE actor_id IS NULL OR actor_name IS NULL",null).use{it.moveToFirst();it.getInt(0)}==0)
        afterBackfill()
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS event_draft_once ON events(draft_id) WHERE draft_id IS NOT NULL")
        db.execSQL("CREATE TABLE IF NOT EXISTS drafts(id TEXT PRIMARY KEY NOT NULL,slot INTEGER NOT NULL UNIQUE CHECK(slot=1),owner_id TEXT NOT NULL REFERENCES drivers(id),payload TEXT NOT NULL)")
        check(db.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()})
    }
}

class SqliteIssueRepository(context: Context, databaseName: String = "caritda_v01.db") :
    SQLiteOpenHelper(context.applicationContext,databaseName,null,3), IssueRepository {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        DriverMigration.createV1(db)
        db.execSQL("UPDATE settings SET value='' WHERE name='driver'")
        DriverMigration.migrate(db,legacy=false)
        RecordMigration.migrate(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion in 1..2 && newVersion==3) { "지원되지 않는 버전입니다. 기존 기록을 보존합니다." }
        if(oldVersion==1)DriverMigration.migrate(db)
        RecordMigration.migrate(db)
    }
    override fun drivers(includeArchived: Boolean): List<Driver> = readableDatabase.rawQuery(
        "SELECT * FROM drivers ${if(includeArchived) "" else "WHERE archived=0"} ORDER BY rowid",null).use { c ->
        buildList{while(c.moveToNext()) add(Driver(c.string("id"),c.string("name"),c.getInt(c.getColumnIndexOrThrow("archived"))!=0))}
    }
    override fun currentDriver(): Driver? {
        val id=readableDatabase.rawQuery("SELECT value FROM settings WHERE name='driver'",null).use { if(it.moveToFirst()) it.getString(0) else "" }
        return drivers(true).find { it.id==id }
    }
    override fun selectDriver(driver: Driver) {
        require(drivers().any{it.id==driver.id}) { "선택할 수 없는 운전자입니다." }
        check(writableDatabase.update("settings",ContentValues().apply{put("value",driver.id)},"name=?",arrayOf("driver"))==1)
    }
    private fun normalized(value: String, excluding: String? = null): Pair<String,String> {
        require(value.none{it.isISOControl()}) { "이름에는 줄바꿈이나 제어 문자를 넣을 수 없습니다." }
        val name=Normalizer.normalize(value.trim(),Normalizer.Form.NFC)
        require(name.isNotBlank() && name.codePointCount(0,name.length)<=12 && name.none{it.isISOControl()}) { "이름은 줄바꿈 없이 1~12자로 입력해 주세요." }
        val key=name.lowercase(Locale.ROOT)
        require(drivers().none{it.id!=excluding && it.label.lowercase(Locale.ROOT)==key}) { "같은 이름이 있습니다. 구분할 별명을 입력해 주세요." }
        return name to key
    }
    override fun registerDriver(name: String): Driver {
        val (label,key)=normalized(name); val driver=Driver(UUID.randomUUID().toString(),label)
        writableDatabase.insertOrThrow("drivers",null,ContentValues().apply{put("id",driver.id);put("name",label);put("name_key",key)})
        return driver
    }
    override fun renameDriver(id: String, name: String) {
        require(drivers().any{it.id==id}) { "보관된 운전자는 수정할 수 없습니다." }
        val (label,key)=normalized(name,id)
        check(writableDatabase.update("drivers",ContentValues().apply{put("name",label);put("name_key",key)},"id=?",arrayOf(id))==1)
    }
    override fun archiveDriver(id: String) {
        require(currentDriver()?.id!=id) { "다른 운전자로 시작한 뒤 보관해 주세요." }
        require(draft()?.ownerId!=id) { "작성 중인 초안을 먼저 저장하거나 취소해 주세요." }
        check(writableDatabase.update("drivers",ContentValues().apply{put("archived",1)},"id=? AND archived=0",arrayOf(id))==1)
    }
    override fun draft(): JournalDraft? = readableDatabase.rawQuery("SELECT payload FROM drafts WHERE slot=1",null).use { c ->
        if(!c.moveToFirst()) null else JSONObject(c.getString(0)).let { j ->
            JournalDraft(j.getString("id"),j.getString("ownerId"),j.getString("ownerName"),j.getString("kind"),
                j.optString("target").takeIf{it.isNotEmpty()},j.getInt("revision"),SymptomType.valueOf(j.getString("type")),
                j.getString("body"),j.getString("memo"),j.getLong("foundAt"),j.getLong("updatedAt"))
        }
    }
    override fun saveDraft(draft: JournalDraft) {
        val existing=draft(); require(existing==null || existing.id==draft.id) { "다른 초안을 먼저 정리해 주세요." }
        val j=JSONObject().apply {
            put("id",draft.id);put("ownerId",draft.ownerId);put("ownerName",draft.ownerName);put("kind",draft.kind)
            put("target",draft.target.orEmpty());put("revision",draft.revision);put("type",draft.type.name)
            put("body",draft.body);put("memo",draft.memo);put("foundAt",draft.foundAt);put("updatedAt",draft.updatedAt)
        }
        writableDatabase.insertWithOnConflict("drafts",null,ContentValues().apply{
            put("id",draft.id);put("slot",1);put("owner_id",draft.ownerId);put("payload",j.toString())
        },SQLiteDatabase.CONFLICT_REPLACE).also{check(it!=-1L)}
    }
    override fun discardDraft(id: String) { writableDatabase.delete("drafts","id=?",arrayOf(id)) }
    override fun issues(includeDeleted:Boolean): List<VehicleIssue> = readableDatabase.rawQuery("SELECT * FROM issues ${if(includeDeleted)"" else "WHERE deleted_at IS NULL"} ORDER BY updated_at DESC,id",null).use { c -> buildList{while(c.moveToNext())add(readIssue(c))} }
    override fun issue(id: String,includeDeleted:Boolean): VehicleIssue? = readableDatabase.rawQuery("SELECT * FROM issues WHERE id=?"+(if(includeDeleted)"" else " AND deleted_at IS NULL"),arrayOf(id)).use { if(it.moveToFirst())readIssue(it) else null }
    override fun events(id: String): List<IssueEvent> = readableDatabase.rawQuery("SELECT * FROM events WHERE issue_id=? ORDER BY _id",arrayOf(id)).use { c -> buildList{while(c.moveToNext())add(IssueEvent(
        EventKind.valueOf(c.string("kind")),Driver(c.string("actor_id"),c.string("actor_name")),c.number("at"),c.string("body"),c.getInt(c.getColumnIndexOrThrow("revision")),
        c.getString(c.getColumnIndexOrThrow("status"))?.let(IssueStatus::valueOf),c.getString(c.getColumnIndexOrThrow("draft_id"))))} }
    override fun create(issue: VehicleIssue, initialEvent: IssueEvent) {
        val db=writableDatabase;db.beginTransaction()
        try {
            db.insertOrThrow("issues",null,ContentValues().apply{
                put("id",issue.id);put("symptom_type",issue.type.name);put("situation",issue.situation)
                put("discovered_at",issue.discoveredAt);put("recorded_at",issue.recordedAt)
                put("reporter",issue.reporter.id);put("reporter_id",issue.reporter.id);put("reporter_name",issue.reporter.label)
                put("memo",issue.memo);put("status",issue.status.name);put("revision",issue.revision);put("updated_at",issue.updatedAt)
            })
            db.insertOrThrow("events",null,eventValues(issue.id,initialEvent));db.delete("drafts","id=?",arrayOf(issue.id));db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
    override fun append(id: String,event: IssueEvent,newStatus: IssueStatus?,recurrence: Boolean): Boolean {
        val db=writableDatabase;db.beginTransaction()
        try {
            val current=issue(id)?:error("기록을 찾을 수 없습니다.")
            if(event.draftId!=null && db.rawQuery("SELECT 1 FROM events WHERE draft_id=?",arrayOf(event.draftId)).use{it.moveToFirst()}) {db.setTransactionSuccessful();return false}
            check(event.revision==current.revision+if(recurrence)1 else 0) {"기록이 변경되었습니다. 다시 확인해 주세요."}
            if(event.kind==EventKind.ACKNOWLEDGED && events(id).any{it.kind==EventKind.ACKNOWLEDGED&&it.actor.id==event.actor.id&&it.revision==event.revision}){db.setTransactionSuccessful();return false}
            db.insertOrThrow("events",null,eventValues(id,event))
            check(db.update("issues",ContentValues().apply{put("updated_at",event.at);put("revision",event.revision);put("status",(newStatus?:current.status).name)},"id=?",arrayOf(id))==1)
            event.draftId?.let{db.delete("drafts","id=?",arrayOf(it))};db.setTransactionSuccessful();return true
        } finally {db.endTransaction()}
    }
    override fun revise(id:String,type:SymptomType,body:String,memo:String,expectedRevision:Int,event:IssueEvent):Boolean {
        val db=writableDatabase;db.beginTransaction()
        try {
            val current=issue(id)?:error("삭제되었거나 찾을 수 없는 기록입니다.")
            require(event.actor.id==current.reporter.id && event.kind==EventKind.EDITED) { "원래 작성자만 수정할 수 있어요." }
            if(event.draftId!=null && db.rawQuery("SELECT 1 FROM events WHERE issue_id=? AND draft_id=? AND kind='EDITED' AND actor_id=?",arrayOf(id,event.draftId,event.actor.id)).use{it.moveToFirst()}){db.setTransactionSuccessful();return false}
            require(current.revision==expectedRevision && event.revision==expectedRevision+1) { "기록이 변경되었어요. 현재 기록과 초안을 다시 확인해 주세요." }
            check(db.update("issues",ContentValues().apply{
                put("symptom_type",type.name);put("situation",body);put("memo",memo);put("updated_at",event.at);put("revision",event.revision)
            },"id=? AND revision=? AND deleted_at IS NULL",arrayOf(id,expectedRevision.toString()))==1)
            db.insertOrThrow("events",null,eventValues(id,event))
            event.draftId?.let{db.delete("drafts","id=?",arrayOf(it))}
            db.setTransactionSuccessful();return true
        }finally{db.endTransaction()}
    }
    override fun markDeleted(id:String,event:IssueEvent):Boolean {
        val db=writableDatabase;db.beginTransaction()
        try {
            val current=issue(id,true)?:error("기록을 찾을 수 없어요.")
            require(event.actor.id==current.reporter.id && event.kind==EventKind.DELETED) { "원래 작성자만 삭제할 수 있어요." }
            if(current.deletedAt!=null){db.setTransactionSuccessful();return false}
            check(db.update("issues",ContentValues().apply{put("deleted_at",event.at);put("deleted_by_id",event.actor.id)},"id=? AND deleted_at IS NULL",arrayOf(id))==1)
            db.insertOrThrow("events",null,eventValues(id,event));db.setTransactionSuccessful();return true
        }finally{db.endTransaction()}
    }
    private fun eventValues(id:String,e:IssueEvent)=ContentValues().apply {
        put("issue_id",id);put("kind",e.kind.name);put("actor",e.actor.id);put("actor_id",e.actor.id);put("actor_name",e.actor.label)
        put("at",e.at);put("body",e.text);put("revision",e.revision);put("status",e.status?.name);put("draft_id",e.draftId)
    }
    private fun readIssue(c:Cursor)=VehicleIssue(c.string("id"),SymptomType.valueOf(c.string("symptom_type")),c.string("situation"),
        c.number("discovered_at"),c.number("recorded_at"),Driver(c.string("reporter_id"),c.string("reporter_name")),c.string("memo"),
        IssueStatus.valueOf(c.string("status")),c.getInt(c.getColumnIndexOrThrow("revision")),c.number("updated_at"),
        c.getColumnIndexOrThrow("deleted_at").let{if(c.isNull(it))null else c.getLong(it)},c.getString(c.getColumnIndexOrThrow("deleted_by_id")))
    private fun Cursor.string(name:String)=getString(getColumnIndexOrThrow(name))
    private fun Cursor.number(name:String)=getLong(getColumnIndexOrThrow(name))
}
