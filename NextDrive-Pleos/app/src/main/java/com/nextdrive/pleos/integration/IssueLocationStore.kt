package com.nextdrive.pleos.integration

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class IssueLocation(val latitude:Double,val longitude:Double,val measuredAt:Long,val receivedAt:Long,val source:String){
    fun valid()=latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0&&receivedAt>0&&measuredAt>0&&measuredAt<=receivedAt+60000&&source.isNotBlank()
}
/** Optional local association only. Does not migrate or modify issue/observation databases. */
class IssueLocationStore(context:Context,name:String="caritda-issue-location.db"):SQLiteOpenHelper(context,name,null,1){
    override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE links(id TEXT PRIMARY KEY,issue_id TEXT NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,measured_at INTEGER NOT NULL,received_at INTEGER NOT NULL,source TEXT NOT NULL)")}
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){error("Unsupported location migration")}
    fun save(issueId:String,value:IssueLocation):Boolean{
        require(issueId.isNotBlank()&&value.valid());val db=writableDatabase;db.beginTransaction()
        try{
            val same=db.rawQuery("SELECT 1 FROM links WHERE issue_id=? AND latitude=? AND longitude=? AND measured_at=? AND received_at=? AND source=?",arrayOf(issueId,value.latitude.toString(),value.longitude.toString(),value.measuredAt.toString(),value.receivedAt.toString(),value.source)).use{it.moveToFirst()}
            if(!same){
                val row=ContentValues().apply{put("id",UUID.randomUUID().toString());put("issue_id",issueId);put("latitude",value.latitude);put("longitude",value.longitude);put("measured_at",value.measuredAt);put("received_at",value.receivedAt);put("source",value.source)}
                check(db.insertOrThrow("links",null,row)>0)
            }
            db.setTransactionSuccessful();return !same
        }finally{db.endTransaction()}
    }
    fun latest(issueId:String):IssueLocation?=readableDatabase.rawQuery("SELECT latitude,longitude,measured_at,received_at,source FROM links WHERE issue_id=? ORDER BY received_at DESC,rowid DESC LIMIT 1",arrayOf(issueId)).use{if(!it.moveToFirst())null else IssueLocation(it.getDouble(0),it.getDouble(1),it.getLong(2),it.getLong(3),it.getString(4))}
    fun count(issueId:String):Int=readableDatabase.rawQuery("SELECT count(*) FROM links WHERE issue_id=?",arrayOf(issueId)).use{it.moveToFirst();it.getInt(0)}
    fun contains(issueId:String,value:IssueLocation)=readableDatabase.rawQuery("SELECT 1 FROM links WHERE issue_id=? AND latitude=? AND longitude=? AND measured_at=? AND received_at=? AND source=?",arrayOf(issueId,value.latitude.toString(),value.longitude.toString(),value.measuredAt.toString(),value.receivedAt.toString(),value.source)).use{it.moveToFirst()}
    fun history(issueId:String,limit:Int=20):List<IssueLocation>{require(limit in 1..200);return readableDatabase.rawQuery("SELECT latitude,longitude,measured_at,received_at,source FROM links WHERE issue_id=? ORDER BY received_at DESC,rowid DESC LIMIT ?",arrayOf(issueId,limit.toString())).use{c->buildList{while(c.moveToNext())add(IssueLocation(c.getDouble(0),c.getDouble(1),c.getLong(2),c.getLong(3),c.getString(4)))}}}
}
