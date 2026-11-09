package com.nextdrive.pleos

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.ui.*

/** Day1 compatibility screen only. No Worker/Gemini invocation or user vehicle DB access. */
class AuthCompatibilityActivity:Activity() {
    private lateinit var gate:CarSafetyGate
    private lateinit var email:EditText
    private lateinit var password:EditText
    private lateinit var status:TextView
    private lateinit var diagnostic:TextView
    private lateinit var safetyMessage:TextView
    private lateinit var login:Button
    private lateinit var inspect:Button
    private lateinit var logout:Button
    private var stopObserving:(()->Unit)?=null
    private var visible=false
    private var generation=0
    override fun onCreate(saved:Bundle?) {super.onCreate(saved);check(packageName=="com.nextdrive.pleos.validation")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val dark=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        val ui=CaritdaComponents(this,CaritdaPalette.of(dark));val root=ui.column().apply{setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(12));setBackgroundColor(ui.colors.background)}
        setContentView(root);root.addView(ui.text("검증 전용 · TEST · Firebase 호환성",22,true))
        root.addView(ui.action("이전 화면으로","auth_back",ActionStyle.QUIET,true,HmiIcon.BACK){finish()})
        val body=ui.column();root.addView(ScrollView(this).apply{addView(body)},LinearLayout.LayoutParams(-1,0,1f))
        body.addView(ui.text("최초 로그인과 세션 유지 확인",28,true))
        body.addView(ui.text("관리자가 등록한 시험 계정으로 로그인해요. Firebase에 이메일과 비밀번호가 전송돼요. 차량 기록과 Gemini 요청은 보내지 않아요.",22))
        fun input(name:String,label:String,type:Int)=EditText(this).apply{tag=name;hint=label;inputType=type;textSize=22f;minHeight=ui.dp(76);setTextColor(ui.colors.text);isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS}
        email=input("auth_email","이메일",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);body.addView(email)
        password=input("auth_password","비밀번호",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD);body.addView(password)
        login=ui.action("로그인","auth_login",ActionStyle.PRIMARY,false,HmiIcon.PERSON){
            if(!allowed()||FirebaseSessionProvider.busy)return@action
            val user=email.text.toString();val secret=password.text.toString()
            if(user.isBlank()){email.error="이메일을 입력해 주세요.";return@action}
            if(secret.isEmpty()){password.error="비밀번호를 입력해 주세요.";return@action}
            password.text.clear();status.text="로그인하고 있어요.";val run=generation
            FirebaseSessionProvider.signIn(user,secret){if(visible&&run==generation)show(it)}
        };body.addView(login)
        inspect=ui.action("세션·ID 토큰 확인","auth_inspect",ActionStyle.SECONDARY,false,HmiIcon.CHECK){
            if(!allowed()||FirebaseSessionProvider.busy)return@action
            status.text="세션과 인증을 확인하고 있어요.";val run=generation;FirebaseSessionProvider.inspectToken{if(visible&&run==generation)show(it)}
        };body.addView(inspect)
        logout=ui.action("로그아웃","auth_logout",ActionStyle.QUIET,false,HmiIcon.BACK){if(allowed()){generation++;password.text.clear();show(FirebaseSessionProvider.signOut())}};body.addView(logout)
        status=ui.text("설정을 확인하고 있어요.",22).apply{tag="auth_status";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};body.addView(status)
        diagnostic=ui.text("",20).apply{tag="auth_diagnostic"};body.addView(diagnostic)
        safetyMessage=ui.text("",20).apply{tag="auth_gate"};body.addView(safetyMessage)
        body.addView(ui.action("기기 서명 기능 확인 · 서버 전송 없음","auth_key",ActionStyle.SECONDARY,true,HmiIcon.CHECK){
            if(!allowed())return@action
            val result=runCatching{val key=DeviceKeyStore("caritda.auth.compatibility.${packageName}");key.ensure();val bytes="CARITDA local key compatibility only".toByteArray();check(key.verify(bytes,key.sign(bytes)))}
            status.text=if(result.isSuccess)"이 기기에서 서명이 동작해요. 서버 등록은 아직 하지 않았어요."else"기기 서명을 확인하지 못했어요. 기존 기록은 그대로예요."
            diagnostic.text=if(result.isSuccess)"DEVICE_KEY_LOCAL_PASS · 서버 등록/하드웨어 인증 아님"else"DEVICE_KEY_FAILED"
        })
        body.addView(ui.text("토큰 원문·UID·비밀번호는 표시하거나 로그에 남기지 않아요. 인증 확인은 서버 검증·기기 등록·AI 응답 성공을 뜻하지 않아요.",20))
        gate=CarSafetyGate(this){if(it!=EditingState.ALLOWED){password.text.clear();generation++};update()}
        show(FirebaseSessionProvider.prepare(this))
    }
    private fun allowed()=::gate.isInitialized&&gate.state==EditingState.ALLOWED
    private fun update(){if(!::login.isInitialized)return
        val enabled=allowed()&&FirebaseSessionProvider.configState=="READY"&&FirebaseSessionProvider.initialized&&!FirebaseSessionProvider.busy
        login.isEnabled=enabled;inspect.isEnabled=enabled&&FirebaseSessionProvider.snapshot().signedIn;logout.isEnabled=allowed()&&FirebaseSessionProvider.snapshot().signedIn
        email.isEnabled=enabled;password.isEnabled=enabled
        if(::safetyMessage.isInitialized)safetyMessage.text=when {
            !::gate.isInitialized||gate.state==EditingState.UNKNOWN->"차량의 입력 허용 상태를 확인하고 있어요."
            gate.state==EditingState.RESTRICTED->"주행 중에는 로그인할 수 없어요. 안전하게 정차한 뒤 이용해 주세요."
            FirebaseSessionProvider.busy->"인증 요청 중이에요. 중복 요청하지 않아요."
            else->"정차·입력 허용 상태예요."
        }
        window.decorView.findViewWithTag<View>("auth_key")?.isEnabled=allowed()&&!FirebaseSessionProvider.busy
    }
    private fun show(result:FirebaseSessionProvider.Result){
        status.text=when(result.code){
            "AUTH_CONFIG_MISSING"->"Firebase 공개 설정이 아직 없어요. 관리자가 설정 파일을 준비해야 해요."
            "AUTH_CONFIG_PACKAGE_MISMATCH","AUTH_PACKAGE_MISMATCH"->"이 TEST 앱의 Firebase 등록을 확인해 주세요. 일반 앱과 패키지가 달라요."
            "AUTH_SIGNED_IN"->"로그인돼 있어요. 앱을 다시 연 뒤 같은 상태인지 확인해 주세요."
            "AUTH_SIGNED_OUT","AUTH_SIGN_IN_REQUIRED"->"최초 한 번 로그인해 주세요. 차량 기록을 남길 사람 선택과는 별개예요."
            "AUTH_RESTORING"->"저장된 로그인 상태를 확인하고 있어요. 잠시 기다려 주세요."
            "AUTH_TOKEN_AVAILABLE"->"ID 토큰을 확보했어요. 원문은 표시하지 않아요. 서버 인가 검증은 다음 단계예요."
            "AUTH_NETWORK"->"로그인 서버에 연결하지 못했어요. 인터넷을 확인해 주세요."
            "AUTH_CREDENTIALS","AUTH_INPUT"->"이메일과 비밀번호를 확인해 주세요."
            "AUTH_RATE_LIMIT"->"로그인 요청이 많아요. 잠시 후 직접 다시 시도해 주세요."
            "AUTH_RELOGIN","AUTH_DISABLED"->"계정 상태를 확인하고 다시 로그인해 주세요."
            "AUTH_PROVIDER_DISABLED","AUTH_PROJECT_CONFIGURATION"->"Firebase 로그인 설정을 관리자가 확인해야 해요."
            "AUTH_CANCELLED"->"로그인 작업이 취소됐어요."
            else->"인증을 확인하지 못했어요. 기존 차량 기록은 그대로예요."
        }
        diagnostic.text=result.code+(if(result.tokenAvailable)" · TOKEN_PRESENT (원문 비표시)"else "")
        update()
    }
    override fun onStart(){super.onStart();visible=true;stopObserving=FirebaseSessionProvider.observe{if(visible){if(!FirebaseSessionProvider.busy&&diagnostic.text.toString()=="AUTH_RESTORING")show(FirebaseSessionProvider.snapshot())else update()}};gate.start();if(diagnostic.text.toString()=="AUTH_RESTORING")show(FirebaseSessionProvider.snapshot())else update()}
    override fun onStop(){visible=false;generation++;password.text.clear();email.text.clear();stopObserving?.invoke();stopObserving=null;gate.stop();super.onStop()}
}
