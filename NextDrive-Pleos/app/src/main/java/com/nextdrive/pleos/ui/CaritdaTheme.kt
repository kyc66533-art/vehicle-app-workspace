package com.nextdrive.pleos.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

/** Light is the default; an explicit existing preference remains the user's choice. */
object CaritdaTheme {
    fun preferences(context:Context,fixture:String?=null):SharedPreferences =
        context.getSharedPreferences(fixture?.let{it+"-ui"}?:"MainActivity",Context.MODE_PRIVATE)
    fun dark(context:Context,fixture:String?=null):Boolean=when(preferences(context,fixture).getString("theme","light")){
        "dark"->true;"light"->false
        else->context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
    }
}
