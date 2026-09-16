package com.skipadv.rule

import java.util.concurrent.ConcurrentHashMap

/**
 * A GKD-style selector, parsed once and used to match against [MatchableNode]s.
 *
 * Supported grammar (a focused subset of GKD / CSS):
 *  - a chain of node steps separated by `>` (parent -> child relationship)
 *  - an optional `@` on a step marks it as the node the action should target
 *  - an optional leading simple class name, e.g. `TextView` (matched against the
 *    fully-qualified class name suffix); `*` means "any class"
 *  - zero or more attribute predicates `[attr(op)value]` per step
 *
 * Attribute operators:
 *   [text]            presence (non-blank)
 *   [text=X]          equals
 *   [text!=X]         not equals
 *   [text^="X"]       startsWith
 *   [text$="X"]       endsWith
 *   [text*="X"]       contains
 *   [clickable]       node is clickable
 *   [clickable=true]  / [clickable=false]
 *
 * Supported attributes: text, desc, id, class, clickable.
 *
 * Examples:
 *   [text^="跳过"][clickable=true]
 *   @LinearLayout[clickable=true] > TextView[text^="关闭"]
 *   *[id$="skip"][clickable=true]
 */
class Selector private constructor(
    val parts: List<SelectorPart>,
) {
    /** The step to perform the action on: the one marked `@`, else the last step. */
    val actionIndex: Int = parts.indexOfLast { it.isAction }.let { if (it < 0) parts.lastIndex else it }

    fun matchesRootOrChild(root: MatchableNode): MatchableNode? = Matcher.findNode(root, this)

    enum class Op { PRESENT, EQ, NEQ, PREFIX, SUFFIX, CONTAINS }

    enum class Attr { TEXT, DESC, ID, CLASS, CLICKABLE }

    /** A single attribute predicate parsed from one `[...]` block. */
    class AttrPredicate(
        private val attr: Attr,
        private val op: Op,
        private val value: String?,
    ) : Predicate {
        override fun matches(n: MatchableNode): Boolean {
            if (attr == Attr.CLICKABLE) {
                val b = n.clickable
                return when (op) {
                    Op.PRESENT -> b
                    Op.EQ -> (value == "true") == b
                    Op.NEQ -> (value != "true") == b
                    else -> false
                }
            }
            val v: String? = when (attr) {
                Attr.TEXT -> n.text
                Attr.DESC -> n.desc
                Attr.ID -> n.viewId
                Attr.CLASS -> n.className
                Attr.CLICKABLE -> n.clickable.toString()
            }
            if (v == null) return op == Op.NEQ
            return when (op) {
                Op.PRESENT -> v.isNotEmpty()
                Op.EQ -> v == value
                Op.NEQ -> v != value
                Op.PREFIX -> v.startsWith(value ?: "")
                Op.SUFFIX -> v.endsWith(value ?: "")
                Op.CONTAINS -> v.contains(value ?: "")
            }
        }
    }

    companion object {
        private val cache = ConcurrentHashMap<String, Selector>()

        /** Parses a selector, caching by its source string. Throws on bad syntax. */
        fun parse(raw: String): Selector = cache.getOrPut(raw) { compile(raw) }

        private fun compile(raw: String): Selector {
            val parts = splitTop(raw.trim(), '>')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { parsePart(it, raw) }
            if (parts.isEmpty()) throw IllegalArgumentException("empty selector: '$raw'")
            return Selector(parts)
        }

        private fun parsePart(part: String, whole: String): SelectorPart {
            var s = part.trim()
            var isAction = false
            if (s.startsWith("@")) {
                isAction = true
                s = s.substring(1).trim()
            }
            var className: String? = null
            if (s.startsWith("*")) {
                s = s.substring(1).trim()
            } else {
                val cm = Regex("^([A-Za-z_][A-Za-z0-9_]*)").find(s)
                if (cm != null) {
                    val end = cm.range.last + 1
                    if (end == s.length || s[end] == '[') {
                        className = cm.groupValues[1]
                        s = if (end == s.length) "" else s.substring(end).trim()
                    }
                }
            }
            val predicates = mutableListOf<Predicate>()
            var i = 0
            while (i < s.length) {
                if (s[i] != '[') {
                    throw IllegalArgumentException("unexpected char '${s[i]}' in selector: '$whole'")
                }
                val close = s.indexOf(']', i)
                if (close < 0) throw IllegalArgumentException("unclosed '[' in selector: '$whole'")
                val inner = s.substring(i + 1, close)
                if (inner.isNotBlank()) predicates.add(parsePredicate(inner, whole))
                i = close + 1
            }
            return SelectorPart(isAction, className, predicates)
        }

        private fun parsePredicate(inner: String, whole: String): Predicate {
            val m = Regex("^([A-Za-z]\\w*)\\s*([!^$*]?=?)\\s*(.*)$").find(inner.trim())
                ?: throw IllegalArgumentException("bad attribute predicate '[$inner]' in selector: '$whole'")
            val attrName = m.groupValues[1]
            val opStr = m.groupValues[2]
            var value = m.groupValues[3].trim()
            if (value.length >= 2) {
                val first = value.first()
                if ((first == '"' || first == '\'') && value.last() == first) {
                    value = value.substring(1, value.length - 1)
                }
            }
            val attr = when (attrName) {
                "text" -> Selector.Attr.TEXT
                "desc" -> Selector.Attr.DESC
                "id" -> Selector.Attr.ID
                "class" -> Selector.Attr.CLASS
                "clickable" -> Selector.Attr.CLICKABLE
                else -> throw IllegalArgumentException("unknown attribute '$attrName' in selector: '$whole'")
            }
            val op = when (opStr) {
                "" -> Selector.Op.PRESENT
                "=" -> Selector.Op.EQ
                "!=" -> Selector.Op.NEQ
                "^=" -> Selector.Op.PREFIX
                "\$=" -> Selector.Op.SUFFIX
                "*=" -> Selector.Op.CONTAINS
                else -> throw IllegalArgumentException("bad operator '$opStr' in selector: '$whole'")
            }
            return AttrPredicate(attr, op, value)
        }

        /** Splits a selector chain on [sep] (default '>') but not inside `[...]`. */
        private fun splitTop(s: String, sep: Char = '>'): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var depth = 0
            for (ch in s) {
                when {
                    ch == '[' -> { depth++; cur.append(ch) }
                    ch == ']' -> { depth--; cur.append(ch) }
                    ch == sep && depth == 0 -> { out.add(cur.toString()); cur.setLength(0) }
                    else -> cur.append(ch)
                }
            }
            out.add(cur.toString())
            return out
        }
    }
}

/** One step in a selector chain. */
data class SelectorPart(
    val isAction: Boolean,
    val className: String?,
    val predicates: List<Predicate>,
) {
    fun matches(n: MatchableNode): Boolean {
        if (className != null) {
            val c = n.className ?: return false
            val ok = c == className || c.endsWith("." + className) || c.endsWith("/" + className)
            if (!ok) return false
        }
        for (p in predicates) if (!p.matches(n)) return false
        return true
    }
}

/** A single attribute predicate (see [Selector.AttrPredicate]). */
interface Predicate {
    fun matches(n: MatchableNode): Boolean
}