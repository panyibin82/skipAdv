package com.skipadv

import android.app.Application
import com.skipadv.rule.GlobalRule

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        GlobalRule.load(this)
    }
}
