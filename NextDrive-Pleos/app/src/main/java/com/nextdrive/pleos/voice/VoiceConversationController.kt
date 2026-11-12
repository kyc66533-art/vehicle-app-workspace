package com.nextdrive.pleos.voice

import com.nextdrive.pleos.data.ConversationStore
import com.nextdrive.pleos.domain.CommitReceipt
import com.nextdrive.pleos.integration.PlaybackOutcome
import com.nextdrive.pleos.model.SymptomType
import java.util.UUID

/** Main-actor, bounded half-duplex session. Ports marshal all completions onto the main actor.
 * SDK final+ended is enforced by SpeechInput; neither a partial nor a timer enters accept(). */
class VoiceConversationController(private val store:ConversationStore,private val ports:Ports) {
    enum class State { IDLE, ARMING, LISTENING, INTERPRETING, SAVING, AI_REQUESTING, AI_WAITING, SPEAKING, FOLLOW_UP_WAIT, ERROR_PAUSED, STOPPING, ENDED }
    data class Reply(val text:String,val origin:String="LOCAL_RULE",val operation:String?=null,val model:String?=null,val generatedAt:Long?=null)
    interface Ports {
        fun valid():Boolean
        fun scope():String
        fun permit():VoiceSettings.Permit
        fun owner():String
        fun account():String
        fun listen(accepted:(String)->Unit,failed:(String)->Unit)
        fun stopAudio()
        fun save(token:String,text:String,type:SymptomType):CommitReceipt
        fun analyze(issue:String,revision:Int,turn:String,done:(Result<Reply>)->Unit)
        fun dialogue(issue:String,revision:Int,turn:String,question:String,done:(Result<Reply>)->Unit)
        fun query(command:VoiceCommand,issue:String?,alert:String?):String
        fun speak(text:String,done:(PlaybackOutcome)->Unit)
        fun changed(state:State,message:String)
    }
    @Volatile var state=State.IDLE;private set
    var sessionId:String?=null;private set
    var issueId:String?=null;private set
    var issueRevision=0;private set
    var alertId:String?=null;private set
    var lastReply="";private set
    private var lastOrigin="LOCAL_RULE"
    val replyOrigin get()=lastOrigin
    private var generation=0
    private var frozenScope=""
    private var began=0L
    private var turns=0
    private var recognition=""
    private var consumed=false
    private var inflight:Triple<String,String,String>?=null
    private fun active()=sessionId!=null&&ports.valid()&&ports.scope()==frozenScope&&System.currentTimeMillis()-began in 0..300000&&turns<=5&&state !in setOf(State.ENDED,State.STOPPING,State.ERROR_PAUSED)
    fun effectsAllowed()=active()
    private fun transition(next:State,message:String){state=next;sessionId?.let{store.state(it,next.name)};ports.changed(next,message)}
    fun start(alert:String?=null,announcement:String?=null){
        if(state !in setOf(State.IDLE,State.ENDED,State.ERROR_PAUSED)||!ports.valid()||!ports.permit().history)return
        generation++;frozenScope=ports.scope();began=System.currentTimeMillis();turns=0;issueId=null;issueRevision=0;alertId=alert;lastReply="";lastOrigin="LOCAL_RULE"
        val id=UUID.randomUUID().toString()
        if(!VoiceSessionOwner.claim(id)){ports.changed(state,"다른 음성 대화가 진행 중이에요. 먼저 그 대화를 마쳐 주세요.");return}
        sessionId=id
        try{store.start(id,ports.owner(),ports.account(),"voice-v1:"+ports.permit().toString(),alert);transition(State.ARMING,"음성 대화를 준비하고 있어요.")
            if(announcement!=null)reply(Reply(announcement),"attention")else listen()
        }catch(_:Exception){pause("대화 이력을 저장하지 못해 시작하지 않았어요.")}
    }
    private fun listen(){
        if(!active()||turns>=5){end();return}
        recognition=UUID.randomUUID().toString();consumed=false
        val run=generation;val token=recognition
        transition(State.LISTENING,"마이크가 켜졌어요. 짧게 말씀해 주세요.")
        ports.listen({text->if(run==generation&&token==recognition&&!consumed&&active()){
            consumed=true;accept(text,token)
        }},{code->if(run==generation&&token==recognition)pause("음성 입력을 마쳤어요. 다시 시작할 수 있어요. · $code")})
    }
    private fun accept(text:String,turn:String){
        try{
            if(!active())return end()
            turns++;transition(State.INTERPRETING,"말씀하신 내용을 확인하고 있어요.")
            store.appendOnce(turn,sessionId!!,"USER","ASR",text,issueRevision)
            when(val command=VoiceCommandRouter.route(text,ports.permit().record,issueId!=null||alertId!=null)){
                VoiceCommand.End->end()
                VoiceCommand.Wake->reply(Reply("말씀하세요."),turn)
                is VoiceCommand.Record->save(turn,command.text,command.type)
                VoiceCommand.RecordContext->{if(alertId!=null)save(turn,ports.query(command,issueId,alertId),SymptomType.OTHER)else reply(Reply("기록할 증상과 기록해 달라는 말씀을 함께 알려 주세요."),turn)}
                is VoiceCommand.FollowUp->{
                    if(issueId==null)reply(Reply(ports.query(command,null,alertId)),turn)
                    else if(!ports.permit().cloud)reply(Reply("이 문제의 새 AI 대화는 음성 설정에서 전송 범위를 허용한 뒤 사용할 수 있어요."),turn)
                    else request(turn,true,command.question)
                }
                VoiceCommand.Repeat->reply(Reply(lastReply.ifBlank{"아직 읽을 설명이 없어요."},if(lastOrigin.startsWith("GEMINI"))"GEMINI_SAVED"else"LOCAL_RULE"),turn)
                VoiceCommand.Clarify->reply(Reply("차량 증상을 말씀하시거나, 배터리 잔량과 최근 기록을 물어봐 주세요. 기록해 줘라고 말씀하시면 저장해요."),turn)
                else->reply(Reply(ports.query(command,issueId,alertId)),turn)
            }
        }catch(_:Exception){pause("이번 처리를 마치지 못했어요. 이미 저장한 기록과 대화는 남아 있어요.")}
    }
    private fun save(turn:String,text:String,type:SymptomType){
        if(!active())return end()
        transition(State.SAVING,"문제를 기록하고 있어요.")
        val receipt=ports.save(sessionId!!+":"+turn,text,type)
        store.link(receipt);issueId=receipt.issueId;issueRevision=receipt.revision;store.anchor(sessionId!!,receipt.issueId,receipt.revision)
        if(!active())return end()
        if(ports.permit().cloud)request(turn,false,"")else reply(Reply("문제를 기록했어요. AI 자동 설명은 꺼져 있어요."),turn)
    }
    private fun request(turn:String,dialogue:Boolean,question:String){
        if(!active())return end()
        val run=generation;val issue=issueId!!;val revision=issueRevision;val op="$turn:ai";val session=sessionId!!
        transition(State.AI_REQUESTING,"저장한 문제의 AI 설명을 요청하고 있어요.")
        store.operation(op,session,turn,if(dialogue)"DIALOGUE"else"ISSUE","PREPARED")
        inflight=Triple(op,turn,if(dialogue)"DIALOGUE"else"ISSUE")
        transition(State.AI_WAITING,"AI 답변을 기다리고 있어요. 기록은 이미 저장됐어요. 분당 제한에 걸리면 같은 요청으로 잠시 기다려요.")
        val done:(Result<Reply>)->Unit={result->
            if(run==generation&&active()&&issue==issueId&&revision==issueRevision){
                inflight=null
                try{result.fold({answer->store.operation(op,session,turn,if(dialogue)"DIALOGUE"else"ISSUE","SUCCEEDED",server=answer.operation,model=answer.model,generatedAt=answer.generatedAt);reply(answer,turn)},
                    {failure->val code=(failure as? com.nextdrive.pleos.ai.PortableFailure)?.code?:"APP_PROCESSING_FAILED"
                        store.operation(op,session,turn,if(dialogue)"DIALOGUE"else"ISSUE",if(code in setOf("MODEL_OUTCOME_UNKNOWN","REQUEST_PENDING","APP_NETWORK"))"UNKNOWN"else"FAILED",code=code)
                        reply(Reply("새 AI 설명을 받지 못했어요. 저장한 문제는 그대로예요. 배터리와 기록 조회는 계속 사용할 수 있어요. 자세한 요청 상태는 대화 이력에서 확인해 주세요."),turn)})
                }catch(_:Exception){pause("처리 이력을 보관하지 못했어요. 기록은 남아 있어요.")}
            }
        }
        if(dialogue)ports.dialogue(issue,revision,turn,question,done)else ports.analyze(issue,revision,turn,done)
    }
    private fun reply(answer:Reply,parent:String){
        if(!active())return end()
        val session=sessionId!!;val id=UUID.randomUUID().toString()
        store.appendOnce(id,session,"ASSISTANT",answer.origin,answer.text,issueRevision,parent);lastReply=answer.text;lastOrigin=answer.origin
        if(!ports.permit().speak){
            transition(State.FOLLOW_UP_WAIT,answer.text)
            if(ports.permit().followUp)listen()else end()
            return
        }
        val run=generation;val delivery="$id:auto"
        store.playback(delivery,session,id,"STARTED");transition(State.SPEAKING,answer.text)
        ports.speak(answer.text){outcome->
            runCatching{store.playback(delivery,session,id,outcome.name)}.onFailure{pause("음성 재생 결과를 저장하지 못했어요.");return@speak}
            if(run!=generation)return@speak
            if(outcome!=PlaybackOutcome.DONE){pause("음성 안내가 중단됐어요. 화면에서 설명을 확인해 주세요.");return@speak}
            if(active()&&ports.permit().followUp){transition(State.FOLLOW_UP_WAIT,"다음 질문을 기다려요.");listen()}
            else end()
        }
    }
    private fun interrupted(){val pending=inflight;inflight=null;if(pending!=null&&sessionId!=null)runCatching{store.operation(pending.first,sessionId!!,pending.second,pending.third,"UNKNOWN",code="LOCAL_SESSION_ENDED")}}
    fun pause(message:String){generation++;interrupted();VoiceSessionOwner.release(sessionId);ports.stopAudio();runCatching{transition(State.ERROR_PAUSED,message)}.onFailure{state=State.ERROR_PAUSED;ports.changed(state,message)}}
    fun end(){generation++;interrupted();VoiceSessionOwner.release(sessionId);state=State.STOPPING;ports.stopAudio();runCatching{transition(State.ENDED,"음성 대화를 마쳤어요. 저장한 기록과 대화 이력은 남아 있어요.")}.onFailure{state=State.ENDED;ports.changed(state,"대화를 마쳤어요.")}}
}
