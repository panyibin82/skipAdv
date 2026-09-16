package com.skipadv.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory [MatchableNode] for unit testing the selector/matcher engine. */
private class FakeNode(
    override val className: String? = null,
    override val text: String? = null,
    override val desc: String? = null,
    override val viewId: String? = null,
    override val clickable: Boolean = false,
    override var parent: MatchableNode? = null,
    children: List<FakeNode> = emptyList(),
) : MatchableNode {
    override val children: List<MatchableNode> = children
}

private fun node(
    cls: String? = "android.widget.TextView",
    text: String? = null,
    desc: String? = null,
    id: String? = null,
    clickable: Boolean = false,
    children: List<FakeNode> = emptyList(),
): FakeNode {
    val n = FakeNode(cls, text, desc, id, clickable, null, children)
    children.forEach { it.parent = n }
    return n
}

class SelectorMatcherTest {

    private fun matches(root: MatchableNode, selector: String) =
        Matcher.findNode(root, Selector.parse(selector))

    @Test
    fun `matches node by text prefix and clickable`() {
        val root = node(children = listOf(
            node(text = "跳过广告", clickable = true),
            node(text = "关闭", clickable = false),
        ))
        val hit = matches(root, """[text^="跳过"][clickable=true]""")
        assertNotNull(hit)
        assertEquals("跳过广告", hit!!.text)
    }

    @Test
    fun `does not match non-clickable node`() {
        val root = node(children = listOf(node(text = "跳过广告", clickable = false)))
        assertNull(matches(root, """[text^="跳过"][clickable=true]"""))
    }

    @Test
    fun `contains operator and bare attribute means presence`() {
        val root = node(children = listOf(node(text = "【跳过】广告", clickable = true)))
        assertNotNull(matches(root, """[text*="跳过"][clickable]"""))
    }

    @Test
    fun `exact equals does not match substring`() {
        val root = node(children = listOf(node(text = "跳过开屏广告", clickable = true)))
        assertNull(matches(root, """[text="跳过"]"""))
        assertNotNull(matches(root, """[text="跳过开屏广告"]"""))
    }

    @Test
    fun `not equals matches mismatch`() {
        val root = node(children = listOf(node(text = "继续浏览", clickable = true)))
        assertNotNull(matches(root, """[text!="跳过"]"""))
    }

    @Test
    fun `matches resource id by suffix`() {
        val root = node(children = listOf(node(id = "com.your.app:id/skip_btn", clickable = true)))
        assertNotNull(matches(root, """[id$="skip_btn"]"""))
    }

    @Test
    fun `matches class name via simple name`() {
        val root = node(children = listOf(node(cls = "android.support.v7.widget.Button", text = "跳过", clickable = true)))
        assertNotNull(matches(root, """Button[text="跳过"]"""))
    }

    @Test
    fun `child chain with @ targets the ancestor`() {
        val target = node(cls = "android.widget.LinearLayout", clickable = true, children = listOf(
            node(text = "关闭"),
        ))
        val root = node(children = listOf(target))
        val result = matches(root, """@LinearLayout[clickable=true] > [text="关闭"]""")

        assertNotNull(result)
        assertEquals(target, result)
    }

    @Test
    fun `child chain fails when leaf is not inside the ancestor`() {
        val sibling = node(text = "关闭")
        val root = node(children = listOf(
            node(cls = "android.widget.LinearLayout", clickable = true),
            sibling,
        ))
        // "关闭" is a sibling, not a child of the LinearLayout -> no match
        assertNull(matches(root, """@LinearLayout[clickable=true] > [text="关闭"]"""))
    }

    @Test
    fun `default action target is the matched leaf`() {
        val leaf = node(text = "跳过", clickable = true)
        val root = node(children = listOf(leaf))
        assertNotNull(matches(root, """[text="跳过"][clickable=true]"""))
        assertTrue(Selector.parse("""[text="跳过"][clickable=true]""").actionIndex == 0)
    }

    @Test
    fun `malformed selector throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Selector.parse("[nosuchattr=1]")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Selector.parse("[text=1")
        }
    }
}