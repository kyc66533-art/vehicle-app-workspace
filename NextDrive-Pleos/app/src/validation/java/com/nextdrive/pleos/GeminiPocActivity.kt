package com.nextdrive.pleos

import android.app.Activity
import android.os.Bundle
import android.content.res.Configuration
import android.text.*
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.ui.*
import java.net.URL

/** Validation variant only. Provider key, production records and automatic requests are absent. */
class GeminiPocActivity:Activity(){
    private lateinit var safety:CarSafetyGate
    private lateinit var token:EditText
    private lateinit var consentBox:CheckBox
    private lateinit var deployed:CheckBox
    private lateinit var send:Button
    private lateinit var preflight:Button
    private lateinit var cancel:Button
    private lateinit var result:TextView
    private lateinit var state:TextView
    private lateinit var remember:CheckBox
    private lateinit var reason:TextView
    private lateinit var diagnostic:TextView
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val expiry=Runnable{if(PocTokenSession.read()==null)stopRequest("시험 인증 기억 시간이 끝났어요. 다시 입력해 주세요.")}
    private var consent:AnalysisConsent?=null
    private var client:AiRelayClient?=null
    private var generation=0
    private var busy=false
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    override fun onCreate(saved:Bundle?){super.onCreate(saved);check(packageName=="com.nextdrive.pleos.validation")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val dark=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        val ui=CaritdaComponents(this,CaritdaPalette.of(dark));val root=ui.column().apply{setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(12));setBackgroundColor(ui.colors.background)}
        setContentView(root);root.addView(ui.text("검증 전용 · TEST",20,true))
        root.addView(ui.action("이전 화면으로","gemini_back",ActionStyle.QUIET,true,HmiIcon.BACK){finish()})
        val body=ui.column();root.addView(ScrollView(this).apply{addView(body)},LinearLayout.LayoutParams(-1,0,1f))
        body.addView(ui.text("개발자 진단 · Gemini 연결 시험",28,true))
        body.addView(ui.text("합성 SOC 80 → 60 → 18% · 기준 20% 미만\n실제 차량값이 아니에요. 사용자 기록이나 이름은 전송하지 않아요.",22).apply{tag="gemini_synthetic"})
        body.addView(ui.text("로컬 규칙 참고: 합성 충전량이 설정 기준보다 낮아요. 아래 실제 AI 응답과 다른 설명이에요.",20))
        body.addView(ui.text("개발자용 화면이에요. Worker 시험 토큰만 입력해 주세요. Gemini API 키는 입력하지 마세요. 파일에는 저장하지 않아요.",22))
        token=EditText(this).apply{tag="gemini_token";hint="임시 앱 시험 토큰";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;minHeight=ui.dp(76);textSize=22f;setTextColor(ui.colors.text)};body.addView(token)
        deployed=CheckBox(this).apply{tag="gemini_deployed";text="보안 Worker 배포와 시험 호출 승인을 확인했어요.";textSize=22f;setTextColor(ui.colors.text);minHeight=ui.dp(76);setOnCheckedChangeListener{_,_->update()}};body.addView(deployed)
        remember=CheckBox(this).apply{tag="gemini_remember";text="이 TEST 앱에서만 인증을 최대 10분간 기억해요.\n메모리 전용 · 화면을 나가도 잠시 유지 · 사용 후 지워 주세요.";textSize=22f;setTextColor(ui.colors.text);minHeight=ui.dp(76);setOnCheckedChangeListener{_,checked->
            if(!checked)PocTokenSession.clear() else if(Regex("[A-Za-z0-9_-]{43}").matches(token.text.toString())){PocTokenSession.keep(token.text.toString());scheduleExpiry()};update()}};body.addView(remember)
        consentBox=CheckBox(this).apply{tag="gemini_consent";text="합성 SOC 내용만 Gemini로 보내는 데 동의해요.";textSize=22f;setTextColor(ui.colors.text);minHeight=ui.dp(76);setOnCheckedChangeListener{_,_->update()}};body.addView(consentBox)
        preflight=ui.action("보안 연결 확인 · 모델 호출 없음","gemini_preflight",ActionStyle.SECONDARY,false,HmiIcon.CHECK){request(true)};body.addView(preflight)
        send=ui.action("실제 Gemini 설명 요청","gemini_send",ActionStyle.PRIMARY,false,HmiIcon.RECORD){request(false)};body.addView(send)
        cancel=ui.action("요청·동의 취소","gemini_cancel",ActionStyle.SECONDARY,true,HmiIcon.BACK){stopRequest("요청을 취소했어요. 이미 서버에 전달됐다면 모델 처리는 끝날 수 있어요.")};body.addView(cancel)
        body.addView(ui.action("시험 인증 지우기","gemini_clear",ActionStyle.QUIET,true,HmiIcon.BACK){stopRequest("시험 인증과 동의를 지웠어요.")})
        reason=ui.text("",20).apply{tag="gemini_disabled_reason"};body.addView(reason)
        state=ui.text("아직 실제 모델을 요청하지 않았어요.",22).apply{tag="gemini_state";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};body.addView(state)
        result=ui.text("실제 AI 응답: 없음",24).apply{tag="gemini_result"};body.addView(result)
        diagnostic=ui.text("진단 코드: 아직 없음",20).apply{tag="gemini_diagnostic"};body.addView(diagnostic)
        body.addView(ui.text("진단에는 허용된 코드와 HTTP 상태만 표시해요. 키·토큰·모델 원문은 진단이나 로그에 남기지 않아요.",20))
        token.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,st:Int,before:Int,count:Int){update()};override fun afterTextChanged(s:Editable?) {}})
        safety=CarSafetyGate(this){if(it==EditingState.RESTRICTED||it!=EditingState.ALLOWED&&busy)stopRequest("입력 제한으로 요청과 표시를 중단했어요.");update()}
    }
    private fun update(){if(!::send.isInitialized)return;val allowed=::safety.isInitialized&&safety.state==EditingState.ALLOWED
        send.isEnabled=allowed&&!busy&&deployed.isChecked&&consentBox.isChecked&&Regex("[A-Za-z0-9_-]{43}").matches(token.text.toString())
        preflight.isEnabled=send.isEnabled
        token.isEnabled=allowed&&!busy;consentBox.isEnabled=allowed&&!busy;deployed.isEnabled=allowed&&!busy
        if(::remember.isInitialized)remember.isEnabled=allowed&&!busy
        if(::reason.isInitialized)reason.text=when{
            !allowed->"정차·입력 허용 상태를 확인한 뒤 이용해 주세요."
            busy->"요청 중이에요. 중복 요청하지 않아요."
            !Regex("[A-Za-z0-9_-]{43}").matches(token.text.toString())->"43자리 임시 시험 토큰을 입력해 주세요."
            !deployed.isChecked->"배포·시험 호출 승인 확인이 필요해요."
            !consentBox.isChecked->"합성 SOC 전송에 동의해 주세요."
            else->"준비됐어요. 보안 연결 확인은 모델을 호출하지 않아요."
        }
    }
    private fun request(checkOnly:Boolean){
        if(safety.state!=EditingState.ALLOWED||!send.isEnabled||busy)return
        val payload=SyntheticSocFixture.payload();val approval=AnalysisConsent(payload).also{it.accept()};consent=approval
        val transient=token.text.toString();val requestId=java.util.UUID.randomUUID().toString();val run=++generation
        val relay=AiRelayClient(URL("https://caritda-ai-relay-dev.kyc66533.workers.dev/v1/synthetic-soc")){transient};client=relay
        if(remember.isChecked){PocTokenSession.keep(transient);scheduleExpiry()}
        busy=true;if(!remember.isChecked)token.text.clear();diagnostic.text="진단 코드: 요청 중"
        state.text=if(checkOnly)"모델을 호출하지 않고 중계 인증을 확인하고 있어요."else"실제 Gemini에 합성값만 요청하고 있어요. 자동 재시도하지 않아요.";result.text=if(checkOnly)"실제 AI 응답: 요청하지 않음"else"실제 AI 응답: 대기 중";update()
        worker.execute{val response=runCatching{relay.explainAnalysis(payload,approval,requestId,checkOnly)};runOnUiThread{
            if(run!=generation||isDestroyed||isFinishing||safety.state!=EditingState.ALLOWED)return@runOnUiThread
            busy=false;approval.revoke();consent=null;client=null
            response.onSuccess{value->diagnostic.text=if(value.origin=="PREFLIGHT")"단계: PREFLIGHT · 모델 호출 없음\n${value.diagnostic}"else value.diagnostic
                if(value.origin=="PREFLIGHT"){state.text=value.text;result.text="실제 AI 응답: 모델 호출 없음"}else{state.text="실제 모델 응답 · ${value.model} · ${value.latencyMs}ms\n합성 데이터 시험이며 실제 차량 분석이 아니에요.";result.text="Gemini 실제 모델 응답\n${value.text}"}}
                .onFailure{e->
                    state.text=when(e){is RelayFailure->e.message;is AnalysisValidationFailure->"앱의 근거 검증을 통과하지 못했어요. 분석값은 그대로 유지돼요.";else->"연결 또는 응답 검증에 실패했어요. 인터넷과 배포 버전을 확인해 주세요."}
                    diagnostic.text=when(e){is RelayFailure->"HTTP ${e.status} · ${e.code}\n${e.diagnostic}";is AnalysisValidationFailure->"단계: APP_VALIDATION · ${e.code}";else->"단계: APP_CONNECTION · 응답 원문은 표시하지 않아요."}
                    if(e is RelayFailure&&(e.status==401||e.code=="TOKEN_EXPIRED")){PocTokenSession.clear();token.text.clear();remember.isChecked=false}
                    result.text="실제 AI 응답: 표시할 수 없음"
                }
            update()
        }}
    }
    private fun stopRequest(message:String,clearAuth:Boolean=true){generation++;consent?.revoke();consent=null;client?.cancel();client=null;busy=false
        if(clearAuth){PocTokenSession.clear();if(::remember.isInitialized)remember.isChecked=false}
        if(::token.isInitialized)token.text.clear();if(::consentBox.isInitialized)consentBox.isChecked=false;if(::state.isInitialized)state.text=message;update()}
    private fun scheduleExpiry(){handler.removeCallbacks(expiry);val remaining=PocTokenSession.remaining();if(remaining>0)handler.postDelayed(expiry,remaining)}
    override fun onStart(){super.onStart();PocTokenSession.read()?.let{remember.isChecked=true;token.setText(it);scheduleExpiry()};safety.start();update()}
    override fun onStop(){
        stopRequest("화면을 떠나 요청과 동의를 취소했어요. 자동 요청하지 않아요.",!remember.isChecked)
        handler.removeCallbacks(expiry);safety.stop();super.onStop()
    }
    override fun onDestroy(){handler.removeCallbacks(expiry);worker.shutdown();super.onDestroy()}
}
