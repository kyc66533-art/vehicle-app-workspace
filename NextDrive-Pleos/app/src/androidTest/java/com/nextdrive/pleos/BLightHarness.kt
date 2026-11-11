package com.nextdrive.pleos

import android.app.*
import android.graphics.*
import android.view.*
import android.widget.*
import java.io.File

class BLightHarness(val inst:Instrumentation){
    init{ValidationEnvironment.requireIsolated(inst.targetContext)}
    fun <T> main(block:()->T):T{if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())return block();var value:T?=null;var failed:Throwable?=null;inst.runOnMainSync{try{value=block()}catch(t:Throwable){failed=t}};failed?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
    fun await(block:()->Boolean){val end=System.currentTimeMillis()+18000;while(System.currentTimeMillis()<end){if(main(block))return;Thread.sleep(40)};error("B_LIGHT_CONDITION_TIMEOUT")}
    fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    fun text(v:View)=views(v).filterIsInstance<TextView>().joinToString("\n"){it.text.toString()}
    fun click(a:Activity,tag:String){if(BLightTestNavigation.prepare(inst,a,tag))return;await{a.window.decorView.findViewWithTag<Button>(tag)?.isEnabled==true};main{check(a.window.decorView.findViewWithTag<Button>(tag).performClick())}}
    fun set(a:Any,name:String,value:Any?){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    fun get(a:Any,name:String):Any?=a.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(a)
    fun draw(a:MainActivity)=main{a.javaClass.getDeclaredMethod("draw").apply{isAccessible=true}.invoke(a)}
    fun render(a:PortableAiActivity)=main{a.javaClass.getDeclaredMethod("render").apply{isAccessible=true}.invoke(a)}
    fun confirm(a:MainActivity,yes:Boolean=true){val dialog=main{get(a,"activeDialog") as AlertDialog};await{dialog.isShowing};main{dialog.getButton(if(yes)AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick()}}
    fun capture(a:Activity,name:String,synthetic:Boolean=true){
        check(a.packageName=="com.nextdrive.pleos.validation"&&Regex("gleo-ui-[a-f0-9-]{36}").matches(a.intent.getStringExtra("validation_fixture").orEmpty()))
        check(Regex("[a-z0-9-]+\\.png").matches(name))
        main{val root=get(a,if(a is MainActivity)"root"else"shell") as View
            views(root).filterIsInstance<TextView>().firstOrNull{it.text.startsWith("검증 전용") }?.text=if(synthetic)"검증 전용 · TEST · 합성 예시"else"검증 전용 · TEST · 실제 에뮬레이터 SDK"
        };inst.waitForIdleSync()
        main{val root=get(a,if(a is MainActivity)"root"else"shell") as View;check(root.width>0&&root.height>0)
            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
            val dir=File(a.filesDir,"b-light-captures").apply{mkdirs()};File(dir,name).outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        }
    }
}
