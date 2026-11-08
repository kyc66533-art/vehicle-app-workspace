package com.nextdrive.pleos.analytics

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Separate derived store. Never modifies raw observations, notices or manual issue records. */
class AnalysisStore(c:Context,name:String="caritda-analytics.db"):SQLiteOpenHelper(c.applicationContext,name,null,1){
    override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE insights(id TEXT PRIMARY KEY,kind TEXT NOT NULL,first_at INTEGER NOT NULL,last_at INTEGER NOT NULL,rule TEXT NOT NULL,evidence TEXT NOT NULL,facts TEXT NOT NULL)")}
    override fun onUpgrade(db:SQLiteDatabase,o:Int,n:Int){error("분석 이력을 보존하는 이전이 필요해요.")}
    fun remember(a:VehicleAnalysis){val db=writableDatabase;db.beginTransaction();try{a.insights.forEach{i->
        db.insertWithOnConflict("insights",null,ContentValues().apply{put("id",i.id);put("kind",i.kind);put("first_at",i.first);put("last_at",i.last);put("rule",i.rule)
            put("evidence",JSONArray(i.evidence.map{JSONObject().put("store",it.storeId).put("row",it.rowId)}).toString())
            put("facts",JSONObject().put("title",i.title).put("meaning",i.meaning).put("action",i.action).put("quality",JSONObject(a.quality)).put("delta",i.delta).toString())},SQLiteDatabase.CONFLICT_IGNORE)
    };db.setTransactionSuccessful()}finally{db.endTransaction()}}
    fun count()=readableDatabase.rawQuery("SELECT count(*) FROM insights",null).use{it.moveToFirst();it.getInt(0)}
}
