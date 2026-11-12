package com.nextdrive.pleos

import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.voice.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.integration.*
import java.util.UUID

/** Unique fixtures only; does not prepare/delete either existing app database. */
class VoiceAutomationChecks(private val instrumentation:Instrumentation){
    private val ctx=instrumentation.targetContext
    private val lines=mutableListOf<String>()
    private fun ck(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun reject(label:String,action:()->Unit){ck(runCatching{action()}.isFailure,label)}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(ctx)
        val prefix="voice-auto-"+UUID.randomUUID();val dbName="$prefix-journal.db"
        val original=ctx.openOrCreateDatabase(dbName,0,null)
        DriverMigration.createV1(original);DriverMigration.migrate(original);RecordMigration.migrate(original);original.version=3
        original.execSQL("INSERT INTO issues(id,symptom_type,situation,discovered_at,recorded_at,reporter,memo,status,revision,updated_at,reporter_id,reporter_name) VALUES('old','NOISE','보존할 원문',1,1,'A','','PLANNED',1,1,'A','운전자 A')")
        original.close()
        var repo=SqliteIssueRepository(ctx,dbName)
        ck(repo.readableDatabase.version==4&&repo.issue("old")?.situation=="보존할 원문"&&repo.issue("old")?.status==IssueStatus.PLANNED,"v3→v4 additive migration preserves issue/status")
        VoiceCommitMigration.migrate(repo.writableDatabase);ck(repo.issue("old")!=null,"migration repeat idempotent")
        val service=CaritdaService(repo,{true});service.selectDriver(Driver.A)
        val now=System.currentTimeMillis();val d=JournalDraft(UUID.randomUUID().toString(),"A",Driver.A.label,"RECORD",body="차 앞쪽에서 이상한 소리가 나요.",foundAt=now,updatedAt=now)
        service.saveDraft(d);val command=DraftCommit("stable-turn",d);val receipt=service.commitDraft(command)
        ck(repo.issues().size==2&&repo.events(receipt.issueId).count{it.kind==EventKind.CREATED}==1&&repo.draft()==null,"atomic issue/event/receipt and draft consumption")
        ck(service.commitDraft(command)==receipt&&repo.issues().size==2,"same token/digest returns same receipt after consumed draft")
        reject("different text with same token conflicts"){service.commitDraft(DraftCommit(command.token,d.copy(body="다른 증상이에요.")))}
        val bad=d.copy(id=UUID.randomUUID().toString());service.saveDraft(bad);repo.selectDriver(Driver.B)
        reject("transaction rechecks current driver"){repo.commitDraft(DraftCommit("driver-change",bad),now)}
        ck(repo.draft()?.id==bad.id&&repo.receipt("driver-change")==null&&repo.issues().size==2,"failed transaction preserves draft and all original records")
        repo.selectDriver(Driver.A);service.discardDraft(bad.id)
        repo.close();repo=SqliteIssueRepository(ctx,dbName)
        ck(repo.receipt(command.token)==receipt&&repo.issue("old")?.situation=="보존할 원문","receipt and original text survive reopened database")
        ck(repo.readableDatabase.rawQuery("PRAGMA integrity_check",null).use{it.moveToFirst();it.getString(0)=="ok"}&&repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()},"journal integrity and foreign keys")
        service.selectDriver(Driver.B);val serviceB=CaritdaService(repo,{true});serviceB.selectDriver(Driver.B)
        ck(serviceB.needsAcknowledgement(repo.issue(receipt.issueId)!!),"A voice record is handed to B")
        ck(serviceB.acknowledge(receipt.issueId)&&!serviceB.acknowledge(receipt.issueId),"B acknowledgement idempotent")
        ck(repo.issue(receipt.issueId)?.status==IssueStatus.UNRESOLVED,"AI/history/ack never resolves issue")
        val conv=ConversationStore(ctx,"$prefix-conversations.db");val session=UUID.randomUUID().toString()
        conv.start(session,"A","account-fixture","permit-v1");conv.stageLink(session,receipt.token,receipt.digest,"A");conv.reconcile(repo)
        ck(conv.readableDatabase.rawQuery("SELECT state FROM links WHERE token=?",arrayOf(receipt.token)).use{it.moveToFirst();it.getString(0)=="COMMITTED"},"cross-database receipt repair without creating another issue")
        conv.appendOnce("u1",session,"USER","ASR","보존할 음성 원문")
        conv.appendOnce("u1",session,"USER","ASR","보존할 음성 원문")
        conv.appendOnce("a1",session,"ASSISTANT","GEMINI_NEW","가능성이 있는 점검 참고예요.")
        ck(conv.messages(session,"A","account-fixture").size==2,"message appendOnce and chronological history")
        reject("duplicate event with changed content rejected"){conv.appendOnce("u1",session,"USER","ASR","바뀐 원문")}
        ck(conv.readableDatabase.rawQuery("SELECT body FROM turns WHERE id='u1'",null).use{it.moveToFirst();!it.getString(0).contains("음성")},"SQLite bodies encrypted at rest")
        reject("other driver cannot read private conversation"){conv.messages(session,"B","account-fixture")}
        reject("other Firebase account cannot read conversation"){conv.messages(session,"A","another-account")}
        conv.playback("play1",session,"a1","STARTED");conv.reconcile(repo)
        ck(conv.readableDatabase.rawQuery("SELECT state FROM playbacks WHERE id='play1'",null).use{it.moveToFirst();it.getString(0)=="UNCERTAIN"},"crash during speech becomes UNCERTAIN; no automatic replay")
        conv.withdrawCloud("account-fixture")
        ck(conv.messages(session,"A","account-fixture").first().text=="보존할 음성 원문"&&conv.messages(session,"A","account-fixture").last().text.contains("철회"),"cloud withdrawal removes AI copy while local transcript and journal survive")
        conv.forgetHistory("A","account-fixture");ck(conv.messages(session,"A","account-fixture").all{it.text.contains("철회")},"history withdrawal removes conversation bodies only")
        val other=UUID.randomUUID().toString();conv.start(other,"B","account-fixture","permit-v1");conv.appendOnce("other-ai",other,"ASSISTANT","GEMINI_NEW","다른 운전자의 점검 참고예요.")
        conv.withdrawCloud("account-fixture","A");ck(conv.messages(other,"B","account-fixture").single().text.contains("다른 운전자"),"one driver withdrawal preserves another driver's private AI history")
        conv.anchor(session,"pinned-original",1);conv.operation("pending-pin",session,"pin-turn","DIALOGUE","PREPARED")
        conv.anchor(session,"different-issue",2)
        ck(conv.pending(session,"A","account-fixture").single().issue=="pinned-original","unfinished operation never follows a later session anchor")
        val permits=VoiceSettings(ctx,"$prefix-settings")
        ck(permits.read("A","account-fixture")==VoiceSettings.Permit(),"all automation scopes default off")
        permits.write("A","account-fixture",VoiceSettings.Permit(true,true,true,true,true,true))
        ck(permits.read("A","account-fixture").cloud&&!permits.read("B","account-fixture").cloud&&!permits.read("A","other").cloud,"permits bound to driver and account")
        router()
        controller(prefix,repo)
        attention(prefix)
        contracts(repo.issue(receipt.issueId)!!)
        conv.close();repo.close();return lines
    }
    private fun router(){
        ck(VoiceCommandRouter.route("카잇다, 차 앞쪽에서 이상한 소리가 나.",true,false) is VoiceCommand.Record,"approved symptom mode routes explicit symptom")
        ck(VoiceCommandRouter.route("차 앞쪽에서 소리가 나. 기록해 줘.",false,false) is VoiceCommand.Record,"explicit save command works without implicit symptom mode")
        for(text in listOf("안녕하세요","기록하지 마세요","차에서 소리가 난다고 말했어요","차 앞쪽에서 소리가 나", "카잇다, 기록할까요?"))ck(VoiceCommandRouter.route(text,false,false) !is VoiceCommand.Record,"no unintended record: fixture-${lines.size}")
        ck(VoiceCommandRouter.route("카잇다",true,false)==VoiceCommand.Wake,"already listening wake prefix distinguished")
        ck(VoiceCommandRouter.route("배터리 얼마나 남았어?",true,true)==VoiceCommand.Soc,"SOC is local command")
        ck(VoiceCommandRouter.route("그럼 어떻게 해야 해?",true,false)==VoiceCommand.Clarify,"ambiguous follow-up cannot silently select another issue")
        ck(VoiceCommandRouter.route("그럼 어떻게 해야 해?",true,true) is VoiceCommand.FollowUp,"pinned issue accepts new question")
        ck(VoiceCommandRouter.route("차 앞쪽에서 소리가 나지만 기록해 주지 마.",true,true)==VoiceCommand.Clarify,"negative recording command is never an automatic symptom write")
        ck(VoiceCommandRouter.route("차 앞쪽 소리는 왜 나는 거야?",true,true) is VoiceCommand.FollowUp,"follow-up about same symptom is not stored as another problem")
        ck(VoiceCommandRouter.route("차 앞쪽 소리 "+"가".repeat(1000),true,false)==VoiceCommand.Clarify,"oversize raw transcript not silently truncated")
    }
    private fun controller(prefix:String,repo:SqliteIssueRepository){
        val store=ConversationStore(ctx,"$prefix-controller.db");val service=CaritdaService(repo,{true});service.selectDriver(Driver.A)
        var valid=true;var scope="stable";var speaks=true;var accepts:((String)->Unit)?=null;var speakDone:((PlaybackOutcome)->Unit)?=null
        var aiDone:((Result<VoiceConversationController.Reply>)->Unit)?=null
        var saves=0;var ai=0;var dialogues=0;var listens=0;var stop=0
        lateinit var controller:VoiceConversationController
        val port=object:VoiceConversationController.Ports{
            override fun valid()=valid
            override fun scope()=scope
            override fun permit()=VoiceSettings.Permit(true,true,speaks,true,true,true)
            override fun owner()="A"
            override fun account()="fixture"
            override fun listen(accepted:(String)->Unit,failed:(String)->Unit){listens++;accepts=accepted}
            override fun stopAudio(){stop++}
            override fun save(token:String,text:String,type:SymptomType):CommitReceipt{
                saves++;val n=System.currentTimeMillis();val d=JournalDraft(UUID.randomUUID().toString(),"A",Driver.A.label,"RECORD",type=type,body=text,foundAt=n,updatedAt=n)
                val c=DraftCommit(token,d);store.stageLink(controller.sessionId!!,token,c.digest,"A");service.saveDraft(d);return service.commitDraft(c)
            }
            override fun analyze(issue:String,revision:Int,turn:String,done:(Result<VoiceConversationController.Reply>)->Unit){ai++;aiDone=done}
            override fun dialogue(issue:String,revision:Int,turn:String,question:String,done:(Result<VoiceConversationController.Reply>)->Unit){dialogues++;aiDone=done}
            override fun query(command:VoiceCommand,issue:String?,alert:String?)="최근 수신 충전량은 테스트 fixture 값이에요."
            override fun speak(text:String,done:(PlaybackOutcome)->Unit){speakDone=done}
            override fun changed(state:VoiceConversationController.State,message:String){}
        }
        controller=VoiceConversationController(store,port);controller.start();val callback=accepts!!;callback("카잇다, 차 앞쪽에서 이상한 소리가 나.");callback("카잇다, 차 앞쪽에서 이상한 소리가 나.")
        ck(saves==1&&ai==1&&controller.state==VoiceConversationController.State.AI_WAITING,"final callback once: one receipt then one AI request, no save/generate button")
        val pinned=controller.issueId;aiDone!!(Result.success(VoiceConversationController.Reply("새 설명 fixture예요.","GEMINI_NEW")))
        ck(controller.state==VoiceConversationController.State.SPEAKING&&listens==1,"AI result automatically starts TTS; STT stays stopped")
        speakDone!!(PlaybackOutcome.DONE);ck(listens==2,"actual complete port DONE starts follow-up STT")
        accepts!!("그럼 어떻게 해야 해?");ck(dialogues==1&&controller.issueId==pinned,"new dialogue request retains same issue/session")
        aiDone!!(Result.failure(PortableFailure("MODEL_HTTP_503")));ck(repo.issue(pinned!!)!=null&&controller.lastReply.contains("새 AI 설명"),"provider503 preserves issue and uses labelled local fallback")
        speakDone!!(PlaybackOutcome.CANCELLED);ck(controller.state==VoiceConversationController.State.ERROR_PAUSED&&listens==2,"cancelled TTS never starts another microphone")
        controller.end();controller.start();val late=accepts!!;scope="changed-account";late("차 앞쪽에서 소리가 나. 기록해 줘.")
        ck(saves==1,"account/scope change blocks late ASR write")
        scope="stable";controller.end();controller.start();val old=accepts!!;controller.end();old("차 앞쪽에서 소리가 나. 기록해 줘.")
        ck(saves==1&&stop>0,"ended session ignores old callbacks and stops audio")
        valid=false;controller.start();ck(controller.state==VoiceConversationController.State.ENDED,"restricted/background cannot start session")
        valid=true;speaks=false;controller.start();val before=listens;accepts!!("배터리 얼마나 남았어?")
        ck(listens==before+1&&controller.state==VoiceConversationController.State.LISTENING,"permitted follow-up continues after local text answer when automatic TTS is off")
        controller.end()
        store.close()
    }
    private fun attention(prefix:String){
        val raw=ObservationStore(ctx,"$prefix-observations.db");val low=LowSocStore(ctx,"$prefix-soc.db");val events=AttentionEventStore(ctx,"$prefix-attention.db");low.configure(true,20,raw)
        fun soc(n:Int):AttentionEventStore.Event?{val now=System.currentTimeMillis();val ref=raw.appendRef(VehicleObservation("EV_SOC",n.toString(),"%",now,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"fixture",ObservationStatus.RECEIVED,"SYNTHETIC_FIXTURE"));low.reconcile(raw);return events.accept(ref,low)}
        soc(100);val first=soc(18)!!;val repeated=soc(18)!!
        ck(first.id==repeated.id&&events.events().size==1&&first.id==low.notices().first().id,"SOC mirrors existing stable episode; repeated callback no duplicate")
        ck(events.claimSpeech(first.id,true)&&!events.claimSpeech(first.id,true),"automatic speech at most one attempt per episode")
        events.recover();ck(!events.claimSpeech(first.id,true),"crashed STARTED delivery never automatically replays")
        events.acknowledge(first.id,"B");ck(events.events().first().phase=="ACTIVE"&&events.acknowledged(first.id,"B")&&!events.acknowledged(first.id,"A"),"driver acknowledgement is scoped and does not clear sensor condition")
        soc(21);ck(events.events().first().phase=="ACTIVE","SOC clear margin retained")
        soc(45);ck(events.events().first().phase=="RECOVERED","fresh clear updates episode without changing a journal state")
        val invalid=raw.appendRef(VehicleObservation("POWER_DOWN",null,null,null,ObservationSource.PLEOS_EMULATOR_SDK,null,false,"fixture",ObservationStatus.TIMEOUT,"SYNTHETIC_FIXTURE"))
        ck(events.accept(invalid,low)==null,"timeout/unsupported never fabricates condition or recovery")
        val now=System.currentTimeMillis();val warning=raw.appendRef(VehicleObservation("WASHER_LOW","true","state",now,ObservationSource.PLEOS_EMULATOR_SDK,true,true,"fixture",ObservationStatus.RECEIVED,"SYNTHETIC_FIXTURE"))
        val washer=events.accept(warning,low)!!;ck(washer.phase=="ACTIVE","valid warning fixture maps to separate vehicle notice")
        ck(events.accept(warning,low)?.id==washer.id,"same observation cannot create another safety episode")
        raw.close();low.close();events.close()
    }
    private fun contracts(issue:VehicleIssue){
        val payload=IssueDialogueContract.payload(issue,"그럼 어떻게 해야 해요?",listOf(IssueDialogueContract.Turn("USER","차 앞쪽에서 소리가 나요."),IssueDialogueContract.Turn("ASSISTANT","여러 원인이 있을 수 있어요.")))
        ck(payload.contains("ASSISTANT_INFERENCE")&&!payload.contains(issue.id)&&!payload.contains("reporter"),"dialogue projection excludes ID, driver, memo, location, sensors")
        val answer=org.json.JSONObject().put("contract",IssueDialogueContract.CONTRACT).put("operation_id","fixture-operation").put("origin","MODEL").put("model",PortableAnalysis.MODEL)
            .put("evidence_digest",com.nextdrive.pleos.analytics.VehicleAnalytics.id(payload)).put("facts",org.json.JSONObject(payload)).put("evidence_quote",issue.situation.take(3)).put("context_refs",org.json.JSONArray(listOf(0,1)))
            .put("answer","앞서 남긴 소음의 상황을 다시 살펴봐 주세요.").put("checks","정차한 뒤 증상을 관찰해 주세요.").put("inspection","반복되면 정비소 점검을 고려해 주세요.").put("uncertainty","기록만으로 원인을 확인할 수 없어요.").put("generated_at",System.currentTimeMillis())
        ck(IssueDialogueContract.validate(org.json.JSONObject(answer.toString()),payload,"fixture-operation").contains("앞서"),"dialogue facts arrays validate structurally after JSON round trip")
        val changed=org.json.JSONObject(answer.toString());changed.getJSONObject("facts").getJSONArray("context_turns").getJSONObject(1).put("text","변조한 이전 응답이에요.")
        reject("altered previous dialogue context is rejected"){IssueDialogueContract.validate(changed,payload,"fixture-operation")}
        reject("phone/private data rejected before model"){IssueDialogueContract.payload(issue,"전화 010-1234-5678",emptyList())}
        reject("byte budget rejects rather than clips prior context"){IssueDialogueContract.payload(issue,"정비소에 가야 할까요?",List(4){IssueDialogueContract.Turn(if(it%2==0)"USER"else"ASSISTANT","가".repeat(500))})}
        val pending=org.json.JSONObject().put("intent","stable-intent").put("operation","stable-op").put("state","ISSUED")
        ck(VoiceRateWindow.mayWait("RATE_LIMIT",pending),"unreserved same intent can wait for existing minute limit")
        ck(!VoiceRateWindow.mayWait("MODEL_HTTP_503",pending)&&!VoiceRateWindow.mayWait("RATE_LIMIT",org.json.JSONObject(pending.toString()).put("state","UNKNOWN"))&&!VoiceRateWindow.mayWait("RATE_LIMIT",org.json.JSONObject(pending.toString()).put("state","DISPATCHING")),"no automatic retry for paid provider failure or uncertain dispatch")
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val thread=Thread{PortableOperationLock.run{entered.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS)}};thread.start();check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
        val cleaned=java.util.concurrent.atomic.AtomicBoolean(false)
        try{
            val failure=runCatching{PortableOperationLock.run{true}}.exceptionOrNull()
            ck(failure is PortableFailure&&failure.code=="REQUEST_PENDING","overlapping cache operations cannot overwrite another job/pending")
            PortableOperationLock.whenIdle{cleaned.set(true)};ck(!cleaned.get(),"privacy cleanup waits for current cache writer")
        }finally{release.countDown();thread.join(5000)}
        ck(cleaned.get(),"queued privacy cleanup runs when cache operation finishes")
        ck(PortableOperationLock.run{PortableOperationLock.run{true}},"nested authenticated status remains reentrant")
        var started=0;var done:((PlaybackOutcome)->Unit)?=null;var terminal:PlaybackOutcome?=null
        val whole=WholeExplanationPlayback({true},{_,cb->started++;done=cb},{})
        whole.play("가".repeat(250)){terminal=it};done!!(PlaybackOutcome.DONE);done!!(PlaybackOutcome.DONE)
        ck(started==3&&terminal==null,"full text waits for last piece, no timer success")
        done!!(PlaybackOutcome.DONE);ck(terminal==PlaybackOutcome.DONE,"last piece actual DONE completes aggregate")
        whole.play("가".repeat(250)){terminal=it};done!!(PlaybackOutcome.REJECTED);ck(terminal==PlaybackOutcome.REJECTED,"focus rejection propagates without relistening")
    }
}
