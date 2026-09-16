package com.skipadv.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.skipadv.action.ActionExecutor
import com.skipadv.rule.CustomRuleStore
import com.skipadv.rule.MatchableNode
import com.skipadv.rule.Matcher
import com.skipadv.rule.RuleRepository
import com.skipadv.rule.Selector

/**
 * The accessibility service that implements the "listen -> match -> act" loop.
 *
 *  - listens to window-state / content-changed events of the foreground app
 *  - looks up rules for the reported package, runs the GKD-style selector against the
 *    current window's node tree
 *  - on a match, performs the configured action (click / clickCenter / back)
 *  - enforces a per-rule cooldown so a matched popup isn't clicked repeatedly/rapidly
 */
class AdSkipAccessibilityService : AccessibilityService() {

    private val cooldownMs = 3_000L
    private val lastTriggered = HashMap<String, Long>()
    private val groupEnabled = HashMap<String, Boolean>()

    /** UI toggles a rule on/off by its "$pkg#${group.key}" id. */
    fun setRuleEnabled(id: String, enabled: Boolean) {
        groupEnabled[id] = enabled
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Built-in rules for this package, plus any user-defined custom rules.
        val builtin = RuleRepository.groupsFor(pkg) ?: emptyList()
        val custom = CustomRuleStore.load(this)
            .filter { it.enabled && it.appId == pkg }
            .map { CustomRuleStore.toGroupRule(it) }
        val groups = builtin.map { it.key to it } + custom
        if (groups.isEmpty()) return
        val root = rootInActiveWindow ?: return
        // Only match the app that is actually in the foreground; the event source can
        // be a background window while another app is showing.
        val activePkg = root.packageName?.toString()
        if (activePkg != null && activePkg != pkg) return

        try {
            val wrapped = AccessibilityNodeAdapter(root)
            val now = System.currentTimeMillis()
            for ((groupKey, group) in groups) {
                val id = "$pkg#$groupKey"
                if (groupEnabled[id] == false) continue
                if (now - (lastTriggered[id] ?: 0L) < cooldownMs) continue

                try {
                    val selector = Selector.parse(group.matches)
                    val target = Matcher.findNode(wrapped, selector) ?: continue
                    val rawTarget = (target as? AccessibilityNodeAdapter)?.raw
                    Log.i(TAG, "match: $id ${group.name} action=${group.action}")
                    ActionExecutor.execute(this, group.action, rawTarget)
                    lastTriggered[id] = now
                    // One action per event: further groups would run against a stale tree.
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "rule failed for ${group.matches}", e)
                }
            }
        } finally {
            // Nodes must not be used after recycle; action already performed above.
            root.recycle()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() { /* ignore */ }

    companion object {
        private const val TAG = "AdSkipSvc"

        /** Current running service instance, used by the UI to toggle rules. */
        @Volatile
        var instance: AdSkipAccessibilityService? = null
    }
}

/**
 * Adapts an [AccessibilityNodeInfo] into the SDK-independent [MatchableNode] used by
 * the matcher. Holds a reference to the raw node so the action executor can click it.
 */
private class AccessibilityNodeAdapter(val raw: AccessibilityNodeInfo) : MatchableNode {
    override val className: String? = raw.className?.toString()
    override val text: String? = raw.text?.toString()
    override val desc: String? = raw.contentDescription?.toString()
    override val viewId: String? = raw.viewIdResourceName
    override val clickable: Boolean = raw.isClickable

    override val parent: MatchableNode?
        get() = raw.parent?.let { AccessibilityNodeAdapter(it) }

    override val children: List<MatchableNode>
        get() = (0 until raw.childCount).mapNotNull { i -> raw.getChild(i)?.let { AccessibilityNodeAdapter(it) } }
}