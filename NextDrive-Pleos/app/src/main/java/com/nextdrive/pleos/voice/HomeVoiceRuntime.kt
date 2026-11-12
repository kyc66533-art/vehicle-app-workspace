package com.nextdrive.pleos.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.data.*
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.model.SymptomType
import com.nextdrive.pleos.vehicle.*

/** Foreground-only home host. The optional offline detector releases its microphone before
 * the existing Pleos conversation starts. Without it, public STT has bounded windows only;
 * silence/errors never restart it. Standby and five-minute/five-turn conversation differ. */
class HomeVoiceRuntime(
    private val context:Context,private val prefix:String?,private val service:CaritdaService,
    private val repo:SqliteIssueRepository,private val raw:ObservationStore,private val low:LowSocStore,
    private val attention:AttentionEventStore,private val eligible:()->Boolean,private val changed:()->Unit,
    private val session:PortableSession=FirebasePortableSession(context),
    private val speechFactory:(()->SpeechBackend)?=null,
    private val focus:VoiceListeningFocus=AndroidVoiceListeningFocus(context)
) {
    private val main=Handler(Looper.getMainLooper())
    private val settings=VoiceSettings(context,prefix?.let{"$it-settings"}?:"voice-automation-v1")
    private val historyDelegate=lazy{ConversationStore(context,prefix?.let{"$it-conversations.db"}?:"caritda-conversations.db").also{it.expire();it.reconcile(repo,VoiceSessionOwner.current())}}
    private val history by historyDelegate
    private var foreground=false
    private var owner="";private var account=""
    private var wasEligible=false
    private var blocked=false
    private var windows=0
    private var generation=0
    private var waiting=false
    private var pendingReturn=false
    private var attentionKind:String?=null
    private var attentionDelivery:String?=null
    private var committing=false
    private val detector=if(speechFactory==null)HomeWakeDetectorFactory.create(context)else null
    private var detectorUnavailable=false
    var controller:VoiceConversationController?=null;private set
    var detail="홈 음성 대기는 꺼져 있어요.";private set
    private val input=SpeechInput(context,factory=speechFactory,allowed={valid()})
    private val wake=SpeechInput(context,factory=speechFactory,recognitionTimeoutMs=30000,allowed={valid()&&waiting})
    private val output=SpeechOutput(context){valid()}
    private val playback=WholeExplanationPlayback({valid()},{text,done->output.speakOutcome(text,done){}},{output.stop()})
    private val bridge=VoiceAiBridge(context,{valid()&&permit().cloud&&controller?.effectsAllowed()==true},{repo.issue(it)},{session})
    private val issueContext by lazy{VoiceIssueContext(service,history,raw,low,attention,{valid()},{owner},{account},{controller!!},{bridge},{attentionKind})}
    val autoEnabled get()=service.confirmed()&&settings.read(service.driver().id,session.subject.orEmpty()).autoWait
    val active get()=waiting||controller?.state?.let{it !in setOf(VoiceConversationController.State.IDLE,VoiceConversationController.State.ENDED,VoiceConversationController.State.ERROR_PAUSED)}==true
    val wakeSource get()=if(detector!=null&&!detectorUnavailable)detector.source else "PLEOS_BOUNDED_STT"
    val wakeDiagnostics get()=detector?.diagnostics?:"source=PLEOS_BOUNDED_STT requests="+wake.requests
    val microphoneActive get()=detector?.microphoneActive==true||(if(waiting)wake.state else input.state).let{it.busy&&it.microphone==MicrophoneState.ACTIVE}
    val title get()=when {
        !hasMicrophone()&&autoEnabled->"마이크를 사용할 수 없어요"
        waiting&&microphoneActive->"음성 대기 중"
        waiting->"마이크를 준비하고 있어요"
        detector?.busy==true->"마이크를 멈추고 있어요"
        controller?.state==VoiceConversationController.State.LISTENING&&microphoneActive->"말씀을 듣고 있어요"
        controller?.state==VoiceConversationController.State.SPEAKING->if(output.started&&output.busy)"음성으로 설명하고 있어요"else"음성 안내를 준비하고 있어요"
        controller?.state in setOf(VoiceConversationController.State.AI_REQUESTING,VoiceConversationController.State.AI_WAITING)->"AI 설명을 기다리고 있어요"
        controller?.state==VoiceConversationController.State.SAVING->"문제를 기록하고 있어요"
        else->"음성 대기 꺼짐"
    }
    private fun permit()=settings.read(owner,account)
    private fun hasMicrophone()=context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
    private fun authReady()=session !is FirebasePortableSession||FirebaseSessionProvider.configState!="READY"||FirebaseSessionProvider.initialized
    private fun valid()=foreground&&eligible()&&service.confirmed()&&service.driver().id==owner&&session.subject.orEmpty()==account&&authReady()&&permit().autoWait&&permit().history&&hasMicrophone()&&service.draft().let{it==null||committing&&controller?.state==VoiceConversationController.State.SAVING&&it.ownerId==owner}
    fun resume(){foreground=true;windows=0;blocked=false;wasEligible=false;evaluate()}
    fun pause(){foreground=false;wasEligible=false;stopEffects();detail="화면을 벗어나 청취를 멈췄어요.";changed()}
    fun evaluate(){
        val nextOwner=if(service.confirmed())service.driver().id else "";val nextAccount=session.subject.orEmpty()
        if(nextOwner!=owner||nextAccount!=account){stopEffects();owner=nextOwner;account=nextAccount;wasEligible=false;blocked=false;windows=0}
        if(!valid()){
            if(active)stopEffects()
            wasEligible=false
            detail=when{!autoEnabled->"음성 설정에서 홈 자동 대기를 선택할 수 있어요.";!hasMicrophone()->"음성 설정에서 마이크 권한을 확인해 주세요.";!permit().history->"음성 설정에서 대화 본문 보관 범위도 선택해 주세요.";else->"정차·운전자·홈 화면 상태를 확인한 뒤 대기해요."}
            changed();return
        }
        if(!wasEligible){wasEligible=true;if(!blocked&&!active)arm()}
    }
    private fun ensureController():VoiceConversationController {
        controller?.let{return it}
        VoicePrivacy.reconcile(context)
        if(account.isNotBlank())VoicePrivacy.expireDialogue(context,account)
        return VoiceConversationController(history,object:VoiceConversationController.Ports {
            override fun valid()=this@HomeVoiceRuntime.valid()
            override fun scope()=owner+":"+account+":"+permit().toString()
            override fun permit()=this@HomeVoiceRuntime.permit()
            override fun owner()=owner
            override fun account()=account
            override fun stopStandby(){settings.write(owner,account,permit().copy(autoWait=false));blocked=true}
            override fun stopAudio(){input.stop();wake.stop();detector?.stop();waiting=false;focus.release();playback.cancel();bridge.cancel()}
            override fun listen(accepted:(String)->Unit,failed:(String)->Unit){
                if(!focus.acquire{block("다른 음성 서비스가 오디오를 사용해 청취를 멈췄어요.")}){failed("AUDIO_FOCUS_DENIED");return}
                input.observer={state->if(state.phase==SpeechPhase.FAILED&&!state.busy)failed(state.code);changed()}
                input.listen({detail=it;changed()}){text->focus.release();accepted(text)}
            }
            override fun save(token:String,text:String,type:SymptomType):com.nextdrive.pleos.domain.CommitReceipt {
                committing=true;return try{issueContext.save(token,text,type)}finally{committing=false}
            }
            override fun analyze(issue:String,revision:Int,turn:String,done:(Result<VoiceConversationController.Reply>)->Unit)=issueContext.analyze(issue,revision,turn,done)
            override fun dialogue(issue:String,revision:Int,turn:String,question:String,done:(Result<VoiceConversationController.Reply>)->Unit)=issueContext.dialogue(issue,revision,turn,question,done)
            override fun query(command:VoiceCommand,issue:String?,alert:String?)=issueContext.query(command)
            override fun speak(text:String,done:(PlaybackOutcome)->Unit){
                focus.release();val event=attentionDelivery;attentionDelivery=null
                playback.play(text){outcome->event?.let{attention.finishSpeech(it,outcome.name)};done(outcome)}
            }
            override fun changed(state:VoiceConversationController.State,message:String){
                detail=when(state){
                    VoiceConversationController.State.SPEAKING->"준비된 안내를 읽어요. 전체 내용은 대화 이력에 보관해요."
                    VoiceConversationController.State.AI_WAITING,VoiceConversationController.State.AI_REQUESTING->"저장한 문제의 AI 설명을 기다려요. 원래 기록은 남아 있어요."
                    VoiceConversationController.State.FOLLOW_UP_WAIT->"다음 질문을 준비하고 있어요."
                    else->message
                }
                if(state==VoiceConversationController.State.ERROR_PAUSED)blocked=true
                if(state==VoiceConversationController.State.ENDED&&!pendingReturn){pendingReturn=true;main.post{pendingReturn=false;if(valid()&&!blocked&&!active)arm()}}
                changed()
            }
        }).also{controller=it}
    }
    private fun arm(){
        if(!valid()||active||blocked)return
        if(VoiceSessionOwner.current()!=null){block("다른 음성 대화를 먼저 마쳐 주세요.");return}
        if(detector!=null&&!detectorUnavailable){
            generation++;val run=generation;waiting=true;detail="이 화면에서 ‘카잇다’라는 호출을 기다려요. 음성은 기기 안에서 처리해요."
            detector.stop{
                if(run==generation&&valid()&&waiting)detector.start(
                    {run==generation&&waiting&&valid()},
                    {if(run==generation)changed()},
                    {text->if(run==generation&&valid())activate(text)},
                    {code->if(run==generation){
                        if(code in setOf("WAKE_ENGINE_UNAVAILABLE","WAKE_ENGINE_FAILED","MODEL_INTEGRITY_FAILED","MODEL_CACHE_CONFLICT")){
                            detectorUnavailable=true;waiting=false;detail="기기 내 호출 기능을 준비하지 못해 짧은 음성 대기로 전환해요.";arm()
                        }else block("마이크를 사용할 수 없어 대기를 멈췄어요. 다른 음성 서비스와 권한을 확인해 주세요.")
                    }}
                )
            }
            changed();return
        }
        if(windows>=3){blocked=true;detail="이번 화면의 짧은 대기를 마쳤어요. 필요하면 다시 대기를 눌러 주세요.";changed();return}
        windows++;generation++;val run=generation;waiting=true
        if(!focus.acquire{block("다른 음성 서비스가 오디오를 사용해 대기를 멈췄어요.")}){block("오디오를 사용할 수 없어 대기를 시작하지 않았어요.");return}
        detail="짧게 호출을 기다려요. ‘카잇다’라고 말씀해 주세요."
        wake.observer={state->if(run==generation){if(state.phase==SpeechPhase.FAILED&&!state.busy)block("호출을 확인하지 못해 마이크를 껐어요. 필요하면 다시 대기를 눌러 주세요.");else changed()}}
        wake.listen({changed()}){text->
            if(run!=generation||!valid())return@listen
            activate(text)
        }
        changed()
    }
    private fun activate(text:String){
        generation++;waiting=false;wake.stop();focus.release()
        if(VoiceCommandRouter.route(text,false,false)==VoiceCommand.StopListening){disable();return}
        val remaining=VoiceCommandRouter.afterWake(text)
        if(remaining==null){blocked=true;detail="호출어를 확인하지 못해 청취를 멈췄어요.";changed();return}
        try{
            val c=ensureController();val onlyWake=remaining.isBlank()
            if(onlyWake)c.start(announcement="말씀하세요.",prompt=true,activation=text)else c.start(firstText=text)
        }catch(_:Exception){block("대화 준비를 마치지 못했어요. 기존 기록은 그대로예요.")}
    }
    private fun stopEffects(){generation++;waiting=false;main.removeCallbacksAndMessages(null);pendingReturn=false;controller?.end();detector?.stop{changed()};wake.stop();input.stop();focus.release();playback.cancel();bridge.cancel()}
    private fun block(message:String){blocked=true;stopEffects();detail=message;changed()}
    fun disable(){if(owner.isNotBlank())settings.write(owner,account,permit().copy(autoWait=false));blocked=true;stopEffects();detail="음성 대기를 껐어요. 마이크를 사용하지 않아요.";changed()}
    fun retry(){blocked=false;windows=0;wasEligible=false;evaluate()}
    fun announce(event:AttentionEventStore.Event,kind:String,text:String):Boolean {
        if(!valid()||controller?.effectsAllowed()==true)return false
        generation++;val run=generation;waiting=false;wake.stop();focus.release()
        attentionKind=kind;attentionDelivery=event.id
        val start={
            if(run==generation&&valid())try{ensureController().start(event.id,text)}catch(_:Exception){attention.finishSpeech(event.id,"FAILED");block("차량 안내를 시작하지 못했어요.")}
            else attention.finishSpeech(event.id,"CANCELLED")
        }
        if(detector!=null)detector.stop(start)else start()
        return true
    }
    fun close(){pause();detector?.close();bridge.close();if(historyDelegate.isInitialized())history.close()}
}
