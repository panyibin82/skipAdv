package com.skipadv.rule

/**
 * Walks a [MatchableNode] tree to find the first element matching a [Selector].
 *
 * The last selector step is the leaf to locate; earlier steps are validated against
 * the node's ancestors (a `A > B` chain means B is a child of A), walking up via
 * [MatchableNode.parent]. The returned node is the action target (the step marked `@`,
 * or the matched leaf by default).
 */
object Matcher {
    fun findNode(root: MatchableNode, selector: Selector): MatchableNode? {
        val parts = selector.parts
        val last = parts.last()

        fun walk(n: MatchableNode): MatchableNode? {
            if (last.matches(n)) {
                var cur = n
                var ok = true
                for (i in parts.lastIndex - 1 downTo 0) {
                    val p = cur.parent ?: run { ok = false; break }
                    cur = p
                    if (!parts[i].matches(p)) { ok = false; break }
                }
                if (ok) return actionTarget(n, parts)
            }
            for (c in n.children) {
                walk(c)?.let { return it }
            }
            return null
        }

        return walk(root)
    }

    private fun actionTarget(leaf: MatchableNode, parts: List<SelectorPart>): MatchableNode {
        var cur = leaf
        for (i in parts.lastIndex downTo 0) {
            if (parts[i].isAction) return cur
            cur = cur.parent ?: break
        }
        return leaf
    }
}