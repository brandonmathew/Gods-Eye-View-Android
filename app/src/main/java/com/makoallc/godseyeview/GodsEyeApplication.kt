package com.makoallc.godseyeview

import android.app.Application
import com.makoallc.godseyeview.crash.CrashHandler

class GodsEyeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
