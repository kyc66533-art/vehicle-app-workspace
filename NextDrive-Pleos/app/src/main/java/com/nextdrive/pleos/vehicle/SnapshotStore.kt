package com.nextdrive.pleos.vehicle

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Snapshot(val id:String,val cardId:String,val at:Long,val evidence:List<ObservationRef>,val changed:Boolean)

/** Derived information only. No issue writers, SYSTEM actors, or cross-database transaction claims. */
class SnapshotStore(context:Context,name:String="caritda-snapshots.db"):
    SQLiteOpenHelper(context.applicationContext,name,null,1) {
    private val appScope=context.packageName
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE cards (id TEXT PRIMARY KEY, scope TEXT NOT NULL, environment TEXT NOT NULL, type TEXT NOT NULL, hidden INTEGER NOT NULL DEFAULT 0, UNIQUE(scope,environment,type))")
        db.execSQL("CREATE TABLE snapshots (id TEXT PRIMARY KEY, card_id TEXT NOT NULL REFERENCES cards(id), session TEXT NOT NULL, at INTEGER NOT NULL, evidence TEXT NOT NULL, fingerprint TEXT NOT NULL, changed INTEGER NOT NULL, UNIQUE(card_id,session))")
        db.execSQL("CREATE TABLE links (issue_id TEXT PRIMARY KEY, snapshot_id TEXT NOT NULL REFERENCES snapshots(id), linked_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE selections (draft_id TEXT PRIMARY KEY, snapshot_id TEXT NOT NULL REFERENCES snapshots(id))")
        db.execSQL("CREATE TABLE pending_links (issue_id TEXT PRIMARY KEY, snapshot_id TEXT NOT NULL REFERENCES snapshots(id))")
    }
    override fun onConfigure(db:SQLiteDatabase){db.setForeignKeyConstraintsEnabled(true)}
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){error("자동 기록을 보존한 채 업데이트가 필요합니다.")}

    /** Replay is idempotent after raw commit/derived failure. Only complete real SDK batches qualify. */
    @Synchronized fun reconcile(raw:ObservationStore){
        val refs=raw.refs().filter{it.value.source in setOf(ObservationSource.PLEOS_EMULATOR_SDK,ObservationSource.PLEOS_VEHICLE_SDK)}
        refs.groupBy{Triple(it.storeId,it.value.source,it.value.session)}.values.forEach{batch->
            if(!batch.map{it.value.kind}.containsAll(listOf("EV_SOC","ODOMETER","FUEL_PERCENT")))return@forEach
            val sample=batch.first();val scope="$appScope:local-vehicle:${sample.storeId}"
            val db=writableDatabase;db.beginTransaction()
            try{
                db.execSQL("INSERT OR IGNORE INTO cards(id,scope,environment,type) VALUES(?,?,?,'VEHICLE_SNAPSHOT')",arrayOf(UUID.randomUUID().toString(),scope,sample.value.source.name))
                val card=db.rawQuery("SELECT id FROM cards WHERE scope=? AND environment=?",arrayOf(scope,sample.value.source.name)).use{it.moveToFirst();it.getString(0)}
                val exists=db.rawQuery("SELECT 1 FROM snapshots WHERE card_id=? AND session=?",arrayOf(card,sample.value.session)).use{it.moveToFirst()}
                if(!exists){
                    val upto=refs.filter{it.rowId<=batch.maxOf{r->r.rowId} && it.storeId==sample.storeId && it.value.source==sample.value.source}
                    val evidence=listOf("EV_SOC","ODOMETER","FUEL_PERCENT").flatMap{kind->
                        val attempt=batch.last{it.value.kind==kind}
                        val valid=upto.lastOrNull{it.value.kind==kind&&accepted(it.value)}
                        listOfNotNull(attempt,valid?.takeIf{it.rowId!=attempt.rowId})
                    }
                    val fp=listOf("EV_SOC","ODOMETER","FUEL_PERCENT").joinToString("|"){kind->val v=batch.last{it.value.kind==kind}.value;"$kind:${v.status}:${v.raw}:${v.unit}"}
                    val prior=db.rawQuery("SELECT fingerprint FROM snapshots WHERE card_id=? ORDER BY rowid DESC LIMIT 1",arrayOf(card)).use{if(it.moveToFirst())it.getString(0)else null}
                    db.insertOrThrow("snapshots",null,ContentValues().apply{put("id",UUID.randomUUID().toString());put("card_id",card);put("session",sample.value.session);put("at",batch.maxOf{it.value.recordedAt});put("evidence",encode(evidence));put("fingerprint",fp);put("changed",if(prior!=null&&prior!=fp)1 else 0)})
                }
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}
        }
    }
    fun cards(hidden:Boolean=false):List<Snapshot> = readableDatabase.rawQuery("SELECT s.* FROM cards c JOIN snapshots s ON s.rowid=(SELECT max(t.rowid) FROM snapshots t WHERE t.card_id=c.id) WHERE c.hidden=? ORDER BY s.rowid DESC",arrayOf(if(hidden)"1"else"0")).use{c->buildList{while(c.moveToNext())add(decode(c))}}
    fun latest():Snapshot?=cards().firstOrNull()
    fun get(id:String):Snapshot?=readableDatabase.rawQuery("SELECT * FROM snapshots WHERE id=?",arrayOf(id)).use{if(it.moveToFirst())decode(it)else null}
    fun history(card:String):List<Snapshot> = readableDatabase.rawQuery("SELECT * FROM snapshots WHERE card_id=? AND changed=1 ORDER BY rowid DESC",arrayOf(card)).use{c->buildList{while(c.moveToNext())add(decode(c))}}
    fun first(card:String):Snapshot?=readableDatabase.rawQuery("SELECT * FROM snapshots WHERE card_id=? ORDER BY rowid LIMIT 1",arrayOf(card)).use{if(it.moveToFirst())decode(it)else null}
    /** Presentation pagination only: no schema, ordering or evidence changes. */
    fun timeline(card:String,limit:Int=3,offset:Int=0):List<Snapshot>{
        require(limit in 1..100 && offset>=0)
        return readableDatabase.rawQuery("SELECT * FROM snapshots WHERE card_id=? ORDER BY rowid DESC LIMIT ? OFFSET ?",arrayOf(card,limit.toString(),offset.toString())).use{c->buildList{while(c.moveToNext())add(decode(c))}}
    }
    fun snapshotCount(card:String):Int=readableDatabase.rawQuery("SELECT count(*) FROM snapshots WHERE card_id=?",arrayOf(card)).use{it.moveToFirst();it.getInt(0)}
    fun hide(card:String,hidden:Boolean){writableDatabase.execSQL("UPDATE cards SET hidden=? WHERE id=?",arrayOf(if(hidden)1 else 0,card))}
    fun select(draft:String,snapshot:String?){
        if(snapshot==null)writableDatabase.delete("selections","draft_id=?",arrayOf(draft))
        else {require(get(snapshot)!=null);writableDatabase.execSQL("INSERT OR REPLACE INTO selections VALUES(?,?)",arrayOf(draft,snapshot))}
    }
    fun selected(draft:String):String?=readableDatabase.rawQuery("SELECT snapshot_id FROM selections WHERE draft_id=?",arrayOf(draft)).use{if(it.moveToFirst())it.getString(0)else null}
    /** Queue before user commit. Orphans never become links; UI can recover the same issue ID. */
    fun prepareLink(issue:String,snapshot:String){
        require(get(snapshot)!=null)
        if(linked(issue)!=null)return // Once committed, later refresh/selection cannot rewrite evidence.
        writableDatabase.execSQL("INSERT OR REPLACE INTO pending_links VALUES(?,?)",arrayOf(issue,snapshot))
    }
    fun completeLink(issue:String){
        val db=writableDatabase;db.beginTransaction();try{
            db.execSQL("INSERT OR IGNORE INTO links SELECT issue_id,snapshot_id,? FROM pending_links WHERE issue_id=?",arrayOf(System.currentTimeMillis(),issue))
            db.delete("pending_links","issue_id=?",arrayOf(issue));db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    fun linked(issue:String):Snapshot?=readableDatabase.rawQuery("SELECT snapshot_id FROM links WHERE issue_id=?",arrayOf(issue)).use{if(it.moveToFirst())get(it.getString(0))else null}
    fun pending(issue:String):Boolean=readableDatabase.rawQuery("SELECT 1 FROM pending_links WHERE issue_id=?",arrayOf(issue)).use{it.moveToFirst()}
    private fun decode(c:android.database.Cursor):Snapshot {
        fun s(n:String)=c.getString(c.getColumnIndexOrThrow(n))
        val a=JSONArray(s("evidence"))
        return Snapshot(s("id"),s("card_id"),c.getLong(c.getColumnIndexOrThrow("at")),(0 until a.length()).map{i->val j=a.getJSONObject(i)
            ObservationRef(j.getString("store"),j.getLong("row"),VehicleObservation(j.getString("kind"),if(j.isNull("raw"))null else j.getString("raw"),if(j.isNull("unit"))null else j.getString("unit"),if(j.isNull("received"))null else j.getLong("received"),ObservationSource.valueOf(j.getString("source")),if(j.isNull("supported"))null else j.getBoolean("supported"),j.getBoolean("valid"),j.getString("session"),ObservationStatus.valueOf(j.getString("status")),j.getString("detail"),j.getLong("recorded")))
        },c.getInt(c.getColumnIndexOrThrow("changed"))==1)
    }
    private fun encode(refs:List<ObservationRef>):String=JSONArray().apply{refs.forEach{r->val v=r.value;put(JSONObject().apply{put("store",r.storeId);put("row",r.rowId);put("kind",v.kind);put("raw",v.raw?:JSONObject.NULL);put("unit",v.unit?:JSONObject.NULL);put("received",v.receivedAt?:JSONObject.NULL);put("source",v.source.name);put("supported",v.supported?:JSONObject.NULL);put("valid",v.valid);put("session",v.session);put("status",v.status.name);put("detail",v.detail);put("recorded",v.recordedAt)})}}.toString()
    companion object {
        fun accepted(v:VehicleObservation):Boolean {
            val n=v.raw?.toDoubleOrNull()?:return false
            return v.status==ObservationStatus.RECEIVED&&v.valid&&v.supported==true&&v.receivedAt!=null&&n.isFinite()&&n>=0 && when(v.kind){"ODOMETER"->v.unit=="km";else->v.unit=="%"&&n<=100}
        }
    }
}
