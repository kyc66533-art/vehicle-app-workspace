package com.nextdrive.pleos

import android.app.Activity
import android.app.Instrumentation
import android.view.View

/** Follow real menu/optional controls after progressive disclosure; no hidden control or DB bypass. */
object ValidationUiActions {
    fun reveal(inst:Instrumentation,a:Activity,key:String){
        ValidationEnvironment.requireIsolated(inst.targetContext)
        // The B light UI has no hidden menu/nav_* Views: routes follow the visible buttons (BLightTestNavigation).
        if(key !in setOf("memo","select_snapshot","discovered_at"))BLightTestNavigation.prepare(inst,a,key)
        inst.runOnMainSync {
            if(a.window.decorView.findViewWithTag<View>(key)!=null)return@runOnMainSync
            val opener=when(key){
                "memo","select_snapshot","discovered_at"->"record_extras"
                else->null
            }
            opener?.let{
                val v=a.window.decorView.findViewWithTag<View>(it)?:error("Missing opener $it for $key")
                check(v.isEnabled&&v.visibility==View.VISIBLE);check(v.performClick())
            }
        }
        inst.waitForIdleSync()
    }
}
