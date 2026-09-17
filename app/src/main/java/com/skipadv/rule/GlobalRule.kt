package com.skipadv.rule

import android.content.Context
import android.util.Log

/**
 * The global fallback rule: fires on ANY app's foreground window (no package
 * filter), clicking common skip/close controls. Guarded by:
 *  - a user toggle (default off, persisted)
 *  - [matchWindowMs]: only effective within N seconds after the foreground app
 *    changes to a *different* package (splash-ad window), so idle in-app popups
 *    are not clicked blindly.
 */
object GlobalRule {
    private const val TAG = "CustomRuleStore"
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "global_rule_enabled"
    private const val KEY_WINDOW = "global_rule_window_ms"

    /** Skip/close matching only happens within this window after app switch. */
    const val DEFAULT_WINDOW_MS = 10_000L

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var windowMs: Long = DEFAULT_WINDOW_MS
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        windowMs = prefs.getLong(KEY_WINDOW, DEFAULT_WINDOW_MS)
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
        enabled = value
        Log.i(TAG, "global rule ${if (value) "enabled" else "disabled"}")
    }

    /**
     * Selectors tried in order against the current window. Raw GKD-style:
     *  - any visible text containing 跳过 (skip buttons show countdown like "跳过 3")
     *  - clickable nodes whose id ends with close/skip
     *  - clickable nodes described as 关闭
     */
    val selectors: List<String> = listOf(
        "@[text*=\"跳过\"]",
        "@[id*=\"close\"][clickable=true]",
        "@[id*=\"skip\"][clickable=true]",
        "@[desc*=\"关闭\"][clickable=true]",
    )
}
