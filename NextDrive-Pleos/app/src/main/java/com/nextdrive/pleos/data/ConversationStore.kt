package com.nextdrive.pleos.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nextdrive.pleos.analytics.VehicleAnalytics
import com.nextdrive.pleos.domain.CommitReceipt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private encrypted text; the journal and this sidecar are NOT one transaction.
 * No bearer, password or raw audio. The local driver selector is not strong authentication. */
class ConversationStore(context:Context,private val name:String="caritda-conversations.db"):
    SQLiteOpenHelper(context.applicationContext,name,null,3) {
    data class Message(val id:String,val role:String,val origin:String,val text:String,val at:Long,val revision:Int)
    data class Pending(val id:String,val session:String,val turn:String,val kind:String,val issue:String,val revision:Int)
    override fun onConfigure(db:SQLiteDatabase){db.setForeignKeyConstraintsEnabled(true)}
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("""CREATE TABLE sessions(id TEXT PRIMARY KEY,owner TEXT NOT NULL,account TEXT NOT NULL,
            state TEXT NOT NULL,issue_id TEXT,revision INTEGER NOT NULL DEFAULT 0,alert_id TEXT,
            started INTEGER NOT NULL,ended INTEGER,expires INTEGER NOT NULL,permit TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE turns(id TEXT PRIMARY KEY,session_id TEXT NOT NULL REFERENCES sessions(id),
            seq INTEGER NOT NULL,role TEXT NOT NULL,origin TEXT NOT NULL,body TEXT,digest TEXT NOT NULL,
            key_alias TEXT NOT NULL,at INTEGER NOT NULL,revision INTEGER NOT NULL,parent TEXT,UNIQUE(session_id,seq))""")
        db.execSQL("""CREATE TABLE operations(id TEXT PRIMARY KEY,session_id TEXT NOT NULL REFERENCES sessions(id),
            turn_id TEXT NOT NULL,kind TEXT NOT NULL,state TEXT NOT NULL,code TEXT NOT NULL DEFAULT '',
            digest TEXT NOT NULL DEFAULT '',issue_id TEXT,revision INTEGER,server_operation TEXT,model TEXT,generated_at INTEGER,at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE playbacks(id TEXT PRIMARY KEY,session_id TEXT NOT NULL REFERENCES sessions(id),
            turn_id TEXT NOT NULL,state TEXT NOT NULL,at INTEGER NOT NULL,ended INTEGER)""")
        db.execSQL("""CREATE TABLE links(id TEXT PRIMARY KEY,session_id TEXT NOT NULL REFERENCES sessions(id),
            token TEXT NOT NULL,digest TEXT NOT NULL,owner TEXT NOT NULL,draft_id TEXT,issue_id TEXT,revision INTEGER,state TEXT NOT NULL)""")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){
        check(oldVersion in 1..2&&newVersion==3){"CONVERSATION_VERSION_UNSUPPORTED"}
        for((column,type)in listOf("server_operation" to "TEXT","model" to "TEXT","generated_at" to "INTEGER")){
            val exists=db.rawQuery("PRAGMA table_info(operations)",null).use{c->var found=false;while(c.moveToNext())if(c.getString(1)==column)found=true;found}
            if(!exists)db.execSQL("ALTER TABLE operations ADD COLUMN $column $type")
        }
        val hasDraft=db.rawQuery("PRAGMA table_info(links)",null).use{c->var found=false;while(c.moveToNext())if(c.getString(1)=="draft_id")found=true;found}
        if(!hasDraft)db.execSQL("ALTER TABLE links ADD COLUMN draft_id TEXT")
    }
    fun start(id:String,owner:String,account:String,permit:String,alert:String?=null,now:Long=System.currentTimeMillis()){
        check(owner.isNotBlank());writableDatabase.insertOrThrow("sessions",null,ContentValues().apply{
            put("id",id);put("owner",owner);put("account",account);put("state","ARMING");put("started",now)
            put("expires",now+30L*86400000);put("permit",permit);put("alert_id",alert)
        })
    }
    private fun session(id:String,owner:String?=null,account:String?=null):Pair<String,String> = readableDatabase.rawQuery("SELECT owner,account FROM sessions WHERE id=?",arrayOf(id)).use{
        check(it.moveToFirst()){"SESSION_MISSING"};val pair=it.getString(0) to it.getString(1)
        check((owner==null||owner==pair.first)&&(account==null||account==pair.second)){"HISTORY_SCOPE"};pair
    }
    fun state(id:String,state:String){check(writableDatabase.update("sessions",ContentValues().apply{put("state",state);if(state=="ENDED")put("ended",System.currentTimeMillis())},"id=?",arrayOf(id))==1)}
    fun anchor(id:String,issue:String,revision:Int){check(writableDatabase.update("sessions",ContentValues().apply{put("issue_id",issue);put("revision",revision)},"id=?",arrayOf(id))==1)}
    private fun alias(owner:String,account:String,cloud:Boolean)="caritda.conversation."+VehicleAnalytics.id(name+":"+owner+":"+if(cloud)account else "local")
    private fun key(alias:String,create:Boolean):SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it};check(create){"HISTORY_KEY_MISSING"}
        return KeyGenerator.getInstance("AES","AndroidKeyStore").run{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes("GCM").setEncryptionPaddings("NoPadding").build());generateKey()}
    }
    private fun encrypt(text:String,alias:String,aad:String):String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(alias,true));cipher.updateAAD(aad.toByteArray())
        return Base64.encodeToString(cipher.iv+cipher.doFinal(text.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    }
    private fun decrypt(text:String,alias:String,aad:String):String {
        val bytes=Base64.decode(text,Base64.NO_WRAP);val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(alias,false),GCMParameterSpec(128,bytes.copyOfRange(0,12)));cipher.updateAAD(aad.toByteArray())
        return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }
    fun appendOnce(id:String,session:String,role:String,origin:String,text:String,revision:Int=0,parent:String?=null){
        require(role in setOf("USER","ASSISTANT")&&origin in setOf("ASR","LOCAL_RULE","GEMINI_NEW","GEMINI_SAVED","GEMINI_RECOVERED"))
        require(text.isNotBlank()&&text.length<=12000)
        val db=writableDatabase;db.beginTransaction()
        try{
            val digest=VehicleAnalytics.id(text)
            db.rawQuery("SELECT digest,session_id,role,origin FROM turns WHERE id=?",arrayOf(id)).use{c->if(c.moveToFirst()){
                check(c.getString(0)==digest&&c.getString(1)==session&&c.getString(2)==role&&c.getString(3)==origin){"TURN_CONFLICT"};db.setTransactionSuccessful();return
            }}
            val (owner,account)=session(session);val alias=alias(owner,account,origin.startsWith("GEMINI"))
            val seq=db.rawQuery("SELECT COALESCE(MAX(seq),0)+1 FROM turns WHERE session_id=?",arrayOf(session)).use{it.moveToFirst();it.getInt(0)}
            db.insertOrThrow("turns",null,ContentValues().apply{put("id",id);put("session_id",session);put("seq",seq);put("role",role);put("origin",origin)
                put("body",encrypt(text,alias,session+":"+id));put("digest",digest);put("key_alias",alias);put("at",System.currentTimeMillis());put("revision",revision);put("parent",parent)})
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
    fun messages(session:String,owner:String,account:String):List<Message>{
        session(session,owner,account)
        return readableDatabase.rawQuery("SELECT id,role,origin,body,key_alias,at,revision FROM turns WHERE session_id=? ORDER BY seq",arrayOf(session)).use{c->buildList{
            while(c.moveToNext()){val id=c.getString(0);val text=if(c.isNull(3))"보관 동의가 철회되었거나 보관 기간이 지났어요."else runCatching{decrypt(c.getString(3),c.getString(4),session+":"+id)}.getOrElse{"이전 대화 내용을 복원하지 못했어요."}
                add(Message(id,c.getString(1),c.getString(2),text,c.getLong(5),c.getInt(6)))
            }
        }}
    }
    fun sessions(owner:String,account:String):List<String> = readableDatabase.rawQuery("SELECT id FROM sessions WHERE owner=? AND account=? AND expires>? ORDER BY started DESC LIMIT 30",arrayOf(owner,account,System.currentTimeMillis().toString())).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
    fun dialogueTurns(owner:String,account:String):Set<String> = readableDatabase.rawQuery("SELECT o.turn_id FROM operations o JOIN sessions s ON s.id=o.session_id WHERE s.owner=? AND s.account=? AND o.kind='DIALOGUE'",arrayOf(owner,account)).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
    fun operation(id:String,session:String,turn:String,kind:String,state:String,digest:String="",code:String="",server:String?=null,model:String?=null,generatedAt:Long?=null){
        val db=writableDatabase
        db.execSQL("INSERT OR IGNORE INTO operations(id,session_id,turn_id,kind,state,digest,at,issue_id,revision) SELECT ?,?,?,?,?,?,?,issue_id,revision FROM sessions WHERE id=?",arrayOf(id,session,turn,kind,state,digest,System.currentTimeMillis(),session))
        check(db.update("operations",ContentValues().apply{put("state",state);put("code",code);if(server!=null)put("server_operation",server);if(model!=null)put("model",model);if(generatedAt!=null)put("generated_at",generatedAt)},"id=? AND session_id=? AND turn_id=? AND kind=? AND digest=?",arrayOf(id,session,turn,kind,digest))==1){"OPERATION_CONFLICT"}
    }
    fun activity(session:String,owner:String,account:String):List<String>{
        session(session,owner,account)
        val requests=readableDatabase.rawQuery("SELECT kind,state,code,model FROM operations WHERE session_id=? ORDER BY at",arrayOf(session)).use{c->buildList{while(c.moveToNext())add("${if(c.getString(0)=="ISSUE")"문제 분석"else"후속 질문"} · ${c.getString(1)}"+(c.getString(2).takeIf{it.isNotBlank()}?.let{" · $it"}?:"")+(c.getString(3)?.let{" · $it"}?:""))}}
        val playbacks=readableDatabase.rawQuery("SELECT state FROM playbacks WHERE session_id=? ORDER BY at",arrayOf(session)).use{c->buildList{while(c.moveToNext())add("음성 안내 · "+c.getString(0))}}
        return requests+playbacks
    }
    fun pending(session:String,owner:String,account:String):List<Pending>{
        session(session,owner,account)
        return readableDatabase.rawQuery("SELECT o.id,o.turn_id,o.kind,o.issue_id,o.revision FROM operations o WHERE o.session_id=? AND o.state IN ('PREPARED','UNKNOWN') AND o.issue_id IS NOT NULL ORDER BY o.at",arrayOf(session)).use{c->buildList{while(c.moveToNext())add(Pending(c.getString(0),session,c.getString(1),c.getString(2),c.getString(3),c.getInt(4)))}}
    }
    fun stageLink(session:String,token:String,digest:String,owner:String,draftId:String?=null){
        writableDatabase.execSQL("INSERT OR IGNORE INTO links(id,session_id,token,digest,owner,draft_id,state) VALUES(?,?,?,?,?,?,'PREPARED')",arrayOf(token,session,token,digest,owner,draftId))
        check(readableDatabase.rawQuery("SELECT 1 FROM links WHERE id=? AND session_id=? AND digest=? AND owner=?",arrayOf(token,session,digest,owner)).use{it.moveToFirst()})
    }
    fun link(receipt:CommitReceipt){check(writableDatabase.update("links",ContentValues().apply{put("issue_id",receipt.issueId);put("revision",receipt.revision);put("state","COMMITTED")},"token=? AND digest=? AND owner=?",arrayOf(receipt.token,receipt.digest,receipt.ownerId))==1)}
    fun reconcile(repository:IssueRepository,liveSession:String?=null){
        readableDatabase.rawQuery("SELECT token,digest,owner,draft_id FROM links WHERE state='PREPARED'",null).use{c->while(c.moveToNext()){
            val token=c.getString(0);val r=repository.receipt(token)
            if(r!=null&&r.digest==c.getString(1)&&r.ownerId==c.getString(2))link(r)
            else writableDatabase.update("links",ContentValues().apply{put("state",if(r!=null)"CONFLICT"else if(c.getString(3)!=null&&repository.draft()?.let{it.id==c.getString(3)&&it.ownerId==c.getString(2)}==true)"WAITING_USER_RESUME"else"ORPHANED_NEEDS_REVIEW")},"token=?",arrayOf(token))
        }}
        writableDatabase.execSQL("UPDATE playbacks SET state='UNCERTAIN' WHERE state='STARTED' AND session_id!=?",arrayOf(liveSession.orEmpty()))
        writableDatabase.execSQL("UPDATE sessions SET state='ENDED',ended=? WHERE ended IS NULL AND id!=?",arrayOf(System.currentTimeMillis(),liveSession.orEmpty()))
    }
    fun playback(id:String,session:String,turn:String,state:String){
        val db=writableDatabase
        if(state=="STARTED")db.insertOrThrow("playbacks",null,ContentValues().apply{put("id",id);put("session_id",session);put("turn_id",turn);put("state",state);put("at",System.currentTimeMillis())})
        else check(db.update("playbacks",ContentValues().apply{put("state",state);put("ended",System.currentTimeMillis())},"id=? AND state='STARTED'",arrayOf(id))==1)
    }
    /** Account transition/revocation removes cloud bodies, not journal records or budget state. */
    fun withdrawCloud(account:String,owner:String?=null){
        val predicate="account=?"+if(owner==null)""else" AND owner=?"
        val args=if(owner==null)arrayOf(account)else arrayOf(account,owner)
        val aliases=readableDatabase.rawQuery("SELECT DISTINCT key_alias FROM turns WHERE origin LIKE 'GEMINI%' AND session_id IN (SELECT id FROM sessions WHERE $predicate)",args).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
        writableDatabase.execSQL("UPDATE turns SET body=NULL WHERE origin LIKE 'GEMINI%' AND session_id IN (SELECT id FROM sessions WHERE $predicate)",args);deleteKeys(aliases)
    }
    fun forgetHistory(owner:String,account:String){
        val aliases=readableDatabase.rawQuery("SELECT DISTINCT key_alias FROM turns WHERE session_id IN (SELECT id FROM sessions WHERE owner=? AND account=?)",arrayOf(owner,account)).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
        writableDatabase.execSQL("UPDATE turns SET body=NULL WHERE session_id IN (SELECT id FROM sessions WHERE owner=? AND account=?)",arrayOf(owner,account))
        // Local keys can be shared by this driver's other account sessions; keep keys and remove bodies.
        deleteKeys(aliases.filter{a->readableDatabase.rawQuery("SELECT 1 FROM turns WHERE key_alias=? AND body IS NOT NULL LIMIT 1",arrayOf(a)).use{!it.moveToFirst()}})
    }
    fun expire(now:Long=System.currentTimeMillis()){
        val aliases=readableDatabase.rawQuery("SELECT DISTINCT key_alias FROM turns WHERE body IS NOT NULL AND session_id IN (SELECT id FROM sessions WHERE expires<=?)",arrayOf(now.toString())).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
        writableDatabase.execSQL("UPDATE turns SET body=NULL WHERE session_id IN (SELECT id FROM sessions WHERE expires<=?)",arrayOf(now))
        deleteKeys(aliases.filter{a->readableDatabase.rawQuery("SELECT 1 FROM turns WHERE key_alias=? AND body IS NOT NULL LIMIT 1",arrayOf(a)).use{!it.moveToFirst()}})
    }
    private fun deleteKeys(aliases:List<String>){val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};aliases.forEach{store.deleteEntry(it)}}
}
