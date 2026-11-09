package com.nextdrive.pleos

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.model.VehicleIssue
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
    @Volatile private var loadGeneration=0
    private var busy=false
    private var analysis:VehicleAnalysis?=null
    private var issue:VehicleIssue?=null
    private val issueMode get()=intent.hasExtra("issue_id")
    private val issueId get()=intent.getStringExtra("issue_id").orEmpty()
    private val consentVersion get()=if(issueMode)IssueExplanation.CONSENT else PortableAnalysis.CONSENT
    private var excerpt:String?=null
    private var excerptInput:EditText?=null
    private var client:PortableAiClient?=null
    private var activeStore:PortableAiStore?=null
    private var stopObserving:(()->Unit)?=null
    private var autoConsumed=false
    private var loadedSubject:String?=null
    private var message="저장된 충전량을 확인하고 있어요."
    private var resultText=""
    private var previewText=""
    override fun onCreate(saved:Bundle?){super.onCreate(saved)
        autoConsumed=saved?.getBoolean("auto_consumed")?:false
        excerpt=saved?.getString("issue_excerpt")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val dark=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        ui=CaritdaComponents(this,CaritdaPalette.of(dark))
        val root=ui.column().apply{setBackgroundColor(ui.colors.background);setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(12))};setContentView(root)
        if(packageName.endsWith(".validation"))root.addView(ui.text("검증 전용 · TEST",22,true))
        root.addView(ui.action("이전 화면으로","portable_back",ActionStyle.QUIET,true,HmiIcon.BACK){finish()})
        status=ui.text("",22).apply{tag="portable_status";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        root.addView(status)
        body=ui.column();root.addView(ScrollView(this).apply{addView(body)},LinearLayout.LayoutParams(-1,0,1f))
        gate=CarSafetyGate(this){if(it!=EditingState.ALLOWED){cancelWork();if(::password.isInitialized)password.text.clear()};render()}
        FirebaseSessionProvider.prepare(this);message="저장된 충전량을 확인하고 있어요.";render()
    }
    private fun allowed()=visible&&::gate.isInitialized&&gate.state==EditingState.ALLOWED
    private fun storeName(subject:String)=if(issueMode)"portable-issue-v1-$subject"else"portable-ai-v2-$subject"
    private fun issueJob()=issue?.let{IssueExplanation.jobKey(it)}
    private fun jobState():JSONObject {val s=state();return if(issueMode)s.optJSONObject("jobs")?.optJSONObject(issueJob().orEmpty())?:JSONObject()else s}
    private fun ready()=if(issueMode)issue!=null else analysis?.latest!=null
    private fun selectedRecord():VehicleIssue? {
        // Instrumentation may supply only a unique validation fixture DB; ordinary app ignores it.
        val fixture=intent.getStringExtra("validation_issue_db")
        val name=if(packageName=="com.nextdrive.pleos.validation"&&fixture!=null&&Regex("issue-ai-fixture-[a-f0-9-]{36}\\.db").matches(fixture))fixture else "caritda_v01.db"
        return SqliteIssueRepository(this,name).use{it.issue(issueId)}
    }
    private fun state():JSONObject=FirebaseSessionProvider.subject()?.let{PortableAiStore(this,storeName(it)).read()}?:JSONObject()
    private fun consentReady()=runCatching{state().optString("consent")==consentVersion}.getOrDefault(false)
    private fun cancelWork(){generation++;client?.cancel();client=null;activeStore?.invalidate();activeStore=null;busy=false}
    private fun render(){if(!::body.isInitialized)return
        val draftEmail=if(::email.isInitialized)email.text.toString()else""
        excerptInput?.let{excerpt=it.text.toString()};excerptInput=null
        body.removeAllViews();body.addView(ui.text("AI로 쉽게 설명",28,true))
        body.addView(ui.text(if(issueMode)"선택한 증상 한 건의 가능한 원인과 확인할 일을 설명해요. 확정 진단은 아니에요."else"차량에서 받은 충전량 기록만 설명해요. 고장 진단이나 운행 안전 판단은 하지 않아요.",22))
        if(issueMode&&issue!=null){
            body.addView(ui.text("전송할 증상 · ${issue!!.type.label}",24,true))
            val pendingText=runCatching{JSONObject(jobState().getJSONObject("pending").getString("payload")).getString("situation")}.getOrNull()
            val field=EditText(this).apply{tag="issue_ai_excerpt";setText(pendingText?:excerpt?:issue!!.situation);textSize=22f;minLines=2;setTextColor(ui.colors.text);isSaveEnabled=false;isEnabled=allowed()&&!busy&&pendingText==null;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE}
            excerptInput=field;body.addView(field)
            body.addView(ui.text("개인 이름·연락처는 빼 주세요. 여기서 고쳐도 원래 기록은 바뀌지 않아요. 메모·작성자·다른 기록·차량값은 보내지 않아요.",20))
        }
        if(!allowed())body.addView(ui.state("안전하게 정차한 뒤 이용해 주세요.","입력 허용 상태가 확인돼야 로그인·AI 요청을 할 수 있어요."))
        status.text=message
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
                message="로그인하고 있어요.";FirebaseSessionProvider.signIn(e,p){r->if(visible&&run==generation){message=if(r.signedIn)"로그인됐어요. 처음에는 전송 안내를 확인해 주세요."else PortableFailure(r.code).displayMessage();render()}}
            });return
        }
        val a=analysis
        if(!issueMode&&a?.latest!=null)body.addView(ui.metric("설명할 충전량",VehicleAnalysis.fmt(a.latest!!.number)+" %","최근 수신 기록 · ${a.points.size}건","Pleos 에뮬레이터에서 받은 값이에요.","portable_soc",HmiIcon.BATTERY))
        if(!consentReady()){
            autoConsumed=true // First-use login/registration never silently calls the model.
            body.addView(ui.state("처음 한 번 전송에 동의해 주세요.",if(issueMode)"선택한 한 건의 증상 종류와 위 본문을 Cloudflare 중계와 Google Gemini로 보내요. 작성자·메모·시간·차량값·다른 기록은 보내지 않아요. 개인 이름 등이 본문에 있으면 먼저 빼 주세요. 설명은 이 기기에 암호화해 저장하고, 서버 결과는 하루 뒤 삭제해요. AI가 틀릴 수 있어요. 이후 선택한 기록의 AI 버튼을 누르면 같은 범위로 요청하며, 동의는 철회할 수 있어요."else"충전량의 최근값·최소·최대·관측 평균·비교 가능한 변화량, 표본 수·상대 경과시간·품질 요약을 Cloudflare 중계와 Google Gemini로 보내요. 이름·VIN·위치·증상 원문·원본 DB는 보내지 않아요. 로그인 계정과 기기 서명은 중계 인증에만 사용해요. 다음 요청부터 같은 범위에 동의를 유지하며 언제든 철회할 수 있어요."))
            body.addView(ui.action("동의하고 이 기기 등록","portable_register",ActionStyle.PRIMARY,allowed()&&!busy&&ready(),HmiIcon.CHECK){sessionTask {api->api.register();"AI 이용 동의와 기기 등록을 저장했어요."}})
            body.addView(ui.action("동의하지 않고 돌아가기","portable_consent_cancel",ActionStyle.SECONDARY,!busy,HmiIcon.BACK){finish()})
        }else{
            body.addView(ui.text("AI 이용 동의가 유지돼요. 같은 범위의 설명에는 로그인·체크박스를 반복하지 않아요.",20))
            body.addView(ui.action(if(jobState().has("pending"))"이전 요청 결과 확인"else"AI로 쉽게 설명","portable_explain",ActionStyle.PRIMARY,allowed()&&!busy&&ready(),HmiIcon.RECORD){requestExplanation()})
            body.addView(ui.action("AI 이용 동의 철회","portable_withdraw",ActionStyle.SECONDARY,allowed()&&!busy,HmiIcon.BACK){
                sessionTask {api->
                    val revoked=runCatching{api.revoke()}.isSuccess
                    api.withdrawLocal()
                    if(revoked)"동의를 철회했어요. 서버의 이 기기 동의와 로컬 AI 결과를 지웠어요. 이미 전송된 요청은 되돌릴 수 없어요."
                    else "이 앱의 동의를 철회했어요. 서버에는 연결하지 못했어요. 관리자에게 기기 동의 해제를 요청해 주세요. 이미 전송된 요청은 되돌릴 수 없어요."
                };resultText=""
            })
        }
        body.addView(ui.action("전송 요약 확인","portable_preview",ActionStyle.QUIET,allowed()&&!busy&&ready(),HmiIcon.RECORD){previewText=runCatching{if(issueMode)IssueExplanation.payload(issue!!,excerptInput?.text?.toString()?:excerpt?:issue!!.situation)else PortableAnalysis.payload(a!!)}.getOrElse{(it as? PortableFailure)?.displayMessage()?:"최근 수신값을 확인해 주세요."};render()})
        if(previewText.isNotBlank())body.addView(ui.state("전송할 내용 · 원래 기록은 바뀌지 않아요",previewText).apply{tag="portable_payload_preview"})
        if(consentReady())body.addView(ui.action("AI 이용 가능 횟수 확인 · 모델 호출 없음","portable_status_check",ActionStyle.QUIET,allowed()&&!busy,HmiIcon.CHECK){sessionTask {api->val s=api.status();"기존 TEST 사용 ${s.getInt("legacy_total")}회 · 정식 요청 시도 ${s.getInt("portable_attempts")}회\n누적 승인 상한 ${s.getInt("global_total")}회 · 현재 승인 잔여 ${s.getInt("remaining")}회\n모델 ${if(s.getBoolean("model_enabled"))"켜짐"else"꺼짐"} · 불확실한 이전 요청 ${if(s.optBoolean("uncertain_block"))"있음"else"없음"}\n모델 호출 없이 확인했어요."}})
        if(resultText.isNotBlank())body.addView(ui.state("Gemini가 작성한 설명",resultText).apply{tag="portable_model_result"})
        val cached=runCatching{jobState().optJSONObject("last")}.getOrNull()
        if(resultText.isBlank()&&cached!=null){val answer=cached.getJSONObject("answer");val text=runCatching{if(issueMode)IssueExplanation.validate(answer,cached.getString("payload"),answer.getString("operation_id"))else PortableAnalysis.validate(answer,cached.getString("payload"),answer.getString("operation_id"))}.getOrNull();if(text!=null)body.addView(ui.state("이전에 받은 AI 설명 · 현재 상태 판정 아님",text).apply{tag="portable_cached_result"})}
        body.addView(ui.action("로그아웃","portable_logout",ActionStyle.QUIET,allowed()&&!busy,HmiIcon.BACK){cancelWork();FirebaseSessionProvider.subject()?.let{PortableAiStore(this,storeName(it)).withdraw()};FirebaseSessionProvider.signOut();resultText="";message="로그아웃했어요. 차량 기록은 그대로예요.";render()})
        if(!autoConsumed&&(issueMode||intent.getBooleanExtra("request_from_analysis",false))&&allowed()&&!busy&&ready()&&consentReady()){
            autoConsumed=true;body.post{if(visible&&allowed()&&!busy)requestExplanation()}
        }
    }
    private fun requestExplanation(){
        if(!allowed()||busy||!consentReady())return
        excerptInput?.let{excerpt=it.text.toString()}
        sessionTask(work={api->
            val selected=issue
            val payload=if(issueMode){
                val current=selectedRecord()?:throw PortableFailure("ISSUE_UNAVAILABLE")
                if(selected==null||IssueExplanation.jobKey(selected)!=IssueExplanation.jobKey(current))throw PortableFailure("ISSUE_CHANGED")
                if(api.hasPending())""else IssueExplanation.payload(current,excerpt?:current.situation)
            }else if(api.hasPending())""else PortableAnalysis.payload(analysis?:throw PortableFailure("NO_VALID_SOC"))
            val answer=api.explain(payload)
            if(issueMode)return@sessionTask IssueExplanation.validate(answer,api.jobState().getJSONObject("last").getString("payload"),answer.getString("operation_id"))
            val explanation=answer.getString("explanation")
            val source=answer.getJSONObject("facts").getJSONObject("facts")
            "최근 충전량 ${VehicleAnalysis.fmt(source.getDouble("latest"))}%\n$explanation\n차량의 실제 표시와 함께 확인해 주세요.\n모델 · ${answer.getString("model")}"
        },modelResult=true)
    }
    private fun sessionTask(modelResult:Boolean=false,work:(PortableAiClient)->String){
        if(!allowed()||busy)return
        busy=true;val run=generation;message="요청을 확인하고 있어요. 중복 전송하지 않아요.";render()
        val tokenDeadline=Runnable {if(visible&&run==generation&&busy){cancelWork();message=PortableFailure("AUTH_TOKEN_TIMEOUT").displayMessage();render()}}
        val feedbackHandler=android.os.Handler(mainLooper);feedbackHandler.postDelayed(tokenDeadline,15000)
        FirebaseSessionProvider.withSession {uid,token,error->
            feedbackHandler.removeCallbacks(tokenDeadline)
            if(!visible||run!=generation||!allowed())return@withSession
            if(error!=null||uid==null||token==null){busy=false;message=PortableFailure(error?:"AUTH_SIGN_IN_REQUIRED").displayMessage();render();return@withSession}
            val s=PortableAiStore(this,storeName(VehicleAnalytics.id(uid)));activeStore=s
            val api=PortableAiClient(s,DeviceKeyStore("caritda.portable.${packageName}"),uid,token,allowed={visible&&run==generation&&gate.state==EditingState.ALLOWED&&FirebaseSessionProvider.matches(uid)},issueJob=if(issueMode)issueJob()?:throw PortableFailure("ISSUE_UNAVAILABLE")else null);client=api
            worker.execute {val result=runCatching{work(api)};runOnUiThread {
                if(visible&&run==generation){busy=false;client=null;activeStore=null;result.onSuccess{if(modelResult){resultText=it;message="AI 설명을 받았어요. 아래에서 확인해 주세요."}else message=it}.onFailure{message=((it as? PortableFailure)?:PortableFailure("AI_REQUEST_FAILED")).displayMessage()};render()}
            }}
        }
    }
    private fun loadAnalysis(){val run=++loadGeneration;worker.execute {
        if(issueMode){val selected=runCatching{selectedRecord()}.getOrNull();runOnUiThread{if(visible&&run==loadGeneration){issue=selected;message=if(selected==null)"기록을 찾을 수 없어요. 이전 화면에서 다시 선택해 주세요."else"선택한 증상만 설명해요. 원래 기록은 바뀌지 않아요.";render()}};return@execute}
        val r=runCatching{val now=System.currentTimeMillis();ObservationStore(this).use{val refs=it.analysisRefs("EV_SOC",now-86400000);VehicleAnalytics.analyze("EV_SOC",refs.takeLast(2000),now-86400000,now,truncated=refs.size>2000)}}
        runOnUiThread{if(visible&&run==loadGeneration){analysis=r.getOrNull();message=if(analysis?.latest!=null)"설명할 최근 충전량이 있어요."else"차량 정보 화면에서 최근 값을 먼저 불러와 주세요.";render()}}
    }}
    override fun onStart(){super.onStart();visible=true;loadedSubject=FirebaseSessionProvider.subject();stopObserving=FirebaseSessionProvider.observe {val next=FirebaseSessionProvider.subject();if(next!=loadedSubject){cancelWork();resultText="";loadedSubject=next;message=if(next==null)"계정으로 로그인해 주세요."else"로그인됐어요. AI 이용 안내를 확인해 주세요."};if(visible)render()};gate.start();loadAnalysis()}
    override fun onStop(){visible=false;loadGeneration++;cancelWork();resultText="";if(::password.isInitialized)password.text.clear();if(::email.isInitialized)email.text.clear();stopObserving?.invoke();stopObserving=null;gate.stop();super.onStop()}
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("auto_consumed",autoConsumed);out.putString("issue_excerpt",excerptInput?.text?.toString()?:excerpt);super.onSaveInstanceState(out)}
}
