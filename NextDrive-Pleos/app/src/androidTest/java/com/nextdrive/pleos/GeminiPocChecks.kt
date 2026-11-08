package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import com.nextdrive.pleos.ai.*
import com.nextdrive.pleos.analytics.VehicleAnalytics
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** No provider calls: real JS security handler + mocked fetch/storage, native UI without Send. */
class GeminiPocChecks(private val inst:Instrumentation){
    private val c=inst.targetContext
    private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,s:String){check(ok){s};lines.add("PASS $s")}
    private fun <T> main(f:()->T):T{var v:T?=null;var error:Throwable?=null;inst.runOnMainSync{try{v=f()}catch(t:Throwable){error=t}};error?.let{throw it};@Suppress("UNCHECKED_CAST")return v as T}
    private fun tag(a:GeminiPocActivity,k:String):View?=a.window.decorView.findViewWithTag(k)
    fun run():List<String>{ValidationEnvironment.requireIsolated(c)
        val a=inst.startActivitySync(Intent().setClassName(c,"com.nextdrive.pleos.GeminiPocActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GeminiPocActivity
        val end=System.currentTimeMillis()+20000
        while(System.currentTimeMillis()<end&&!main{(tag(a,"gemini_token") as? EditText)?.isEnabled==true})Thread.sleep(100)
        verify(main{tag(a,"gemini_synthetic")!=null&&(tag(a,"gemini_state") as TextView).text.contains("아직 실제 모델")},"Gemini native page explicitly synthetic and no automatic model request")
        verify(main{a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0},"Gemini token screen blocks screenshots")
        verify(main{val v=tag(a,"gemini_token") as EditText;!v.isSaveEnabled&&v.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD!=0},"Gemini token is masked and excluded from state saving")
        main{(tag(a,"gemini_token") as EditText).setText("d".repeat(43));(tag(a,"gemini_consent") as CheckBox).isChecked=true}
        verify(main{!(tag(a,"gemini_send") as Button).isEnabled},"Gemini consent alone cannot send before deployment approval")
        main{(tag(a,"gemini_cancel") as Button).performClick()}
        verify(main{(tag(a,"gemini_token") as EditText).text.isEmpty()&&!(tag(a,"gemini_consent") as CheckBox).isChecked},"Gemini cancel clears temporary token and consent without HTTP")
        val payload=SyntheticSocFixture.payload();val p=JSONObject(payload)
        verify(p.getString("source")=="TEST_ONLY"&&p.getBoolean("synthetic")&&p.getInt("latest")==18&&p.getInt("delta")==-42,"Gemini synthetic fixture reuses analysis facts without claiming SDK receipt")
        val consent=AnalysisConsent(payload).also{it.accept()}
        val local=JSONObject().put("origin","MODEL").put("evidence_digest",VehicleAnalytics.id(payload)).put("facts",p).put("explanation","설정한 충전량 기준을 확인해 주세요.").put("action","CHECK_CHARGING_PLAN")
        verify(AnalysisExplanation.validate(local.toString(),consent,payload).isNotBlank(),"Gemini contract validates mocked grounded facts, not an actual model response")
        local.getJSONObject("facts").put("latest",19)
        verify(runCatching{AnalysisExplanation.validate(local.toString(),consent,payload)}.isFailure,"Gemini altered fixture facts rejected")
        val done=CountDownLatch(1);var jsResult="";var browser:WebView?=null
        val source=c.getFileStreamPath("gemini-worker.ts").readText().replace("export const ","const ").replace("export class ","class ").replace("export async function ","async function ")
            .replace("export default {fetch(request,env) { return handle(request,env); }};","")
        check(!source.contains("export "))
        val harness=c.getFileStreamPath("gemini-worker-security.js").readText()
        main{
            browser=WebView(a).apply{
                settings.javaScriptEnabled=true
                webViewClient=object:WebViewClient(){
                    override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest?):WebResourceResponse=WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
                    override fun onPageFinished(view:WebView,url:String){
                        val code="(async()=>{try{"+source+"\n"+harness+"\nwindow.__workerResult=JSON.stringify(await runWorkerChecks(handle,BudgetGate));}catch(e){window.__workerResult=JSON.stringify({error:String(e)});}})()"
                        view.evaluateJavascript(code,null)
                    }
                }
                loadDataWithBaseURL("https://caritda-local-test.invalid/","<html><body>Offline worker security harness</body></html>","text/html","UTF-8",null)
            }
            a.addContentView(browser,android.view.ViewGroup.LayoutParams(1,1))
        }
        val until=System.currentTimeMillis()+55000
        while(System.currentTimeMillis()<until&&done.count>0){
            val poll=CountDownLatch(1)
            main{browser!!.evaluateJavascript("window.__workerResult || null"){value->if(value!="null"){jsResult=value;done.countDown()};poll.countDown()}}
            check(poll.await(8,TimeUnit.SECONDS)){"WebView callback unavailable"};if(done.count>0)Thread.sleep(150)
        }
        main{browser?.destroy();a.finish()}
        check(done.count==0L){"Worker Web API harness timed out"}
        val decoded=JSONArray("[$jsResult]").getString(0)
        check(!decoded.startsWith("{\"error\"")){decoded}
        val checks=JSONArray(decoded)
        repeat(checks.length()){lines.add(checks.getString(it)+" [local Web API / mocked provider+storage]")}
        return lines
    }
}
