package com.nextdrive.pleos.vehicle

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

enum class ObservationStatus { RECEIVED, SDK_UNAVAILABLE, UNSUPPORTED, PERMISSION_DENIED, CONNECTION_FAILED, TIMEOUT, INVALID }
enum class ObservationSource { PLEOS_EMULATOR_SDK, PLEOS_VEHICLE_SDK, TEST_ONLY, NONE }

data class VehicleObservation(
    val kind: String, val raw: String?, val unit: String?, val receivedAt: Long?,
    val source: ObservationSource, val supported: Boolean?, val valid: Boolean,
    val session: String, val status: ObservationStatus, val detail: String,
    val recordedAt: Long = System.currentTimeMillis(), val driverId: String? = null,
)

data class ObservationRef(val storeId:String,val rowId:Long,val value:VehicleObservation)

/** Only adds an identity table; every old observation column and row remains unchanged. */
object ObservationMigration {
    fun migrate(db:SQLiteDatabase,afterCreate:()->Unit={}) {
        db.execSQL("CREATE TABLE IF NOT EXISTS observation_identity (singleton INTEGER PRIMARY KEY CHECK(singleton=1), store_id TEXT NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO observation_identity VALUES(1,?)",arrayOf(UUID.randomUUID().toString()))
        afterCreate()
    }
}

/** Separate append-only database: the v0.1 incident schema and records remain untouched. */
class ObservationStore(context: Context, name: String = "caritda-observations.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE observations (
            id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, raw TEXT, unit TEXT,
            received_at INTEGER, source TEXT NOT NULL, supported INTEGER, valid INTEGER NOT NULL,
            session TEXT NOT NULL, status TEXT NOT NULL, detail TEXT NOT NULL, recorded_at INTEGER NOT NULL,
            driver_id TEXT)""")
        db.execSQL("CREATE INDEX observations_recent ON observations(id DESC)")
        ObservationMigration.migrate(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion==1 && newVersion==2)
        ObservationMigration.migrate(db)
    }
    fun append(value: VehicleObservation) {
        appendRef(value)
    }
    /** Returns the immutable identity of this insertion, never a guessed latest-row ID. */
    fun appendRef(value:VehicleObservation):ObservationRef {
        require(value.kind.isNotBlank() && value.session.isNotBlank())
        if (value.status == ObservationStatus.RECEIVED) {
            require(!value.raw.isNullOrBlank() && !value.unit.isNullOrBlank() && value.receivedAt != null &&
                value.supported == true && value.valid && value.source != ObservationSource.NONE)
        } else {
            require(value.raw == null && value.unit == null && value.receivedAt == null && !value.valid)
        }
        val values = ContentValues().apply {
            put("kind", value.kind); put("raw", value.raw); put("unit", value.unit)
            put("received_at", value.receivedAt); put("source", value.source.name)
            if (value.supported == null) putNull("supported") else put("supported", if (value.supported) 1 else 0)
            put("valid", if (value.valid) 1 else 0); put("session", value.session)
            put("status", value.status.name); put("detail", value.detail)
            put("recorded_at", value.recordedAt); put("driver_id", value.driverId)
        }
        val row=writableDatabase.insertOrThrow("observations", null, values)
        return ObservationRef(identity(),row,value)
    }
    fun latest(limit: Int = 20): List<VehicleObservation> {
        require(limit in 1..100)
        return readableDatabase.rawQuery("SELECT * FROM observations ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            fun s(name: String) = c.getString(c.getColumnIndexOrThrow(name))
            fun number(name: String): Long? { val i = c.getColumnIndexOrThrow(name); return if (c.isNull(i)) null else c.getLong(i) }
            buildList { while (c.moveToNext()) add(VehicleObservation(
                s("kind"), s("raw"), s("unit"), number("received_at"), ObservationSource.valueOf(s("source")),
                number("supported")?.let { it != 0L }, number("valid") == 1L, s("session"),
                ObservationStatus.valueOf(s("status")), s("detail"), number("recorded_at")!!, s("driver_id"),
            )) }
        }
    }
    /** Read-only projection: find historical success even after more than 100 failed attempts. */
    fun mostRecent(kind:String,receivedOnly:Boolean=false):VehicleObservation? {
        return readableDatabase.rawQuery("SELECT * FROM observations WHERE kind=? AND source!='TEST_ONLY'"+
            (if(receivedOnly)" AND status='RECEIVED' AND valid=1 AND supported=1" else "")+" ORDER BY id DESC LIMIT 1",arrayOf(kind)).use { c ->
            if(!c.moveToFirst())null else {
                fun s(name:String)=c.getString(c.getColumnIndexOrThrow(name))
                fun n(name:String):Long? {val i=c.getColumnIndexOrThrow(name);return if(c.isNull(i))null else c.getLong(i)}
                VehicleObservation(s("kind"),s("raw"),s("unit"),n("received_at"),ObservationSource.valueOf(s("source")),n("supported")?.let{it!=0L},n("valid")==1L,
                    s("session"),ObservationStatus.valueOf(s("status")),s("detail"),n("recorded_at")!!,s("driver_id"))
            }
        }
    }
    fun identity():String=readableDatabase.rawQuery("SELECT store_id FROM observation_identity WHERE singleton=1",null).use{it.moveToFirst();it.getString(0)}
    fun lastRow():Long=readableDatabase.rawQuery("SELECT COALESCE(MAX(id),0) FROM observations",null).use{it.moveToFirst();it.getLong(0)}
    /** Bounded, read-only analysis query. Call from a worker, never from graph redraw. */
    fun analysisRefs(kind:String,since:Long,limit:Int=2001):List<ObservationRef> {
        require(kind in setOf("EV_SOC","ODOMETER")&&limit in 1..2001)
        val store=identity()
        return readableDatabase.rawQuery("SELECT * FROM observations WHERE kind=? AND recorded_at>=? ORDER BY id DESC LIMIT ?",arrayOf(kind,since.toString(),limit.toString())).use{c->
            fun s(n:String)=c.getString(c.getColumnIndexOrThrow(n))
            fun n(n:String):Long?{val i=c.getColumnIndexOrThrow(n);return if(c.isNull(i))null else c.getLong(i)}
            buildList{while(c.moveToNext())add(ObservationRef(store,n("id")!!,VehicleObservation(s("kind"),s("raw"),s("unit"),n("received_at"),ObservationSource.valueOf(s("source")),n("supported")?.let{it!=0L},n("valid")==1L,s("session"),ObservationStatus.valueOf(s("status")),s("detail"),n("recorded_at")!!,s("driver_id"))))}.reversed()
        }
    }
    fun refs():List<ObservationRef> = readableDatabase.rawQuery("SELECT * FROM observations ORDER BY id",null).use{c->
        val store=identity()
        fun s(n:String)=c.getString(c.getColumnIndexOrThrow(n))
        fun n(n:String):Long?{val i=c.getColumnIndexOrThrow(n);return if(c.isNull(i))null else c.getLong(i)}
        buildList{while(c.moveToNext())add(ObservationRef(store,n("id")!!,VehicleObservation(s("kind"),s("raw"),s("unit"),n("received_at"),ObservationSource.valueOf(s("source")),n("supported")?.let{it!=0L},n("valid")==1L,s("session"),ObservationStatus.valueOf(s("status")),s("detail"),n("recorded_at")!!,s("driver_id"))))}
    }
    companion object { fun sessionId(): String = UUID.randomUUID().toString() }
}
