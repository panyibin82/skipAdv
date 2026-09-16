package com.skipadv.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Executes the action defined by a matched rule against an accessibility node.
 * Mirrors GKD's action set for the MVP: click / clickNode / clickCenter / back.
 */
object ActionExecutor {

    fun execute(service: AccessibilityService, action: String, node: AccessibilityNodeInfo?) {
        when (action) {
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

            "clickCenter" -> centerClick(service, node)

            "click", "clickNode", "" -> click(service, node)

            else -> { /* unknown action: ignore */ }
        }
    }

    /**
     * Clicks [node] with escalating reliability:
     *  1. ACTION_CLICK on the node itself (works for standard widgets)
     *  2. ACTION_CLICK on the nearest clickable ancestor (skip buttons are often
     *     wrapped in a container that owns the click handler)
     *  3. a real touch gesture at the node's center (ad SDKs / WebViews frequently
     *     ignore synthetic accessibility clicks but respond to actual motion events)
     */
    private fun click(service: AccessibilityService, node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
        var cur = node.parent
        while (cur != null) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            cur = cur.parent
        }
        centerClick(service, node)
    }

    /** Midpoint click via GestureDescription (works even for non-clickable nodes). */
    private fun centerClick(service: AccessibilityService, node: AccessibilityNodeInfo?) {
        if (node == null) return
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return
        val cx = r.exactCenterX().toFloat()
        val cy = r.exactCenterY().toFloat()
        val path = Path().apply { moveTo(cx, cy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50L))
            .build()
        service.dispatchGesture(gesture, null, null)
    }
}