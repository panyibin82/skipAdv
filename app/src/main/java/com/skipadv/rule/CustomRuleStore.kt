package com.skipadv.rule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-defined rules persisted in app-private storage (survives reboot/reinstall-upgrade).
 *
 * A custom rule is a simplified [GroupRule] targeting one app package with a text
 * keyword match — the common "click the button containing X" case. Advanced users
 * can still enter a raw GKD-style selector instead of a plain keyword.
 */
object CustomRuleStore {
    private const val PREFS = "custom_rules"
    private const val KEY = "rules_json"

    data class CustomRule(
        val id: Long,
        val appId: String,
        val appName: String,
        /** Plain keyword (matched via text contains) or raw selector when [isRaw]. */
        val pattern: String,
        val action: String,
        val enabled: Boolean,
        val isRaw: Boolean = false,
    )

    fun load(context: Context): MutableList<CustomRule> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                CustomRule(
                    id = o.getLong("id"),
                    appId = o.getString("appId"),
                    appName = o.optString("appName", o.getString("appId")),
                    pattern = o.getString("pattern"),
                    action = o.optString("action", "click"),
                    enabled = o.optBoolean("enabled", true),
                    isRaw = o.optBoolean("isRaw", false),
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, rules: List<CustomRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("appId", r.appId)
                    put("appName", r.appName)
                    put("pattern", r.pattern)
                    put("action", r.action)
                    put("enabled", r.enabled)
                    put("isRaw", r.isRaw)
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** Converts a custom rule into the selector + group key used by the matcher. */
    fun toGroupRule(rule: CustomRule): Pair<Int, GroupRule> {
        val key = (rule.id % Int.MAX_VALUE).toInt()
        val selector = if (rule.isRaw) {
            rule.pattern
        } else {
            "@[text*=\"${rule.pattern}\"]"
        }
        val gr = GroupRule(
            key = key,
            name = "自定义:${rule.pattern}",
            matches = selector,
            action = rule.action,
        )
        return key to gr
    }
}
