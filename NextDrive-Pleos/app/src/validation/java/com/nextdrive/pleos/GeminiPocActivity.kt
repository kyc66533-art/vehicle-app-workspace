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
        body.addView(ui.text("Gemini 실제 연결 시험",28,true))
        body.addView(ui.text("합성 SOC 80 → 60 → 18% · 기준 20% 미만\n실제 차량값이 아니에요. 사용자 기록이나 이름은 전송하지 않아요.",22).apply{tag="gemini_synthetic"})
        body.addView(ui.text("로컬 규칙 참고: 합성 충전량이 설정 기준보다 낮아요. 아래 실제 AI 응답과 다른 설명이에요.",20))
        body.addView(ui.text("Worker 시험 토큰만 입력해 주세요. Gemini API 키를 입력하지 마세요. 입력값은 저장하지 않고 화면을 떠나면 지워요.",22))
        token=EditText(this).apply{tag="gemini_token";hint="임시 앱 시험 토큰";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;minHeight=ui.dp(76);textSize=22f;setTextColor(ui.colors.text)};body.addView(token)
        deployed=CheckBox(this).apply{tag="gemini_deployed";text="보안 Worker 배포와 시험 호출 승인을 확인했어요.";textSize=22f;setTextColor(ui.colors.text);minHeight=ui.dp(76);setOnCheckedChangeListener{_,_->update()}};body.addView(deployed)
        consentBox=CheckBox(this).apply{tag="gemini_consent";text="합성 SOC 내용만 Gemini로 보내는 데 동의해요.";textSize=22f;setTextColor(ui.colors.text);minHeight=ui.dp(76);setOnCheckedChangeListener{_,_->update()}};body.addView(consentBox)
        preflight=ui.action("보안 연결 확인 · 모델 호출 없음","gemini_preflight",ActionStyle.SECONDARY,false,HmiIcon.CHECK){request(true)};body.addView(preflight)
        send=ui.action("실제 Gemini 설명 요청","gemini_send",ActionStyle.PRIMARY,false,HmiIcon.RECORD){request(false)};body.addView(send)
        cancel=ui.action("요청·동의 취소","gemini_cancel",ActionStyle.SECONDARY,true,HmiIcon.BACK){stopRequest("요청을 취소했어요. 이미 서버에 전달됐다면 모델 처리는 끝날 수 있어요.")};body.addView(cancel)
        state=ui.text("아직 실제 모델을 요청하지 않았어요.",22).apply{tag="gemini_state";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};body.addView(state)
        result=ui.text("실제 AI 응답: 없음",24).apply{tag="gemini_result"};body.addView(result)
        token.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,st:Int,before:Int,count:Int){update()};override fun afterTextChanged(s:Editable?) {}})
        safety=CarSafetyGate(this){if(it!=EditingState.ALLOWED&&busy)stopRequest("입력 제한으로 요청과 표시를 중단했어요.");update()}
    }
    private fun update(){if(!::send.isInitialized)return;val allowed=::safety.isInitialized&&safety.state==EditingState.ALLOWED
        send.isEnabled=allowed&&!busy&&deployed.isChecked&&consentBox.isChecked&&Regex("[A-Za-z0-9_-]{43}").matches(token.text.toString())
        preflight.isEnabled=send.isEnabled
        token.isEnabled=allowed&&!busy;consentBox.isEnabled=allowed&&!busy;deployed.isEnabled=allowed&&!busy
    }
    private fun request(checkOnly:Boolean){
        if(safety.state!=EditingState.ALLOWED||!send.isEnabled||busy)return
        val payload=SyntheticSocFixture.payload();val approval=AnalysisConsent(payload).also{it.accept()};consent=approval
        val transient=token.text.toString();val requestId=java.util.UUID.randomUUID().toString();val run=++generation
        val relay=AiRelayClient(URL("https://caritda-ai-relay-dev.kyc66533.workers.dev/v1/synthetic-soc")){transient};client=relay
        busy=true;token.text.clear();state.text=if(checkOnly)"모델을 호출하지 않고 중계 인증을 확인하고 있어요."else"실제 Gemini에 합성값만 요청하고 있어요. 자동 재시도하지 않아요.";result.text=if(checkOnly)"실제 AI 응답: 요청하지 않음"else"실제 AI 응답: 대기 중";update()
        worker.execute{val response=runCatching{relay.explainAnalysis(payload,approval,requestId,checkOnly)};runOnUiThread{
            if(run!=generation||isDestroyed||isFinishing||safety.state!=EditingState.ALLOWED)return@runOnUiThread
            busy=false;approval.revoke();consent=null;client=null
            response.onSuccess{value->if(value.origin=="PREFLIGHT"){state.text=value.text;result.text="실제 AI 응답: 모델 호출 없음"}else{state.text="실제 모델 응답 · ${value.model} · ${value.latencyMs}ms\n합성 데이터 시험이며 실제 차량 분석이 아니에요.";result.text="Gemini 실제 모델 응답\n${value.text}"}}
                .onFailure{e->state.text=if(e is RelayFailure)e.message else "연결 또는 응답 검증에 실패했어요. Worker 배포·인증·인터넷을 확인해 주세요.";result.text="실제 AI 응답: 표시할 수 없음"}
            update()
        }}
    }
    private fun stopRequest(message:String){generation++;consent?.revoke();consent=null;client?.cancel();client=null;busy=false;if(::token.isInitialized)token.text.clear();if(::consentBox.isInitialized)consentBox.isChecked=false;if(::state.isInitialized)state.text=message;update()}
    override fun onStart(){super.onStart();safety.start()}
    override fun onStop(){stopRequest("화면을 떠나 인증과 동의를 지웠어요. 자동 요청하지 않아요.");safety.stop();super.onStop()}
    override fun onDestroy(){worker.shutdown();super.onDestroy()}
}
