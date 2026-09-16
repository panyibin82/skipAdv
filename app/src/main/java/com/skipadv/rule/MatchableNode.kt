package com.skipadv.rule

/**
 * An abstraction of an accessibility node that the selector/matcher engine can work
 * against. Kept independent of the Android SDK so it can be unit-tested on the JVM.
 * The Android accessibility service provides a real implementation backed by
 * [android.view.accessibility.AccessibilityNodeInfo] (see the service package).
 */
interface MatchableNode {
    /** Fully-qualified class name, e.g. "android.widget.TextView", or null. */
    val className: String?

    /** Node text (android:text), e.g. the button label "跳过". */
    val text: String?

    /** Node content description (android:contentDescription). */
    val desc: String?

    /** Resource id as reported by getViewIdResourceName(), e.g. "com.pkg:id/skip". */
    val viewId: String?

    /** Whether the node is reported as clickable. */
    val clickable: Boolean

    val parent: MatchableNode?

    val children: List<MatchableNode>
}