package com.nextdrive.pleos

import android.app.Activity
import android.app.Instrumentation
import android.widget.Button

/** Regression route mapping uses real B buttons. No hidden production compatibility Views. */
object BLightTestNavigation {
    fun prepare(inst:Instrumentation,a:Activity,key:String):Boolean {
        ValidationEnvironment.requireIsolated(inst.targetContext)
        fun <T> main(block:()->T):T{var value:T?=null;inst.runOnMainSync{value=block()};@Suppress("UNCHECKED_CAST")return value as T}
        fun has(tag:String)=main{a.window.decorView.findViewWithTag<Button>(tag)!=null}
        fun tap(tag:String){val end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){
            val ready=main{a.window.decorView.findViewWithTag<Button>(tag)?.isEnabled==true}
            if(ready){main{check(a.window.decorView.findViewWithTag<Button>(tag).performClick())};return};Thread.sleep(40)
        };error("B_VISIBLE_ROUTE_MISSING $tag")}
        if(has(key))return false
        fun home(){if(has("nav_HOME"))tap("nav_HOME")}
        when(key){
            "nav_HOME"->return has("settings_help")
            "nav_VEHICLE"->{home();tap("vehicle_health");return true}
            "nav_ISSUES"->{home();tap("all_issues");return true}
            "menu"->{home();tap("settings_help");return true}
            "change_driver"->{home();tap("change_driver");return true}
            "manage_drivers"->{home();tap("settings_help");tap("manage_drivers");return true}
            "add_driver"->{home();tap("settings_help");tap("manage_drivers");tap("add_driver");return true}
            "auto_snapshots"->{home();tap("vehicle_health");tap("auto_snapshots");return true}
            "my_issues"->{home();tap("all_issues");tap("filter_MINE");return true}
            "help"->{home();tap("settings_help");tap("help");return true}
            "close_help","help_contents","close_menu"->{tap("back");return true}
            "issue_summary","recurrence","change_status","speak_issue","add_note"->if(has("issue_actions"))tap("issue_actions")
            "edit_issue","delete_issue"->if(has("issue_manage"))tap("issue_manage")
            "location_history","location_history_more"->if(has("location_details"))tap("location_details")
            "portable_status_check","portable_withdraw","portable_logout"->if(has("portable_account_details"))tap("portable_account_details")
            "all_summary"->if(has("records_summary"))tap("records_summary")
            "load_details"->{if(!has("health_technical"))tap("health_expand");tap("health_technical")}
            else->if(key.startsWith("help_topic_")&&has("help_topics"))tap("help_topics")
        }
        return false
    }
}
