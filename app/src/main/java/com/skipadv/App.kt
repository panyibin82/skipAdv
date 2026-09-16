package com.skipadv

import android.app.Application
import com.skipadv.rule.RuleRepository

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RuleRepository.load(this)
    }
}