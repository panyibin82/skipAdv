package com.skipadv.rule

/**
 * GKD-style rule models (see https://gkd.li/guide/). One [AppRule] describes the
 * ad-skip/auto-click behaviour for a single target app, keyed by its package name.
 */
data class GroupRule(
    val key: Int,
    val name: String,
    /** GKD-style selector, e.g. `[text^="跳过"][clickable=true]`. */
    val matches: String,
    /** One of: click (default) / clickNode / clickCenter / back. */
    val action: String = "click",
    /** key of the group that must have fired before this one may fire (GKD preKeys). */
    val preKeys: List<Int> = emptyList(),
)

data class AppRule(
    val appId: String,
    val groups: List<GroupRule>,
)