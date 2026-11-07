package com.nextdrive.pleos.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nextdrive.pleos.model.*

class SqliteIssueRepository(context: Context, databaseName: String = "caritda_v01.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 1), IssueRepository {

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE issues (
            id TEXT PRIMARY KEY NOT NULL, symptom_type TEXT NOT NULL, situation TEXT NOT NULL,
            discovered_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL, reporter TEXT NOT NULL,
            memo TEXT NOT NULL, status TEXT NOT NULL, revision INTEGER NOT NULL, updated_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE events (
            _id INTEGER PRIMARY KEY AUTOINCREMENT, issue_id TEXT NOT NULL REFERENCES issues(id),
            kind TEXT NOT NULL, actor TEXT NOT NULL, at INTEGER NOT NULL, body TEXT NOT NULL,
            revision INTEGER NOT NULL, status TEXT)""")
        db.execSQL("CREATE INDEX events_by_issue ON events(issue_id, _id)")
        db.execSQL("""CREATE UNIQUE INDEX one_ack_per_revision ON events(issue_id, actor, revision)
            WHERE kind = 'ACKNOWLEDGED'""")
        db.execSQL("CREATE TABLE settings (name TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO settings(name,value) VALUES('driver','A')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop existing records as a migration fallback.
        error("지원되지 않는 데이터 버전입니다. 기존 기록을 보존했습니다.")
    }
    override fun currentDriver(): Driver = readableDatabase.rawQuery(
        "SELECT value FROM settings WHERE name='driver'", null
    ).use { check(it.moveToFirst()); Driver.valueOf(it.getString(0)) }

    override fun selectDriver(driver: Driver) {
        val values = ContentValues().apply { put("value", driver.name) }
        check(writableDatabase.update("settings", values, "name=?", arrayOf("driver")) == 1)
    }
    override fun issues(): List<VehicleIssue> = readableDatabase.rawQuery(
        "SELECT * FROM issues ORDER BY updated_at DESC, id", null
    ).use { c -> buildList { while (c.moveToNext()) add(readIssue(c)) } }

    override fun issue(id: String): VehicleIssue? = readableDatabase.rawQuery(
        "SELECT * FROM issues WHERE id=?", arrayOf(id)
    ).use { if (it.moveToFirst()) readIssue(it) else null }

    override fun events(id: String): List<IssueEvent> = readableDatabase.rawQuery(
        "SELECT * FROM events WHERE issue_id=? ORDER BY _id", arrayOf(id)
    ).use { c -> buildList { while (c.moveToNext()) {
        add(IssueEvent(
            EventKind.valueOf(c.string("kind")), Driver.valueOf(c.string("actor")),
            c.number("at"), c.string("body"), c.getInt(c.getColumnIndexOrThrow("revision")),
            c.getString(c.getColumnIndexOrThrow("status"))?.let(IssueStatus::valueOf)
        ))
    } } }

    override fun create(issue: VehicleIssue, initialEvent: IssueEvent) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("issues", null, ContentValues().apply {
                put("id", issue.id); put("symptom_type", issue.type.name); put("situation", issue.situation)
                put("discovered_at", issue.discoveredAt); put("recorded_at", issue.recordedAt)
                put("reporter", issue.reporter.name); put("memo", issue.memo); put("status", issue.status.name)
                put("revision", issue.revision); put("updated_at", issue.updatedAt)
            })
            db.insertOrThrow("events", null, eventValues(issue.id, initialEvent))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun append(id: String, event: IssueEvent, newStatus: IssueStatus?, recurrence: Boolean): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = issue(id) ?: error("기록을 찾을 수 없습니다.")
            check(event.revision == current.revision + if (recurrence) 1 else 0) { "기록이 변경되었습니다. 다시 확인해 주세요." }
            if (event.kind == EventKind.ACKNOWLEDGED && events(id).any {
                it.kind == EventKind.ACKNOWLEDGED && it.actor == event.actor && it.revision == event.revision
            }) {
                db.setTransactionSuccessful()
                return false
            }
            db.insertOrThrow("events", null, eventValues(id, event))
            val changes = ContentValues().apply {
                put("updated_at", event.at); put("revision", event.revision)
                put("status", (newStatus ?: current.status).name)
            }
            check(db.update("issues", changes, "id=?", arrayOf(id)) == 1)
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }
    private fun eventValues(id: String, e: IssueEvent) = ContentValues().apply {
        put("issue_id", id); put("kind", e.kind.name); put("actor", e.actor.name)
        put("at", e.at); put("body", e.text); put("revision", e.revision)
        if (e.status == null) putNull("status") else put("status", e.status.name)
    }
    private fun readIssue(c: Cursor) = VehicleIssue(
        c.string("id"), SymptomType.valueOf(c.string("symptom_type")), c.string("situation"),
        c.number("discovered_at"), c.number("recorded_at"), Driver.valueOf(c.string("reporter")),
        c.string("memo"), IssueStatus.valueOf(c.string("status")),
        c.getInt(c.getColumnIndexOrThrow("revision")), c.number("updated_at")
    )
    private fun Cursor.string(name: String) = getString(getColumnIndexOrThrow(name))
    private fun Cursor.number(name: String) = getLong(getColumnIndexOrThrow(name))
}
