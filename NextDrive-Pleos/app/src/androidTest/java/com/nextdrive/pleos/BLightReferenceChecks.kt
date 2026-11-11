package com.nextdrive.pleos

import android.app.*
import android.content.Intent
import android.graphics.*
import android.webkit.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Offline, hash-verified B prototype reference only. This is never an app UI implementation. */
class BLightReferenceChecks(private val inst:Instrumentation){
    private val h=BLightHarness(inst)
    fun run():List<String>{
        val c=inst.targetContext;ValidationEnvironment.requireIsolated(c)
        val sha="527ad37bd9b9482a183277b2f64ff7c48e90b52c"
        val bytes=File(c.filesDir,"b-light-reference-$sha.html").readBytes()
        val actual=MessageDigest.getInstance("SHA-1").run{update("blob ${bytes.size}\u0000".toByteArray());digest(bytes).joinToString(""){"%02x".format(it)}}
        check(actual==sha){"REFERENCE_BLOB_MISMATCH"}
        val a=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture","gleo-ui-"+UUID.randomUUID()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var dialog:Dialog?=null;var view:WebView?=null;val loaded=CountDownLatch(1)
        try{
            h.main{
                val web=WebView(a);view=web
                web.settings.apply{javaScriptEnabled=true;blockNetworkLoads=true;allowFileAccess=false;allowContentAccess=false;domStorageEnabled=false;setSupportZoom(false);textZoom=100;useWideViewPort=true;loadWithOverviewMode=false}
                web.setInitialScale(100)
                web.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String){loaded.countDown()}}
                dialog=Dialog(a).apply{setContentView(web);show();window!!.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window!!.decorView.setPadding(0,0,0,0);window!!.setLayout(1024,720)}
                web.loadDataWithBaseURL("https://caritda-reference.invalid/",bytes.toString(Charsets.UTF_8),"text/html","UTF-8",null)
            }
            check(loaded.await(20,TimeUnit.SECONDS)){"OFFLINE_WEBVIEW_REFERENCE_NOT_LOADED"}
            // Chromium has its own devicePixelRatio on this car image. Match a 1024 CSS-pixel
            // viewport rather than assuming one WebView pixel is one browser CSS pixel.
            val densityReady=CountDownLatch(1);var ratio=1.0
            h.main{view!!.evaluateJavascript("devicePixelRatio"){value->ratio=value.toDouble();densityReady.countDown()}}
            check(densityReady.await(5,TimeUnit.SECONDS)&&ratio in .5..3.0)
            val targetWidth=(1024*ratio).toInt();val targetHeight=(720*ratio).toInt()
            h.main{dialog!!.window!!.setLayout(targetWidth,targetHeight)}
            h.await{view!!.width==targetWidth&&view!!.height==targetHeight}
            val names=listOf("home","health","record","records","detail","ai","handover","settings","help")
            for(name in names){
                val ready=CountDownLatch(1);var success=false
                h.main{view!!.evaluateJavascript("""
                    (function(){
                      let css=document.getElementById('android-reference-only');
                      if(!css){css=document.createElement('style');css.id='android-reference-only';document.head.appendChild(css);}
                      css.textContent='body{background:#f4f7f9}.lab{margin:0;padding:0;max-width:none}.lab>h1,.lab>.note,.tools,.demo-foot{display:none}.stage{margin:0;max-width:none}.app{border:0;border-radius:0}';
                      state.design='b';state.theme='light';state.font='normal';state.size='car';state.page='$name';state.scenario='normal';state.modal=null;state.aiIssue=selected;state.draft='브레이크를 밟으면 끼익 소리가 나요.';render();window.scrollTo(0,0);
                      return document.getElementById('app').className==='app b light';
                    })();
                """.trimIndent()){result->success=result=="true";ready.countDown()}}
                check(ready.await(8,TimeUnit.SECONDS)&&success){"REFERENCE_PAGE_FAILED $name"};inst.waitForIdleSync();Thread.sleep(150)
                h.main{val web=view!!;check(web.width==targetWidth&&web.height==targetHeight){"REFERENCE_SIZE ${web.width}x${web.height}"}
                    val bitmap=Bitmap.createBitmap(web.width,web.height,Bitmap.Config.ARGB_8888);web.draw(Canvas(bitmap))
                    val dir=File(c.filesDir,"b-light-reference-captures").apply{mkdirs()};File(dir,"reference-$name.png").outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
                }
            }
            val metrics=CountDownLatch(1)
            h.main{view!!.evaluateJavascript("JSON.stringify({width:innerWidth,dpr:devicePixelRatio,stage:document.getElementById('stage').getBoundingClientRect().width,font:getComputedStyle(document.getElementById('app')).fontSize})"){value->File(c.filesDir,"b-light-reference-metrics.json").writeText(value);metrics.countDown()}}
            check(metrics.await(5,TimeUnit.SECONDS))
            return listOf("PASS BR01 approved B prototype exact blob verified; Android WebView rendered all nine offline references","PASS BR02 network/file/content access blocked; references remain distinct from native Kotlin captures")
        }finally{h.main{dialog?.dismiss();view?.destroy();a.finish()};inst.waitForIdleSync()}
    }
}
