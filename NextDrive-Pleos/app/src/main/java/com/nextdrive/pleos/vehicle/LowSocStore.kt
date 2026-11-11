package com.nextdrive.pleos.vehicle

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

data class SocPolicy(val enabled:Boolean,val threshold:Int,val version:Int,val since:Long,val store:String)
data class SocNotice(val id:String,val phase:String,val threshold:Int,val version:Int,val entered:Long,val last:Long,val value:String,val unit:String,val source:String,val store:String,val row:Long)
data class SocChange(val episode:String,val kind:String,val store:String,val row:Long,val at:Long,val raw:String,val threshold:Int,val version:Int)
enum class SocAssessment { NORMAL_INFORMATION, USER_LOW_SOC, CONDITION_EXIT, INVALID_DATA, STALE_OR_INSUFFICIENT }

object SocRule {
    const val VERSION="user-soc-v1"
    const val CLEAR_MARGIN=2
    fun assess(v:VehicleObservation,threshold:Int,active:Boolean,now:Long):SocAssessment {
        require(threshold in 1..95)
        if(v.source !in setOf(ObservationSource.PLEOS_EMULATOR_SDK,ObservationSource.PLEOS_VEHICLE_SDK)||v.status!=ObservationStatus.RECEIVED)return SocAssessment.STALE_OR_INSUFFICIENT
        val number=v.raw?.toDoubleOrNull()
        if(v.kind!="EV_SOC"||v.unit!="%"||!v.valid||v.supported!=true||number==null||!number.isFinite()||number !in 0.0..100.0)return SocAssessment.INVALID_DATA
        val at=v.receivedAt?:return SocAssessment.STALE_OR_INSUFFICIENT
        if(at>now+60000||now-at>=600000)return SocAssessment.STALE_OR_INSUFFICIENT
        return if(number<threshold)SocAssessment.USER_LOW_SOC else if(active&&number>=threshold+CLEAR_MARGIN)SocAssessment.CONDITION_EXIT else SocAssessment.NORMAL_INFORMATION
    }
}

