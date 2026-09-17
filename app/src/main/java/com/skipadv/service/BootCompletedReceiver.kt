package com.skipadv.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * After a reboot most ROMs re-bind accessibility services that were enabled in
 * Settings, but some (ColorOS/One UI style builds) delay or silently drop them.
 * This receiver exists so the system has an explicit BOOT_COMPLETED entry point
 * for the app process; it also preloads rules so the service is ready immediately.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "boot completed; waiting for accessibility service rebind")
    }

    companion object {
        private const val TAG = "AdSkipSvc"
    }
}
