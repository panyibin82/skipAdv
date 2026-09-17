package com.skipadv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.skipadv.rule.CustomRuleStore
import com.skipadv.service.AdSkipAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One installed launchable app, for the rule-creation picker. */
data class AppEntry(val packageName: String, val name: String)

/** UI model for one rule row. */
data class RuleUi(
    val appId: String,
    val key: Int,
    val name: String,
    val matches: String,
    val action: String,
    val enabled: Boolean,
    val isCustom: Boolean = false,
    val customStoreId: Long = -1L,
) {
    val id: String get() = "$appId#$key"
}

class RulesViewModel(app: Application) : AndroidViewModel(app) {
    private val _rules = MutableStateFlow<List<RuleUi>>(emptyList())
    val rules: StateFlow<List<RuleUi>> = _rules.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    init {
        reload()
        loadInstalledApps()
    }

    fun reload() {
        val context = getApplication<Application>()
        // No built-in rules: only user-defined custom rules are used.
        val custom = CustomRuleStore.load(context).map { r ->
            val (_, group) = CustomRuleStore.toGroupRule(r)
            RuleUi(
                appId = r.appId,
                key = group.key,
                name = r.appName.ifEmpty { r.appId },
                matches = r.pattern,
                action = r.action,
                enabled = r.enabled,
                isCustom = true,
                customStoreId = r.id,
            )
        }
        _rules.value = custom
    }

    /** Lists installed launchable apps (sorted, for the rule-creation picker). */
    fun loadInstalledApps() {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val entries = pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.name }
            .toList()
        _apps.value = entries
    }

    /** Adds a keyword rule: click the node whose text contains [keyword]. */
    fun addKeywordRule(appId: String, appName: String, keyword: String, action: String): Boolean {
        val kw = keyword.trim()
        if (kw.isEmpty()) return false
        val context = getApplication<Application>()
        val list = CustomRuleStore.load(context)
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1L
        list.add(
            CustomRuleStore.CustomRule(
                id = id, appId = appId, appName = appName,
                pattern = kw, action = action, enabled = true,
            ),
        )
        CustomRuleStore.save(context, list)
        reload()
        return true
    }

    /** Adds a raw GKD-style selector rule. Returns false if the selector can't parse. */
    fun addRawRule(appId: String, appName: String, selector: String, action: String): Boolean {
        val raw = selector.trim()
        if (raw.isEmpty()) return false
        return try {
            com.skipadv.rule.Selector.parse(raw)
            val context = getApplication<Application>()
            val list = CustomRuleStore.load(context)
            val id = (list.maxOfOrNull { it.id } ?: 0L) + 1L
            list.add(
                CustomRuleStore.CustomRule(
                    id = id, appId = appId, appName = appName,
                    pattern = raw, action = action, enabled = true, isRaw = true,
                ),
            )
            CustomRuleStore.save(context, list)
            reload()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Batch mode: applies one keyword rule to every installed app at once.
     * Skips apps that already have an identical keyword rule. Returns how many
     * apps got a new rule.
     */
    fun addKeywordRuleToAll(keyword: String, action: String): Int {
        val kw = keyword.trim()
        if (kw.isEmpty()) return 0
        val context = getApplication<Application>()
        if (_apps.value.isEmpty()) loadInstalledApps()
        val existing = CustomRuleStore.load(context)
        var nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1L
        val seen = existing.filter { !it.isRaw }.mapTo(HashSet()) { "${it.appId}|${it.pattern}" }
        var added = 0
        for (app in _apps.value) {
            val dedupKey = "${app.packageName}|$kw"
            if (dedupKey in seen) continue
            seen.add(dedupKey)
            existing.add(
                CustomRuleStore.CustomRule(
                    id = nextId++, appId = app.packageName, appName = app.name,
                    pattern = kw, action = action, enabled = true,
                ),
            )
            added++
        }
        if (added > 0) {
            CustomRuleStore.save(context, existing)
            reload()
        }
        return added
    }

    /** Removes every custom rule matching this keyword (used to undo a batch add). */
    fun removeKeywordRuleFromAll(keyword: String): Int {
        val kw = keyword.trim()
        if (kw.isEmpty()) return 0
        val context = getApplication<Application>()
        val before = CustomRuleStore.load(context)
        val after = before.filterNot { !it.isRaw && it.pattern == kw }
        val removed = before.size - after.size
        if (removed > 0) {
            CustomRuleStore.save(context, after)
            reload()
        }
        return removed
    }

    /** Count of custom keyword rules with this exact keyword. */
    fun countKeywordRules(keyword: String): Int {
        val kw = keyword.trim()
        if (kw.isEmpty()) return 0
        return CustomRuleStore.load(getApplication()).count { !it.isRaw && it.pattern == kw }
    }

    fun deleteCustomRule(storeId: Long) {
        val context = getApplication<Application>()
        val list = CustomRuleStore.load(context).filter { it.id != storeId }
        CustomRuleStore.save(context, list)
        reload()
    }

    /** Deletes every user-defined rule. Returns how many were removed. */
    fun deleteAllRules(): Int {
        val context = getApplication<Application>()
        val removed = CustomRuleStore.load(context).size
        CustomRuleStore.save(context, emptyList())
        reload()
        return removed
    }

    fun toggle(id: String) {
        val rule = _rules.value.firstOrNull { it.id == id } ?: return
        if (rule.isCustom) {
            val context = getApplication<Application>()
            val list = CustomRuleStore.load(context).map {
                if (it.id == rule.customStoreId) it.copy(enabled = !it.enabled) else it
            }
            CustomRuleStore.save(context, list)
            reload()
        } else {
            _rules.value = _rules.value.map {
                if (it.id == id) it.copy(enabled = !it.enabled) else it
            }
        }
        val newState = _rules.value.firstOrNull { it.id == id }?.enabled ?: return
        AdSkipAccessibilityService.instance?.setRuleEnabled(id, newState)
    }
}
