package com.nextdrive.pleos.data

import android.database.sqlite.SQLiteDatabase

/** Additive v2→v3; the caller owns the transaction. Never deletes or rewrites a user's content. */
object RecordMigration {
    fun migrate(db:SQLiteDatabase,afterColumns:()->Unit={}) {
        fun add(name:String,type:String){
            val exists=db.rawQuery("PRAGMA table_info(issues)",null).use{c->var found=false;while(c.moveToNext())if(c.getString(1)==name)found=true;found}
            if(!exists)db.execSQL("ALTER TABLE issues ADD COLUMN $name $type")
        }
        add("deleted_at","INTEGER")
        add("deleted_by_id","TEXT REFERENCES drivers(id)")
        afterColumns()
        check(db.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()})
    }
}
