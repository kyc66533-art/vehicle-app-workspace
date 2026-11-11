package com.nextdrive.pleos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.ScrollView
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.VehicleIssue
import com.nextdrive.pleos.vehicle.ObservationStore
import com.nextdrive.pleos.ui.*

/** Unverified callers get a local confirmation screen, never a data broadcast or model call. */
class CaritdaActionActivity:Activity(){
    private lateinit var safety:CarSafetyGate
    private lateinit var channel:GleoReplyChannel
    private lateinit var analysis:GleoIssueAnalysis
    private var request:CaritdaActions.Request?=null
    private var reply:CaritdaActions.Reply?=null
    private var visible=false
    private var handled=false
    private var recreated=false
    private var busy=false
    private var localReading=false
    private val speech by lazy{SpeechOutput(this){allowed()}}
    private fun allowed()=visible&&::safety.isInitialized&&safety.state==EditingState.ALLOWED
    override fun onCreate(saved:Bundle?){
        super.onCreate(saved);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        recreated=saved!=null
        request=runCatching{CaritdaActions.parse(intent)}.getOrNull()
        channel=GleoReplyChannel(this,SystemClock.elapsedRealtime());analysis=GleoIssueAnalysis(this){allowed()&&channel.consented()}
        safety=CarSafetyGate(this){if(it!=EditingState.ALLOWED){analysis.cancel();speech.stop();busy=false};maybeHandsFree();draw()}
        draw()
    }
    override fun onStart(){super.onStart();visible=true;safety.start()}
    override fun onStop(){visible=false;analysis.cancel();speech.stop();busy=false;safety.stop();super.onStop()}
    override fun onDestroy(){analysis.close();super.onDestroy()}
    private fun matching(query:String):List<VehicleIssue> {
        if(query.isBlank()||query.trim() in setOf("이거","이 문제","그거","최근 문제"))return emptyList()
        return SqliteIssueRepository(this).use{r->r.issues().filter{it.situation.contains(query.trim(),true)}}
    }
    private fun maybeHandsFree(){
        val r=request?:return
        if(!allowed()||handled||recreated||!channel.consented()||channel.remainingMs==0L)return
        handled=true
        when(r.function){
            CaritdaActions.QUERY->readLocal(true)
            CaritdaActions.DRAFT->finishReply(CaritdaActions.Reply("asking","Please review the symptom and confirm the recording person in CARITDA while parked.","증상과 기록할 사람을 확인한 뒤 직접 저장해 주세요."),true)
            CaritdaActions.ANALYZE->{
                val issue=matching(r.recordQuery).singleOrNull()
                if(issue==null){finishReply(CaritdaActions.Reply("asking","Please select one existing issue in CARITDA while parked.","분석할 기록 한 건을 선택해 주세요."),true);return}
                busy=true;draw()
                analysis.run(issue,r.fresh,(channel.remainingMs-350).coerceAtLeast(0),current={SqliteIssueRepository(this).use{it.issue(issue.id)}}){finishReply(it,true)}
            }
        }
    }
    private fun finishReply(value:CaritdaActions.Reply,external:Boolean){
        busy=false;reply=value
        if(external&&allowed()&&channel.remainingMs>0)channel.send(CaritdaActions.callback(request!!.function,value.status,value.message,value.data,"voice"))
        draw()
    }
    private fun readLocal(external:Boolean){
        if(!allowed())return
        runCatching{SqliteIssueRepository(this).use{repo->ObservationStore(this).use{obs->
            val service=CaritdaService(repo,{allowed()});service.restoreConfiguration(ActionDriverSession.id)
            CaritdaActions.queryReply(request!!.topic,service,obs)
        }}}.onSuccess{finishReply(it,external)}.onFailure{finishReply(CaritdaActions.Reply("fail","The local records could not be read.","기록을 확인하지 못했어요. 원래 기록은 유지돼요."),external)}
    }
    private fun draw(){
        if(!::safety.isInitialized)return
        val dark=resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
        val ui=CaritdaComponents(this,CaritdaPalette.of(dark));val body=ui.column().apply{setPadding(ui.dp(24),ui.dp(16),ui.dp(24),ui.dp(16));setBackgroundColor(ui.colors.background)}
        fun state(title:String,text:String){body.addView(ui.state(title,text))}
        state(if(packageName.endsWith(".validation"))"검증 전용 · TEST · 음성 요청"else"CARITDA 음성 요청","차량 정보·문제 기록·인계 내용을 확인해요.")
        val r=request
        if(!allowed())state("안전하게 정차한 뒤 확인해 주세요.","현재는 기록 조회와 AI 조작을 할 수 없어요.")
        else if(r==null)state("요청을 확인하지 못했어요.","지원하지 않는 항목이거나 내용이 너무 길어요. 기록은 변경하지 않았어요.")
        else {
            if(channel.target()==null)state("앱에서 요청을 확인해 주세요.","이 기기의 Gleo 호출자와 응답 연결을 확인하지 못했어요. 외부로 기록을 보내지 않아요.")
            else if(!channel.consented()){
                state("음성으로 기록을 확인할까요?","Gleo에 요청한 증상 요약·상태 또는 차량 수치와 수신 시각을 보내요. 이름·메모·위치는 보내지 않아요. AI 새 분석 동의는 별도예요.")
                body.addView(ui.action("음성 조회에 동의","gleo_consent",ActionStyle.SECONDARY,true){channel.allowReadOnceConfigured();draw()})
            }else body.addView(ui.action("Gleo 음성 조회 동의 철회","gleo_revoke",ActionStyle.QUIET,!busy){channel.revoke();analysis.cancel();busy=false;draw()})
            reply?.let{state(if(it.status=="success")"확인한 내용"else"요청 안내",it.text)}
            if(busy)state("새 AI 설명을 확인하고 있어요.","응답이 늦으면 앱에서 확인해 주세요. 자동으로 재요청하지 않아요.")
            when(r.function){
                CaritdaActions.QUERY->body.addView(ui.action("요청한 내용 확인","action_confirm",ActionStyle.PRIMARY,!busy){localReading=true;readLocal(false)})
                CaritdaActions.DRAFT->{
                    state("작성할 내용",r.situation.ifBlank{"어떤 증상인지 알려 주세요. 앱에서 직접 입력할 수도 있어요."})
                    body.addView(ui.action("증상 작성으로 이동","action_draft",ActionStyle.PRIMARY,true){if(allowed()){startActivity(Intent(this,MainActivity::class.java).putExtra("voice_draft",r.situation));finish()}})
                }
                CaritdaActions.ANALYZE->{
                    state(if(r.fresh)"새 AI 분석 요청"else"저장된 AI 설명 조회","분석할 한 건을 선택해 주세요. 처음에는 로그인·전송 동의가 필요해요. 새 분석은 앱에서 이용 한도와 모델 상태를 확인해요.")
                    val found=if(localReading)matching(r.recordQuery)else emptyList()
                    if(!localReading)body.addView(ui.action("분석할 기록 확인","action_analysis_confirm",ActionStyle.PRIMARY,true){localReading=true;draw()})
                    else if(found.isEmpty())state("기록을 선택해 주세요.","‘이거’가 어떤 기록인지 추측하지 않아요. CARITDA의 기록 목록에서 한 건을 선택해 주세요.")
                    found.take(3).forEach{issue->body.addView(ui.action(issue.type.label+" · "+issue.situation.take(100),"action_issue_"+issue.id,ActionStyle.SECONDARY,!busy){if(allowed())startActivity(Intent(this,PortableAiActivity::class.java).putExtra("issue_id",issue.id))})}
                }
            }
            if(localReading&&reply!=null)body.addView(ui.action("확인한 내용 음성으로 듣기","action_speak",ActionStyle.QUIET,!speech.busy&&!busy){if(allowed())speech.speak(reply!!.text.take(600)){}})
        }
        body.addView(ui.action("CARITDA 열기","action_open",ActionStyle.SECONDARY,true){startActivity(Intent(this,MainActivity::class.java));finish()})
        setContentView(ScrollView(this).apply{addView(body)})
    }
}
