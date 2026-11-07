package com.nextdrive.pleos

import android.content.Context
import com.nextdrive.pleos.data.DriverMigration

/** This guard also refuses an accidentally built runner targeting the production application. */
object ValidationEnvironment {
    fun requireIsolated(context:Context) {
        check(context.packageName=="com.nextdrive.pleos.validation") { "Refusing tests against the user's application" }
    }
    fun prepare(context:Context) {
        requireIsolated(context)
        // Only our independently installed validation app's disposable fixtures are reset.
        context.deleteDatabase("caritda_v01.db")
        context.deleteDatabase("caritda-observations.db")
        context.openOrCreateDatabase("caritda_v01.db",0,null).use { DriverMigration.createV1(it);it.version=1 }
        check(context.getSharedPreferences("caritda_help",Context.MODE_PRIVATE).edit().putInt("version",1).putString("outcome","existing").commit())
    }
}
