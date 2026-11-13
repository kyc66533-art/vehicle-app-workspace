package com.nextdrive.pleos

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.*
import com.nextdrive.pleos.model.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.ui.*
import com.nextdrive.pleos.vehicle.*
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.voice.*
import java.util.UUID

/** Same B View system. Explicit start, bounded visible/parked sessions; no background hotword. */
class VoiceConversationActivity:Activity(){
    private lateinit var repo:SqliteIssueRepository
    private lateinit var service:CaritdaService
    private lateinit var conversations:ConversationStore
    private lateinit var raw:ObservationStore
    private lateinit var low:LowSocStore
    private lateinit var attention:AttentionEventStore
    private lateinit var soc:PleosSocObserver
    private lateinit var attentionObserver:PleosAttentionObserver
    private var autoAttentionConsumed=false
    private var firstAttentionPlayback=true
    private lateinit var gate:CarSafetyGate
    private lateinit var settings:VoiceSettings
    private lateinit var ui:CaritdaComponents
    private lateinit var body:LinearLayout
    private lateinit var shell:LinearLayout
    private lateinit var fixed:LinearLayout
    private lateinit var controller:VoiceConversationController
    @Volatile private var visible=false
    private var owner=""
    @Volatile private var account=""
    private var settingsOpen=false
    private var historyOpen=false
    private var historyDetails=false
    @Volatile private var recovering=false
    @Volatile private var preparingCloud=false
    private val lifecycleHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val lifecycleCheck=object:Runnable{override fun run(){
        if(!visible||!::controller.isInitialized)return
        if(!service.confirmed()||service.driver().id!=owner){controller.end();finish();return}
        if(controller.state !in setOf(VoiceConversationController.State.IDLE,VoiceConversationController.State.ENDED,VoiceConversationController.State.ERROR_PAUSED,VoiceConversationController.State.STOPPING)&&!controller.effectsAllowed())controller.end()
        lifecycleHandler.postDelayed(this,500)
    }}
    private var message="한 번 시작하면 설명을 들은 뒤 다음 질문을 말씀할 수 있어요."
    private var removeAuth:(()->Unit)?=null
    private val aiSession by lazy{FirebasePortableSession(this) as PortableSession}
    private val fixture by lazy{intent.getStringExtra("validation_fixture")?.takeIf{packageName=="com.nextdrive.pleos.validation"&&Regex("(?:voice-auto|gleo-ui)-[a-f0-9-]{36}").matches(it)}}
    private val speech by lazy{SpeechInput(this){valid()}}
    private val output by lazy{SpeechOutput(this){valid()}}
    private val playback by lazy{WholeExplanationPlayback({valid()},{text,done->output.speakOutcome(text,done){ }},{output.stop()})}
    private val bridge by lazy{VoiceAiBridge(this,{valid()&&permit().cloud&&(recovering||preparingCloud||::controller.isInitialized&&controller.effectsAllowed())},{repo.issue(it)},{aiSession})}
    private val issueContext by lazy{VoiceIssueContext(service,conversations,raw,low,attention,{valid()},{owner},{account},{controller},{bridge},{intent.getStringExtra("attention_kind")})}
    private fun permit()=settings.read(owner,account)
    private fun authReady()=aiSession !is FirebasePortableSession||FirebaseSessionProvider.configState!="READY"||FirebaseSessionProvider.initialized
    private fun valid()=visible&&::gate.isInitialized&&gate.state==EditingState.ALLOWED&&service.confirmed()&&service.driver().id==owner&&authReady()&&aiSession.subject.orEmpty()==account
    private fun scope()=owner+":"+account+":"+permit().toString()
    override fun onCreate(saved:Bundle?){super.onCreate(saved)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if(runCatching{VoicePrivacy.reconcile(this)}.isFailure){Toast.makeText(this,"이전 계정의 대화 정리를 마친 뒤 다시 이용해 주세요.",Toast.LENGTH_LONG).show();finish();return}
        repo=SqliteIssueRepository(this,fixture?.let{"$it.db"}?:"caritda_v01.db");raw=ObservationStore(this,fixture?.let{"$it-observations.db"}?:"caritda-observations.db");low=LowSocStore(this,fixture?.let{"$it-soc.db"}?:"caritda-soc-notices.db")
        conversations=ConversationStore(this,fixture?.let{"$it-conversations.db"}?:"caritda-conversations.db");settings=VoiceSettings(this,fixture?.let{"$it-settings"}?:"voice-automation-v1")
        attention=AttentionEventStore(this,fixture?.let{"$it-attention.db"}?:"caritda-attention.db")
        autoAttentionConsumed=saved?.getBoolean("auto_attention_consumed")?:false
        owner=intent.getStringExtra("driver_id").orEmpty()
        gate=CarSafetyGate(this){if(::controller.isInitialized){if(it!=EditingState.ALLOWED){controller.end();soc.stop();attentionObserver.stop()}else{if(low.policy().enabled)soc.start();attentionObserver.start();maybeAutoAttention()}};render()}
        service=CaritdaService(repo,{gate.state==EditingState.ALLOWED})
        if(repo.currentDriver()?.id!=owner||owner.isBlank()){finish();return}
        service.restoreConfiguration(owner)
        FirebaseSessionProvider.prepare(this);account=aiSession.subject.orEmpty()
        if(account.isNotBlank())VoicePrivacy.expireDialogue(this,account)
        conversations.expire();conversations.reconcile(repo,VoiceSessionOwner.current())
        ui=CaritdaComponents(this,CaritdaPalette.of(CaritdaTheme.dark(this,fixture)))
        body=ui.column();fixed=ui.column();shell=ui.column().apply{setBackgroundColor(ui.colors.background)}
        shell.addView(fixed);shell.addView(ScrollView(this).apply{isFillViewport=true;addView(body)},LinearLayout.LayoutParams(-1,0,1f))
        setContentView(CaritdaPage(this,shell).apply{setBackgroundColor(ui.colors.background)})
        shell.addOnLayoutChangeListener{_,l,_,r,_,oldL,_,oldR,_->if(oldR>oldL&&r-l!=oldR-oldL)shell.post{render()}}
        controller=VoiceConversationController(conversations,object:VoiceConversationController.Ports{
            override fun valid()=this@VoiceConversationActivity.valid()
            override fun scope()=this@VoiceConversationActivity.scope()
            override fun permit()=this@VoiceConversationActivity.permit()
            override fun owner()=owner
            override fun account()=account
            override fun listen(accepted:(String)->Unit,failed:(String)->Unit){
                speech.observer={s->if(s.phase==SpeechPhase.FAILED&&!s.busy)failed(s.code)}
                speech.listen({message=it;body.findViewWithTag<TextView>("voice_status")?.text=it},accepted)
            }
            override fun stopAudio(){speech.stop();playback.cancel();bridge.cancel()}
            override fun stopStandby(){settings.write(owner,account,permit().copy(autoWait=false))}
            override fun save(token:String,text:String,type:SymptomType)=issueContext.save(token,text,type)
            override fun analyze(issue:String,revision:Int,turn:String,done:(Result<VoiceConversationController.Reply>)->Unit)=issueContext.analyze(issue,revision,turn,done)
            override fun dialogue(issue:String,revision:Int,turn:String,question:String,done:(Result<VoiceConversationController.Reply>)->Unit)=issueContext.dialogue(issue,revision,turn,question,done)
            override fun query(command:VoiceCommand,issue:String?,alert:String?)=issueContext.query(command)
            override fun speak(text:String,done:(PlaybackOutcome)->Unit){val alert=if(firstAttentionPlayback&&intent.getBooleanExtra("auto_attention",false))intent.getStringExtra("attention_id")else null;firstAttentionPlayback=false
                playback.play(text){outcome->alert?.let{attention.finishSpeech(it,outcome.name)};done(outcome)}
            }
            override fun changed(state:VoiceConversationController.State,message:String){
                android.util.Log.i("CaritdaVoice","state=${state.name} visible=$visible safety=${gate.state.name} valid=${valid()}")
                this@VoiceConversationActivity.message=if(state==VoiceConversationController.State.SPEAKING)"준비된 설명을 읽고 있어요. 멈추려면 대화 끝내기를 눌러 주세요."else message;render()
            }
        })
        soc=PleosSocObserver(this,raw,{valid()&&low.policy().enabled},{ref->low.reconcile(raw);attention.accept(ref,low)},{})
        attentionObserver=PleosAttentionObserver(this,raw,{valid()},{ref->attention.accept(ref,low)},{message=attentionObserver.message;render()})
        render()
    }
    private fun maybeAutoAttention(){
        if(autoAttentionConsumed||!valid()||!intent.getBooleanExtra("auto_attention",false))return
        autoAttentionConsumed=true
        val id=intent.getStringExtra("attention_id")?:return
        val event=attention.events().firstOrNull{it.id==id&&it.phase=="ACTIVE"}?:return
        val kind=intent.getStringExtra("attention_kind")?:return
        val p=permit();val fresh=DriveCheck.assess(kind,raw.mostRecent(kind),raw.mostRecent(kind,true),low.policy())
        if(p.alerts&&p.speak&&p.history&&fresh.attention&&System.currentTimeMillis()-event.last in 0 until 600000){
            controller.start(id,if(kind=="EV_SOC")"배터리가 설정한 기준보다 낮아졌어요. 고장 판정은 아니에요."else"차량에서 ${fresh.title} 안내를 받았어요. 차량 표시와 함께 확인해 주세요.")
        }else attention.finishSpeech(id,"SKIPPED")
    }
    private fun startConversation(){
        if(!valid()||recovering||preparingCloud)return
        if(!permit().history){settingsOpen=true;message="대화 보관 범위와 필요한 자동화 기능을 먼저 선택해 주세요.";render();return}
        if(service.draft()!=null){message="작성 중인 초안을 먼저 저장하거나 취소해 주세요. 초안은 그대로 보존돼요.";render();return}
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),912);return}
        controller.start(intent.getStringExtra("attention_id"))
    }
    override fun onRequestPermissionsResult(code:Int,names:Array<out String>,results:IntArray){super.onRequestPermissionsResult(code,names,results);if(code==912){if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)startConversation()else{message="마이크 권한을 허용하지 않았어요. 기존 기록과 설명은 그대로예요.";render()}};if(code==913){message=if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)"마이크를 허용했어요. 홈으로 돌아가면 선택한 범위에서 짧게 호출을 기다려요."else"마이크 권한을 허용하지 않아 홈에서 듣지 않아요. 기존 기록은 그대로예요.";render()}}
    private fun recover(pending:ConversationStore.Pending){
        if(!valid()||recovering||!permit().cloud)return
        val selected=repo.issue(pending.issue)
        if(selected==null||selected.revision!=pending.revision){message="원래 문제 내용이 바뀌어 이전 요청을 이 화면에서 이어받지 않았어요.";render();return}
        controller.end();recovering=true;message="원래 요청의 상태를 확인하고 있어요. 같은 예약의 최초 전송이 이어질 수 있어요.";render()
        val question=if(pending.kind=="DIALOGUE")conversations.messages(pending.session,owner,account).firstOrNull{it.id==pending.turn}?.text else null
        if(pending.kind=="DIALOGUE"&&question==null){recovering=false;message="원래 질문을 복원하지 못해 요청을 이어받지 않았어요.";render();return}
        bridge.request(selected,pending.turn,question,emptyList(),{result->
            recovering=false
            result.fold({answer->
                conversations.operation(pending.id,pending.session,pending.turn,pending.kind,"SUCCEEDED",server=answer.operation,model=answer.model,generatedAt=answer.generatedAt)
                conversations.appendOnce("recovered:${pending.turn}",pending.session,"ASSISTANT",answer.origin,answer.text,pending.revision,pending.turn)
                message="원래 요청의 설명을 확인했어요. 대화 이력에서 볼 수 있어요."
            },{error->val code=(error as? PortableFailure)?.code?:"APP_PROCESSING_FAILED";conversations.operation(pending.id,pending.session,pending.turn,pending.kind,if(code=="NO_PENDING_OPERATION")"FAILED"else"UNKNOWN",code=code);message="원래 요청의 결과를 아직 확인하지 못했어요. 새 요청은 만들지 않았어요."})
            render()
        },recoveryOnly=true)
    }
    private fun render(){
        if(!::ui.isInitialized||!::controller.isInitialized)return
        body.removeAllViews();body.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(24));fixed.removeAllViews();fixed.setPadding(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(4))
        val width=if(shell.width>0)shell.width/resources.displayMetrics.density else resources.configuration.screenWidthDp.toFloat()
        val compact=width<600||resources.configuration.fontScale>=1.5f
        val back=ui.action("이전","voice_back",ActionStyle.QUIET,true,if(compact)null else HmiIcon.BACK){finish()}
        val home=ui.action("홈","voice_home",ActionStyle.QUIET,true,if(compact)null else HmiIcon.HOME){
            controller.end()
            val target=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("caritda_home",true)
            if(packageName=="com.nextdrive.pleos.validation")target.putExtra("validation_fixture",fixture?:"gleo-ui-"+UUID.randomUUID())
            startActivity(target);finish()
        }
        fixed.addView(ui.grid(if(compact)listOf(back,home)else listOf(back,ui.text("음성 대화",28,true),home),if(compact)2 else 3))
        if(packageName.endsWith(".validation"))body.addView(ui.text("검증 전용 · TEST",20,true))
        body.addView(ui.text("${service.driver().label}님의 음성 대화",26,true))
        body.addView(ui.text(when(controller.state){VoiceConversationController.State.LISTENING->"● 음성 인식 중";VoiceConversationController.State.SPEAKING->"음성 안내 중";VoiceConversationController.State.AI_WAITING->"AI 답변 대기 중";VoiceConversationController.State.SAVING->"기록 저장 중";VoiceConversationController.State.ERROR_PAUSED->"확인이 필요해요";else->"음성 대화"},24,true).apply{tag="voice_status_card"})
        body.addView(ui.text(message,22).apply{tag="voice_status";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE})
        val running=controller.state !in setOf(VoiceConversationController.State.IDLE,VoiceConversationController.State.ENDED,VoiceConversationController.State.ERROR_PAUSED)
        fixed.addView(ui.action(if(running)"대화 끝내기"else"음성 대화 시작","voice_start_stop",ActionStyle.PRIMARY,if(running)true else valid()&&!recovering&&!preparingCloud){if(running)controller.end()else startConversation()})
        if(controller.issueId!=null)body.addView(ui.state("문제를 기록했어요.","저장한 기록에서 수정·점검·상태를 관리할 수 있어요. AI 설명이 문제 해결을 뜻하지는 않아요.").apply{tag="voice_saved_result"})
        if(controller.lastReply.isNotBlank())body.addView(ui.state(if(controller.replyOrigin.startsWith("GEMINI"))"AI 점검 참고"else"CARITDA 안내",controller.lastReply).apply{tag="voice_explanation"})
        body.addView(ui.text("앱이 보이는 정차 상태에서 한 번에 최대 5분·5개 질문을 받아요. 듣는 중 ‘카잇다’라고 부를 수 있어요. 앱 밖 호출은 지원하지 않아요. 말하는 동안에는 화면의 끝내기를 이용해 주세요.",20))
        body.addView(ui.action(if(settingsOpen)"음성 설정 접기"else"음성 설정","voice_settings",ActionStyle.QUIET,valid()){settingsOpen=!settingsOpen;render()})
        if(settingsOpen){
            val p=permit();val values=listOf(p.record,p.cloud,p.speak,p.followUp,p.history,p.alerts,p.autoWait)
            val labels=listOf("명확한 차량 증상을 자동 기록해요","저장한 증상과 후속 질문을 Gemini로 보내요","준비된 설명을 자동으로 읽어요","설명 후 다음 질문을 자동으로 들어요","최종 문장과 대화 본문을 30일 보관해요","새 차량 안내를 한 번 음성으로 알려요","홈에서 자동으로 호출을 기다려요")
            val boxes=labels.mapIndexed{i,label->CheckBox(this).apply{text=label;textSize=28f;setTextColor(ui.colors.text);minHeight=ui.dp(76);isChecked=values[i];isEnabled=valid();tag="voice_permit_$i"}.also{body.addView(it)}}
            body.addView(ui.text("홈에서 호출을 기다리고, ‘카잇다’라고 말씀하시면 대화를 시작해요. 기기 내 호출 기능을 사용할 수 없으면 최대 30초씩, 화면을 연 동안 3회까지만 대기해요. 앱 밖에서는 듣지 않아요.",20))
            body.addView(ui.text("AI 전송: 선택한 증상 종류·본문, 지금 질문, 같은 문제의 최근 질문·AI 답변 최대 두 쌍이에요. 차량 수치·위치·이름·메모·다른 기록은 보내지 않아요. 일 5회·월 100회·서비스 월 300회와 기존 비용 한도를 함께 써요. 매번 버튼을 누르지 않아도 유료 요청이 이어질 수 있어요. 개인정보가 있거나 길면 전송하지 않아요.\n음성 원본은 저장하지 않아요. 대화는 앱에서 암호화하며 운전자와 AI 계정별로 보여요. 운전자 선택은 비밀번호 인증이 아니므로 민감한 내용은 말씀하지 마세요.",20))
            body.addView(ui.action("선택한 범위 저장","voice_permits_save",ActionStyle.SECONDARY,valid()&&!preparingCloud){
                if(!valid())return@action
                try{
                controller.end();val next=VoiceSettings.Permit(boxes[0].isChecked,boxes[1].isChecked,boxes[2].isChecked,boxes[3].isChecked,boxes[4].isChecked,boxes[5].isChecked,boxes[6].isChecked)
                if(p.cloud&&!next.cloud){VoicePrivacy.forgetDialogue(this,account,conversations.dialogueTurns(owner,account));conversations.withdrawCloud(account,owner)}
                if(p.history&&!next.history){VoicePrivacy.forgetDialogue(this,account,conversations.dialogueTurns(owner,account));conversations.forgetHistory(owner,account)}
                settings.write(owner,account,next);message=if(next.autoWait)"음성 설정을 저장했어요. 홈으로 돌아가면 호출을 기다려요."else"음성 설정을 저장했어요. 대화 시작을 누를 때만 마이크를 켜요."
                if(next.cloud){preparingCloud=true;message="증상 설명과 후속 대화의 전송 동의를 확인하고 있어요. 분석은 요청하지 않아요.";render()
                    bridge.prepareConsents{result->preparingCloud=false;message=result.fold({remaining->"AI 전송 준비를 마쳤어요. 현재 새 요청 잔여 $remaining 회예요. "+if(next.autoWait)"홈에서 ‘카잇다’라고 말씀해 주세요."else"대화 시작을 눌러 주세요."},{error->(error as? PortableFailure)?.displayMessage()?:"AI 준비를 마치지 못했어요. AI 계정·기기·전송 동의를 확인하거나 AI 전송을 끄고 로컬 대화를 이용해 주세요."});render()}
                }else render()
                if(next.autoWait&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),913)
                }catch(_:Exception){preparingCloud=false;message="이전 요청을 정리하는 중이거나 설정을 저장하지 못했어요. 잠시 뒤 다시 저장해 주세요. 기존 기록은 남아 있어요.";render()}
            })
            body.addView(ui.action("AI 계정·기기·전송 동의","voice_ai_account",ActionStyle.QUIET,valid()){
                val i=Intent(this,PortableAiActivity::class.java);service.issues().firstOrNull()?.let{i.putExtra("issue_id",it.id)};startActivity(i)
            })
            val roleText=if(android.os.Build.VERSION.SDK_INT>=29){val role=getSystemService(RoleManager::class.java);"제공 ${role.isRoleAvailable(RoleManager.ROLE_ASSISTANT)} / 이 앱 선택 ${role.isRoleHeld(RoleManager.ROLE_ASSISTANT)}"}else"이 Android 버전에서 조회할 수 없어요"
            body.addView(ui.text("시스템 비서 역할 · $roleText. 차량 PTT·앱 밖 호출어는 플랫폼 연결이 필요해요.",20).apply{tag="voice_role_status"})
            if(android.os.Build.VERSION.SDK_INT>=29&&getSystemService(RoleManager::class.java).isRoleAvailable(RoleManager.ROLE_ASSISTANT))body.addView(ui.action("시스템 비서 선택 화면","voice_assistant_role",ActionStyle.QUIET,valid()){
                if(valid())runCatching{startActivity(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))}.onFailure{message="이 기기에서 비서 선택 화면을 열지 못했어요. 앱 안의 음성 대화는 그대로 사용할 수 있어요.";render()}
            })
        }
        body.addView(ui.action(if(historyOpen)"대화 이력 접기"else"대화 이력 보기","voice_history",ActionStyle.QUIET,valid()){historyOpen=!historyOpen;render()})
        if(historyOpen){
            body.addView(ui.action(if(historyDetails)"요청·음성 상태 접기"else"요청·음성 상태 자세히","voice_history_details",ActionStyle.QUIET,valid()){historyDetails=!historyDetails;render()})
            conversations.sessions(owner,account).forEach{s->
                conversations.messages(s,owner,account).forEach{m->body.addView(ui.state(if(m.role=="USER")"말씀하신 내용"else when(m.origin){"GEMINI_NEW"->"이번 Gemini 답변";"GEMINI_SAVED"->"이전 Gemini 설명";"GEMINI_RECOVERED"->"원래 요청에서 받은 Gemini 답변";else->"CARITDA 안내"},m.text).apply{tag="voice_history_message"})}
                if(historyDetails)conversations.activity(s,owner,account).forEach{body.addView(ui.text(it,20))}
                conversations.pending(s,owner,account).forEach{pending->body.addView(ui.action("원래 요청 결과 확인","voice_recover_${pending.turn}",ActionStyle.QUIET,valid()&&permit().cloud&&!recovering){recover(pending)})}
            }
        }
    }
    override fun onStart(){super.onStart();if(!::controller.isInitialized)return;visible=true
        val now=aiSession.subject.orEmpty();if(now!=account){conversations.withdrawCloud(account);account=now;controller.end()}
        removeAuth=FirebaseSessionProvider.observe{val next=aiSession.subject.orEmpty();if(next!=account){controller.end();conversations.withdrawCloud(account);account=next};render();maybeAutoAttention()}
        gate.start();render()
        lifecycleHandler.postDelayed(lifecycleCheck,500)
    }
    override fun onStop(){visible=false;recovering=false;preparingCloud=false;lifecycleHandler.removeCallbacksAndMessages(null);if(::controller.isInitialized){controller.end();soc.stop();attentionObserver.stop()};removeAuth?.invoke();removeAuth=null;if(::gate.isInitialized)gate.stop();super.onStop()}
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("auto_attention_consumed",true);super.onSaveInstanceState(out)}
    override fun onDestroy(){
        if(::soc.isInitialized)soc.stop();if(::attentionObserver.isInitialized)attentionObserver.stop()
        if(::controller.isInitialized)bridge.close()
        if(::conversations.isInitialized)conversations.close();if(::repo.isInitialized)repo.close()
        if(::raw.isInitialized)raw.close();if(::low.isInitialized)low.close();if(::attention.isInitialized)attention.close()
        super.onDestroy()
    }
}
