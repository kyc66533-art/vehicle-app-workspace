package com.nextdrive.pleos.ui

import android.content.SharedPreferences

/** Help preference only: never confirms a driver, reads a vehicle, or acknowledges a record. */
class GuideState(private val preferences: SharedPreferences) {
    fun prepare(existingInstallation: Boolean): Boolean {
        if (!preferences.contains("version")) {
            check(preferences.edit().putInt("version",1)
                .putString("outcome",if(existingInstallation)"existing" else "pending").commit())
        }
        return preferences.getString("outcome",null)=="pending"
    }
    fun finish(skipped: Boolean, inputAllowed: Boolean) {
        check(inputAllowed) { "화면 입력이 가능한 뒤에 안내를 닫아 주세요." }
        check(preferences.edit().putInt("version",1).putString("outcome",if(skipped)"skipped" else "completed").commit())
    }
}