/** New sidecar only. Raw observations and the user's four manual issue states are never written. */
class LowSocStore(context:Context,name:String="caritda-soc-notices.db"):SQLiteOpenHelper(context.applicationContext,name,null,1){
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE policy(id INTEGER PRIMARY KEY CHECK(id=1), enabled INTEGER NOT NULL, threshold INTEGER NOT NULL, version INTEGER NOT NULL, since_row INTEGER NOT NULL, store_id TEXT NOT NULL)")
        db.execSQL("INSERT INTO policy VALUES(1,0,20,1,0,'')")
        db.execSQL("CREATE TABLE evaluations(id INTEGER PRIMARY KEY AUTOINCREMENT, policy_version INTEGER NOT NULL, store_id TEXT NOT NULL, row_id INTEGER NOT NULL, scope TEXT NOT NULL, classification TEXT NOT NULL, rule TEXT NOT NULL, threshold INTEGER NOT NULL, at INTEGER NOT NULL, UNIQUE(policy_version,store_id,row_id))")
        db.execSQL("CREATE TABLE episodes(id TEXT PRIMARY KEY, scope TEXT NOT NULL, policy_version INTEGER NOT NULL, phase TEXT NOT NULL, threshold INTEGER NOT NULL, entered INTEGER NOT NULL, last_at INTEGER NOT NULL, raw TEXT NOT NULL, unit TEXT NOT NULL, source TEXT NOT NULL, store_id TEXT NOT NULL, row_id INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX one_active_soc ON episodes(scope) WHERE phase='LOW'")
        db.execSQL("CREATE TABLE changes(id INTEGER PRIMARY KEY AUTOINCREMENT, episode_id TEXT NOT NULL REFERENCES episodes(id), kind TEXT NOT NULL, store_id TEXT NOT NULL, row_id INTEGER NOT NULL, received_at INTEGER NOT NULL, raw TEXT NOT NULL, unit TEXT NOT NULL, source TEXT NOT NULL, policy_version INTEGER NOT NULL, threshold INTEGER NOT NULL, rule TEXT NOT NULL)")
        db.execSQL("CREATE TABLE gaps(id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, reason TEXT NOT NULL)")
    }
    override fun onConfigure(db:SQLiteDatabase){db.setForeignKeyConstraintsEnabled(true)}
    override fun onUpgrade(db:SQLiteDatabase,o:Int,n:Int){error("기존 자동 안내를 보존하는 이전이 필요해요.")}
    fun policy():SocPolicy=readableDatabase.rawQuery("SELECT * FROM policy WHERE id=1",null).use{it.moveToFirst();SocPolicy(it.getInt(1)==1,it.getInt(2),it.getInt(3),it.getLong(4),it.getString(5))}
    fun configure(enabled:Boolean,threshold:Int,raw:ObservationStore){
        require(threshold in 1..95){"안내 기준은1~95%로 입력해 주세요."}
        val old=policy();val reset=threshold!=old.threshold
        val since=if(reset||enabled&&!old.enabled)raw.refs().lastOrNull()?.rowId?:0L else old.since
        writableDatabase.execSQL("UPDATE policy SET enabled=?,threshold=?,version=?,since_row=?,store_id=? WHERE id=1",arrayOf(if(enabled)1 else 0,threshold,old.version+if(reset)1 else 0,since,raw.identity()))
    }
    /** Idempotent replay after raw commit/derived failure; never evaluate a historical low on enable. */
    fun reconcile(raw:ObservationStore,now:Long=System.currentTimeMillis(),afterWrite:()->Unit={}){
        val p=policy();if(!p.enabled)return
        val clocks=mutableMapOf<String,Long>()
        readableDatabase.rawQuery("SELECT scope,max(last_at) FROM episodes GROUP BY scope",null).use{c->while(c.moveToNext())clocks[c.getString(0)]=c.getLong(1)}
        raw.refs().filter{it.storeId==p.store&&it.rowId>p.since&&it.value.kind=="EV_SOC"}.forEach{ref->
            val v=ref.value;val scope="${ref.storeId}:${v.source}:EV_SOC"
            val at=v.receivedAt;val reversed=at!=null&&clocks[scope]?.let{at<it}==true
            if(DriveCheck.accepted(v)&&at!=null&&at<=now+60000)clocks[scope]=maxOf(clocks[scope]?:0L,at)
            val db=writableDatabase;db.beginTransaction()
            try{
                val seen=db.rawQuery("SELECT 1 FROM evaluations WHERE policy_version=? AND store_id=? AND row_id=?",arrayOf(p.version.toString(),ref.storeId,ref.rowId.toString())).use{it.moveToFirst()}
                if(!seen){
                    val active=db.rawQuery("SELECT id FROM episodes WHERE scope=? AND phase='LOW'",arrayOf(scope)).use{if(it.moveToFirst())it.getString(0)else null}
                    val result=if(reversed)SocAssessment.STALE_OR_INSUFFICIENT else SocRule.assess(v,p.threshold,active!=null,now)
                    db.insertOrThrow("evaluations",null,ContentValues().apply{put("policy_version",p.version);put("store_id",ref.storeId);put("row_id",ref.rowId);put("scope",scope);put("classification",result.name);put("rule",SocRule.VERSION);put("threshold",p.threshold);put("at",now)})
                    if(active!=null&&result==SocAssessment.NORMAL_INFORMATION)db.execSQL("UPDATE episodes SET policy_version=?,threshold=? WHERE id=?",arrayOf(p.version,p.threshold,active))
                    if(result==SocAssessment.USER_LOW_SOC||result==SocAssessment.CONDITION_EXIT){
                        val id=active?:UUID.randomUUID().toString()
                        if(active==null){
                            db.insertOrThrow("episodes",null,ContentValues().apply{put("id",id);put("scope",scope);put("policy_version",p.version);put("phase","LOW");put("threshold",p.threshold);put("entered",v.receivedAt);put("last_at",v.receivedAt);put("raw",v.raw);put("unit",v.unit);put("source",v.source.name);put("store_id",ref.storeId);put("row_id",ref.rowId)})
                        }
                        db.execSQL("UPDATE episodes SET phase=?,last_at=?,raw=?,store_id=?,row_id=?,policy_version=?,threshold=? WHERE id=?",arrayOf(if(result==SocAssessment.CONDITION_EXIT)"EXITED"else"LOW",v.receivedAt,v.raw,ref.storeId,ref.rowId,p.version,p.threshold,id))
                        if(active==null||result==SocAssessment.CONDITION_EXIT)db.insertOrThrow("changes",null,ContentValues().apply{put("episode_id",id);put("kind",if(result==SocAssessment.CONDITION_EXIT)"CONDITION_EXIT"else"USER_LOW_SOC");put("store_id",ref.storeId);put("row_id",ref.rowId);put("received_at",v.receivedAt);put("raw",v.raw);put("unit",v.unit);put("source",v.source.name);put("policy_version",p.version);put("threshold",p.threshold);put("rule",SocRule.VERSION)})
                    }
                    afterWrite()
                }
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}
        }
    }
    fun gap(reason:String){writableDatabase.insertOrThrow("gaps",null,ContentValues().apply{put("at",System.currentTimeMillis());put("reason",reason)})}
    fun notices():List<SocNotice> = readableDatabase.rawQuery("SELECT * FROM episodes ORDER BY last_at DESC,entered DESC,rowid DESC",null).use{c->buildList{while(c.moveToNext()){
        fun s(k:String)=c.getString(c.getColumnIndexOrThrow(k))
        fun l(k:String)=c.getLong(c.getColumnIndexOrThrow(k))
        add(SocNotice(s("id"),s("phase"),c.getInt(c.getColumnIndexOrThrow("threshold")),c.getInt(c.getColumnIndexOrThrow("policy_version")),l("entered"),l("last_at"),s("raw"),s("unit"),s("source"),s("store_id"),l("row_id")))
    }}}
    fun changeCount():Int=readableDatabase.rawQuery("SELECT count(*) FROM changes",null).use{it.moveToFirst();it.getInt(0)}
    /** Read-only projection of immutable entry/exit evidence; not another threshold detector. */
    fun changes():List<SocChange> = readableDatabase.rawQuery("SELECT episode_id,kind,store_id,row_id,received_at,raw,threshold,policy_version FROM changes ORDER BY id",null).use{c->buildList{while(c.moveToNext())add(SocChange(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getString(5),c.getInt(6),c.getInt(7)))}}
    fun gaps():List<Long> = readableDatabase.rawQuery("SELECT at FROM gaps ORDER BY id",null).use{c->buildList{while(c.moveToNext())add(c.getLong(0))}}
}
