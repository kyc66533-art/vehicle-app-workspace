package com.nextdrive.pleos.vehicle

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** Sensor condition, driver acknowledgement, speech delivery and journal status are separate. */
class AttentionEventStore(context:Context,name:String="caritda-attention.db"):SQLiteOpenHelper(context.applicationContext,name,null,1){
    data class Event(val id:String,val kind:String,val phase:String,val store:String,val row:Long,val first:Long,val last:Long,val source:String)
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("""CREATE TABLE episodes(id TEXT PRIMARY KEY,scope TEXT NOT NULL,kind TEXT NOT NULL,phase TEXT NOT NULL,
            store_id TEXT NOT NULL,row_id INTEGER NOT NULL,first_at INTEGER NOT NULL,last_at INTEGER NOT NULL,source TEXT NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX active_attention ON episodes(scope) WHERE phase='ACTIVE'")
        db.execSQL("CREATE TABLE deliveries(id TEXT PRIMARY KEY,episode TEXT NOT NULL,channel TEXT NOT NULL,state TEXT NOT NULL,at INTEGER NOT NULL,ended INTEGER,UNIQUE(episode,channel))")
        db.execSQL("CREATE TABLE acknowledgements(episode TEXT NOT NULL,driver TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(episode,driver))")
        db.execSQL("CREATE TABLE issue_links(episode TEXT NOT NULL,issue TEXT NOT NULL,revision INTEGER NOT NULL,PRIMARY KEY(episode,issue,revision))")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){error("ATTENTION_VERSION_UNSUPPORTED")}
    fun accept(ref:ObservationRef,low:LowSocStore):Event?{
        val v=ref.value;val now=System.currentTimeMillis()
        if(v.kind !in setOf("EV_SOC","ENGINE_OIL","BRAKE_FLUID","WASHER_LOW","POWER_DOWN")||!DriveCheck.accepted(v)||now-v.receivedAt!! !in 0 until 600000)return null
        val scope="${ref.storeId}:${v.source}:${v.kind}";val db=writableDatabase;db.beginTransaction()
        try{
            val prior=events().firstOrNull{it.store==ref.storeId&&it.source==v.source.name&&it.kind==v.kind}
            if(prior!=null&&(v.receivedAt<prior.last||ref.rowId<=prior.row)){db.setTransactionSuccessful();return prior}
            val current=prior?.takeIf{it.phase=="ACTIVE"}
            val soc=if(v.kind=="EV_SOC")low.notices().firstOrNull{it.store==ref.storeId&&it.source==v.source.name&&it.row==ref.rowId}else null
            if(v.kind=="EV_SOC"&&soc==null){db.setTransactionSuccessful();return null}
            val active=if(v.kind=="EV_SOC")soc!!.phase=="LOW"else when(v.kind){"ENGINE_OIL","BRAKE_FLUID"->v.raw in setOf("LOW","HIGH","CRITICALLY_LOW");else->v.raw=="true"}
            val id=if(v.kind=="EV_SOC")soc!!.id else current?.id?:if(active)UUID.randomUUID().toString()else null
            if(id!=null){
                val values=ContentValues().apply{put("id",id);put("scope",scope);put("kind",v.kind);put("phase",if(active)"ACTIVE"else"RECOVERED");put("store_id",ref.storeId);put("row_id",ref.rowId);put("first_at",current?.first?:soc?.entered?:v.receivedAt);put("last_at",v.receivedAt);put("source",v.source.name)}
                if(db.update("episodes",values,"id=?",arrayOf(id))==0)db.insertOrThrow("episodes",null,values)
            }
            db.setTransactionSuccessful();return if(id==null)null else events().first{it.id==id}
        } finally {db.endTransaction()}
    }
    fun events():List<Event> = readableDatabase.rawQuery("SELECT id,kind,phase,store_id,row_id,first_at,last_at,source FROM episodes ORDER BY last_at DESC",null).use{c->buildList{while(c.moveToNext())add(Event(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getLong(6),c.getString(7)))}}
    fun claimSpeech(episode:String,eligible:Boolean):Boolean {
        val id="$episode:auto-voice-v1";val db=writableDatabase
        return db.insertWithOnConflict("deliveries",null,ContentValues().apply{put("id",id);put("episode",episode);put("channel","AUTO_VOICE_V1");put("state",if(eligible)"STARTED"else"SKIPPED");put("at",System.currentTimeMillis())},SQLiteDatabase.CONFLICT_IGNORE)!=-1L&&eligible
    }
    fun finishSpeech(episode:String,state:String){writableDatabase.update("deliveries",ContentValues().apply{put("state",state);put("ended",System.currentTimeMillis())},"episode=? AND state='STARTED'",arrayOf(episode))}
    fun recover(){writableDatabase.execSQL("UPDATE deliveries SET state='UNCERTAIN' WHERE state='STARTED'")}
    fun acknowledge(id:String,driver:String){writableDatabase.execSQL("INSERT OR IGNORE INTO acknowledgements VALUES(?,?,?)",arrayOf(id,driver,System.currentTimeMillis()))}
    fun acknowledged(id:String,driver:String)=readableDatabase.rawQuery("SELECT 1 FROM acknowledgements WHERE episode=? AND driver=?",arrayOf(id,driver)).use{it.moveToFirst()}
    fun link(episode:String,issue:String,revision:Int){writableDatabase.execSQL("INSERT OR IGNORE INTO issue_links VALUES(?,?,?)",arrayOf(episode,issue,revision))}
}
