package com.skipadv.rule

import android.content.Context
import org.json.JSONArray

/**
 * Loads and indexes the built-in rules from `assets/default_rules.json`.
 *
 * JSON layout:
 *   [
 *     { "appId": "com.pkg.app",
 *       "groups": [
 *         { "key": 1, "name": "...", "matches": "[text^=\"跳过\"]", "action": "click", "preKeys": [] }
 *       ] }
 *   ]
 *
 * Safe by construction: rules only ever fire for apps whose package name is present here.
 */
object RuleRepository {
    private var appRules: Map<String, AppRule> = emptyMap()

    fun load(context: Context) {
        val raw = context.assets.open("default_rules.json")
            .bufferedReader().use { it.readText() }
        val root = JSONArray(raw)
        val map = LinkedHashMap<String, AppRule>(root.length())
        for (i in 0 until root.length()) {
            val obj = root.getJSONObject(i)
            val appId = obj.getString("appId")
            val groupsArr = obj.optJSONArray("groups") ?: JSONArray()
            val groups = (0 until groupsArr.length()).mapNotNull { j ->
                val g = groupsArr.getJSONObject(j)
                if (!g.has("matches")) return@mapNotNull null
                GroupRule(
                    key = g.optInt("key", j),
                    name = g.optString("name", ""),
                    matches = g.getString("matches"),
                    action = g.optString("action", "click"),
                    preKeys = g.optJSONArray("preKeys")?.let { a -> List(a.length()) { a.getInt(it) } }
                        ?: emptyList(),
                )
            }
            map[appId] = AppRule(appId, groups)
        }
        appRules = map
    }

    fun groupsFor(packageName: String): List<GroupRule>? = appRules[packageName]?.groups

    fun allApps(): List<AppRule> = appRules.values.toList()
}