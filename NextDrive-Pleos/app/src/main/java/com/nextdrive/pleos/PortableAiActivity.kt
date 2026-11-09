package com.nextdrive.pleos

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.ai.auth.*
import com.nextdrive.pleos.analytics.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.ui.*
import com.nextdrive.pleos.vehicle.*
import org.json.JSONObject
import java.util.concurrent.Executors

/** First-use account/device/consent flow. No common token, vehicle writes or automatic login. */
class PortableAiActivity:Activity(){
    private lateinit var gate:CarSafetyGate
    private lateinit var ui:CaritdaComponents
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private lateinit var email:EditText
    private lateinit var password:EditText
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var visible=false
    @Volatile private var generation=0
    private var busy=false
    private var analysis:VehicleAnalysis?=null
    private var client:PortableAiClient?=null
    private var activeStore:PortableAiStore?=null
    private var stopObserving:(()->Unit)?=null
    private var autoConsumed=false
    private var loadedSubject:String?=null
    private var message="저장된 충전량을 확인하고 있어요."
    private var resultText=""
    override fun onCreate(saved:Bundle?){super.onCreate(saved)
        autoConsumed=saved?.getBoolean("auto_consumed")?:false
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val dark=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        ui=CaritdaComponents(this,CaritdaPalette.of(dark))
        val root=ui.column().apply{setBackgroundColor(ui.colors.background);setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(12))};setContentView(root)
        if(packageName.endsWith(".validation"))root.addView(ui.text("검증 전용 · TEST",22,true))
        root.addView(ui.action("이전 화면으로","portable_back",ActionStyle.QUIET,true,HmiIcon.BACK){finish()})
        body=ui.column();root.addView(ScrollView(this).apply{addView(body)},LinearLayout.LayoutParams(-1,0,1f))
        gate=CarSafetyGate(this){if(it!=EditingState.ALLOWED){cancelWork();if(::password.isInitialized)password.text.clear()};render()}
        FirebaseSessionProvider.prepare(this);message="저장된 충전량을 확인하고 있어요.";render()
    }
    private fun allowed()=visible&&::gate.isInitialized&&gate.state==EditingState.ALLOWED
    private fun storeName(subject:String)="portable-ai-v2-$subject"
    private fun state():JSONObject=FirebaseSessionProvider.subject()?.let{PortableAiStore(this,storeName(it)).read()}?:JSONObject()
    private fun consentReady()=runCatching{state().optString("consent")==PortableAnalysis.CONSENT}.getOrDefault(false)
    private fun cancelWork(){generation++;client?.cancel();client=null;activeStore?.invalidate();activeStore=null;busy=false}
    private fun render(){if(!::body.isInitialized)return
        val draftEmail=if(::email.isInitialized)email.text.toString()else""
        body.removeAllViews();body.addView(ui.text("AI로 쉽게 설명",28,true))
        body.addView(ui.text("차량에서 받은 충전량 기록만 설명해요. 고장 진단이나 운행 안전 판단은 하지 않아요.",22))
        if(!allowed())body.addView(ui.state("안전하게 정차한 뒤 이용해 주세요.","입력 허용 상태가 확인돼야 로그인·AI 요청을 할 수 있어요."))
        status=ui.text(message,22).apply{tag="portable_status";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};body.addView(status)
        if(!allowed()){status.text="현재는 AI 조작을 할 수 없어요. 안전하게 정차한 뒤 확인해 주세요.";return}
        val signed=FirebaseSessionProvider.snapshot().signedIn
        fun input(tagName:String,label:String,type:Int)=EditText(this).apply{tag=tagName;hint=label;inputType=type;textSize=22f;minHeight=ui.dp(76);setTextColor(ui.colors.text);isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS}
        if(!signed){
            body.addView(ui.text("최초 한 번 계정으로 로그인해 주세요. 기록할 사람 이름과는 별개예요. 이메일·비밀번호는 Firebase로 전송돼요.",22))
            email=input("portable_email","이메일",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply{setText(draftEmail)};body.addView(email)
            password=input("portable_password","비밀번호",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD);body.addView(password)
            body.addView(ui.action("로그인","portable_login",ActionStyle.PRIMARY,allowed()&&!busy&&!FirebaseSessionProvider.busy&&FirebaseSessionProvider.initialized,HmiIcon.PERSON){
                if(!allowed()||busy)return@action
                val e=email.text.toString();val p=password.text.toString();password.text.clear();val run=generation
                message="로그인하고 있어요.";FirebaseSessionProvider.signIn(e,p){r->if(visible&&run==generation){message=if(r.signedIn)"로그인됐어요. 처음에는 전송 안내를 확인해 주세요."else PortableFailure(r.code).userMessage();render()}}
            });return
        }
        val a=analysis
        if(a?.latest!=null)body.addView(ui.metric("설명할 충전량",VehicleAnalysis.fmt(a.latest!!.number)+" %","최근 수신 기록 · ${a.points.size}건","Pleos 에뮬레이터에서 받은 값이에요.","portable_soc",HmiIcon.BATTERY))
        if(!consentReady()){
            autoConsumed=true // First-use login/registration never silently calls the model.
            body.addView(ui.state("처음 한 번 전송에 동의해 주세요.","충전량의 최근값·최소·최대·관측 평균·비교 가능한 변화량, 표본 수·상대 경과시간·품질 요약을 Cloudflare 중계와 Google Gemini로 보내요. 이름·VIN·위치·증상 원문·원본 DB는 보내지 않아요. 로그인 계정과 기기 서명은 중계 인증에만 사용해요. 다음 요청부터 같은 범위에 동의를 유지하며 언제든 철회할 수 있어요."))
            body.addView(ui.action("동의하고 이 기기 등록","portable_register",ActionStyle.PRIMARY,allowed()&&!busy,HmiIcon.CHECK){sessionTask {api->api.register();"AI 이용 동의와 기기 등록을 저장했어요."}})
            body.addView(ui.action("동의하지 않고 돌아가기","portable_consent_cancel",ActionStyle.SECONDARY,!busy,HmiIcon.BACK){finish()})
        }else{
            body.addView(ui.text("AI 이용 동의가 유지돼요. 같은 범위의 설명에는 로그인·체크박스를 반복하지 않아요.",20))
            body.addView(ui.action(if(state().has("pending"))"이전 요청 결과 확인"else"AI로 쉽게 설명","portable_explain",ActionStyle.PRIMARY,allowed()&&!busy&&a?.latest!=null,HmiIcon.RECORD){requestExplanation()})
            body.addView(ui.action("AI 이용 동의 철회","portable_withdraw",ActionStyle.SECONDARY,allowed()&&!busy,HmiIcon.BACK){
                sessionTask {api->
                    val revoked=runCatching{api.revoke()}.isSuccess
                    api.withdrawLocal()
                    if(revoked)"동의를 철회했어요. 서버의 이 기기 동의와 로컬 AI 결과를 지웠어요. 이미 전송된 요청은 되돌릴 수 없어요."
                    else "이 앱의 동의를 철회했어요. 서버에는 연결하지 못했어요. 관리자에게 기기 동의 해제를 요청해 주세요. 이미 전송된 요청은 되돌릴 수 없어요."
                };resultText=""
            })
        }
        body.addView(ui.action("전송 요약 확인","portable_preview",ActionStyle.QUIET,allowed()&&!busy&&a?.latest!=null,HmiIcon.RECORD){message=runCatching{PortableAnalysis.payload(a!!)}.getOrElse{(it as? PortableFailure)?.userMessage()?:"최근 수신값을 확인해 주세요."};render()})
        if(consentReady())body.addView(ui.action("AI 이용 가능 횟수 확인 · 모델 호출 없음","portable_status_check",ActionStyle.QUIET,allowed()&&!busy,HmiIcon.CHECK){sessionTask {api->val s=api.status();"기존 TEST 사용 ${s.getInt("legacy_total")}회 · 정식 요청 시도 ${s.getInt("portable_attempts")}회 · 현재 승인 잔여 ${s.getInt("remaining")}회\n모델 활성화 ${if(s.getBoolean("model_enabled"))"켜짐"else"꺼짐"} · ${if(s.optBoolean("uncertain_block"))"결과가 불확실한 이전 요청으로 새 호출이 차단돼요."else"모델을 호출하지 않고 확인했어요."}"}})
        if(resultText.isNotBlank())body.addView(ui.state("Gemini가 작성한 설명",resultText).apply{tag="portable_model_result"})
        val cached=runCatching{state().optJSONObject("last")}.getOrNull()
        if(resultText.isBlank()&&cached!=null){val answer=cached.getJSONObject("answer");val text=runCatching{PortableAnalysis.validate(answer,cached.getString("payload"),answer.getString("operation_id"))}.getOrNull();if(text!=null)body.addView(ui.state("이전에 받은 AI 설명 · 현재 상태 판정 아님",text).apply{tag="portable_cached_result"})}
        body.addView(ui.action("로그아웃","portable_logout",ActionStyle.QUIET,allowed()&&!busy,HmiIcon.BACK){cancelWork();FirebaseSessionProvider.subject()?.let{PortableAiStore(this,storeName(it)).withdraw()};FirebaseSessionProvider.signOut();resultText="";message="로그아웃했어요. 차량 기록은 그대로예요.";render()})
        if(!autoConsumed&&intent.getBooleanExtra("request_from_analysis",false)&&allowed()&&!busy&&a?.latest!=null&&consentReady()){
            autoConsumed=true;body.post{if(visible&&allowed()&&!busy)requestExplanation()}
        }
    }
    private fun requestExplanation(){
        if(!allowed()||busy||!consentReady())return
        sessionTask(work={api->
            val a=analysis?:throw PortableFailure("NO_VALID_SOC")
            val payload=if(api.hasPending())""else PortableAnalysis.payload(a)
            val answer=api.explain(payload);val explanation=answer.getString("explanation")
            val source=answer.getJSONObject("facts").getJSONObject("facts")
            "최근 충전량 ${VehicleAnalysis.fmt(source.getDouble("latest"))}%\n$explanation\n차량의 실제 표시와 함께 확인해 주세요.\n모델 · ${answer.getString("model")}"
        },modelResult=true)
    }
    private fun sessionTask(modelResult:Boolean=false,work:(PortableAiClient)->String){
        if(!allowed()||busy)return
        busy=true;val run=generation;message="요청을 확인하고 있어요. 중복 전송하지 않아요.";render()
        FirebaseSessionProvider.withSession {uid,token,error->
            if(!visible||run!=generation||!allowed())return@withSession
            if(error!=null||uid==null||token==null){busy=false;message=PortableFailure(error?:"AUTH_SIGN_IN_REQUIRED").userMessage();render();return@withSession}
            val s=PortableAiStore(this,storeName(VehicleAnalytics.id(uid)));activeStore=s
            val api=PortableAiClient(s,DeviceKeyStore("caritda.portable.${packageName}"),uid,token,allowed={visible&&run==generation&&gate.state==EditingState.ALLOWED&&FirebaseSessionProvider.matches(uid)});client=api
            worker.execute {val result=runCatching{work(api)};runOnUiThread {
                if(visible&&run==generation){busy=false;client=null;activeStore=null;result.onSuccess{if(modelResult){resultText=it;message="AI 설명을 받았어요. 아래에서 확인해 주세요."}else message=it}.onFailure{message=(it as? PortableFailure)?.userMessage()?:"AI 요청을 완료하지 못했어요. 기존 분석은 그대로예요."};render()}
            }}
        }
    }
    private fun loadAnalysis(){val run=generation;worker.execute {
        val r=runCatching{val now=System.currentTimeMillis();ObservationStore(this).use{val refs=it.analysisRefs("EV_SOC",now-86400000);VehicleAnalytics.analyze("EV_SOC",refs.takeLast(2000),now-86400000,now,truncated=refs.size>2000)}}
        runOnUiThread{if(visible&&run==generation){analysis=r.getOrNull();message=if(analysis?.latest!=null)"설명할 최근 충전량이 있어요."else"차량 정보 화면에서 최근 값을 먼저 불러와 주세요.";render()}}
    }}
    override fun onStart(){super.onStart();visible=true;loadedSubject=FirebaseSessionProvider.subject();stopObserving=FirebaseSessionProvider.observe {val next=FirebaseSessionProvider.subject();if(next!=loadedSubject){cancelWork();resultText="";loadedSubject=next;message=if(next==null)"계정으로 로그인해 주세요."else"로그인됐어요. AI 이용 안내를 확인해 주세요."};if(visible)render()};gate.start();loadAnalysis()}
    override fun onStop(){visible=false;cancelWork();resultText="";if(::password.isInitialized)password.text.clear();if(::email.isInitialized)email.text.clear();stopObserving?.invoke();stopObserving=null;gate.stop();super.onStop()}
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("auto_consumed",autoConsumed);super.onSaveInstanceState(out)}
}
