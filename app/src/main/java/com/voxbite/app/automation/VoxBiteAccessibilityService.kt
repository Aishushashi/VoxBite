package com.voxbite.app.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class VoxBiteAccessibilityService : AccessibilityService() {

    enum class SwiggyState { IDLE, SEARCHING, TAPPED_ITEM, DONE }
    enum class OlaState    { IDLE, APP_OPEN, FIELD_FOCUSED, SUGGESTIONS_LOADED, DONE }

    companion object {
        @Volatile var instance: VoxBiteAccessibilityService? = null
        val isConnected get() = instance != null

        @Volatile var pendingSwiggyItem     = ""
        @Volatile var swiggyState           = SwiggyState.IDLE

        @Volatile var pendingOlaDestination = ""
        @Volatile var olaState              = OlaState.IDLE

        private const val TAG = "VoxBiteA11y"
    }

    private val handler  = Handler(Looper.getMainLooper())
    private var sRetries = 0
    private var oRetries = 0

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        instance = this
        log("Service connected ✅")
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ─── Event Router ─────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg  = event.packageName?.toString() ?: return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        when {
            pkg == "in.swiggy.android"    && swiggyState != SwiggyState.IDLE -> onSwiggyEvent()
            pkg == "com.olacabs.customer" && olaState    != OlaState.IDLE    -> onOlaEvent()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SWIGGY — 3-strategy add-to-cart
    // ═══════════════════════════════════════════════════════════════

    private fun onSwiggyEvent() {
        handler.removeCallbacksAndMessages("swiggy")
        val delay = when (swiggyState) {
            SwiggyState.SEARCHING   -> 2000L
            SwiggyState.TAPPED_ITEM -> 1500L
            else -> return
        }
        handler.postDelayed({ runSwiggyStep() }, delay)
    }

    private fun runSwiggyStep() {
        val root = rootInActiveWindow ?: return

        when (swiggyState) {
            SwiggyState.SEARCHING -> {
                if (tryAddButtonNearItem(root)) return
                if (tryAnyAddButton(root)) return
                if (tryTapItemCard(root)) {
                    swiggyState = SwiggyState.TAPPED_ITEM
                    return
                }
                sRetry("Search results not ready") {
                    toast("⚠️ Could not find '${pendingSwiggyItem}' on Swiggy")
                    resetSwiggy()
                }
            }

            SwiggyState.TAPPED_ITEM -> {
                if (tryAnyAddButton(root)) return
                sRetry("ADD button not visible yet") {
                    toast("⚠️ ADD button not found for '${pendingSwiggyItem}'")
                    resetSwiggy()
                }
            }

            else -> {}
        }
    }

    // Strategy A: find item node → walk up to card → find ADD inside card
    private fun tryAddButtonNearItem(root: AccessibilityNodeInfo): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(pendingSwiggyItem)
        for (node in nodes) {
            if (!node.isVisibleToUser) continue
            val card   = findCardAncestor(node, depth = 8) ?: continue
            val addBtn = findAddButtonInSubtree(card) ?: continue
            addBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            onAddSuccess("Strategy A")
            return true
        }
        return false
    }

    // Strategy B: any ADD / + button visible on screen
    private fun tryAnyAddButton(root: AccessibilityNodeInfo): Boolean {
        for (text in listOf("ADD", "Add", "+", "add")) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (!node.isVisibleToUser) continue
                val target = if (node.isClickable) node else findClickableParent(node)
                if (target != null) {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    onAddSuccess("Strategy B (text='$text')")
                    return true
                }
            }
        }
        val cdNodes = findNodesByContentDesc(root, listOf("ADD", "add", "Add to cart"))
        for (node in cdNodes) {
            if (!node.isVisibleToUser) continue
            val target = if (node.isClickable) node else findClickableParent(node)
            if (target != null) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                onAddSuccess("Strategy B (content desc)")
                return true
            }
        }
        return false
    }

    // Strategy C: tap the item card to open detail page, then look for ADD
    private fun tryTapItemCard(root: AccessibilityNodeInfo): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(pendingSwiggyItem)
        val node  = nodes.firstOrNull { it.isVisibleToUser } ?: return false
        val card  = findClickableParent(node) ?: return false
        card.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        log("Strategy C — tapped item card: $pendingSwiggyItem")
        return true
    }

    private fun onAddSuccess(strategy: String) {
        val item = pendingSwiggyItem
        swiggyState = SwiggyState.DONE
        resetSwiggy()
        toast("✅ '$item' added to cart!")
        log("$strategy ✅ — '$item' added to cart")
    }

    private fun sRetry(reason: String, onGiveUp: () -> Unit) {
        sRetries++
        log("$reason (retry $sRetries/6)")
        if (sRetries < 6) handler.postDelayed({ runSwiggyStep() }, 1800)
        else { sRetries = 0; onGiveUp() }
    }

    private fun resetSwiggy() {
        pendingSwiggyItem = ""
        swiggyState       = SwiggyState.IDLE
        sRetries          = 0
    }

    // ═══════════════════════════════════════════════════════════════
    //  OLA — fills destination from voice
    // ═══════════════════════════════════════════════════════════════

    private fun onOlaEvent() {
        handler.removeCallbacksAndMessages("ola")
        val delay = when (olaState) {
            OlaState.APP_OPEN           -> 2500L
            OlaState.FIELD_FOCUSED      -> 800L
            OlaState.SUGGESTIONS_LOADED -> 1200L
            else -> return
        }
        handler.postDelayed({ runOlaStep() }, delay)
    }

    private fun runOlaStep() {
        val root = rootInActiveWindow ?: return

        when (olaState) {

            OlaState.APP_OPEN -> {
                val hints = listOf(
                    "Where to?", "Enter destination",
                    "Drop", "Destination", "Search", "where to"
                )
                var focused = false
                for (hint in hints) {
                    val nodes = root.findAccessibilityNodeInfosByText(hint)
                    val node  = nodes.firstOrNull { it.isVisibleToUser } ?: continue
                    val field = findEditableInSubtree(node)
                        ?: findClickableParent(node)
                        ?: node
                    field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    olaState = OlaState.FIELD_FOCUSED
                    log("Focused destination field via hint: '$hint'")
                    focused = true
                    break
                }
                if (!focused) {
                    val edits = collectAllEditTexts(root)
                    if (edits.size >= 2) {
                        edits[1].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        olaState = OlaState.FIELD_FOCUSED
                        log("Focused via EditText[1]")
                    } else {
                        olaRetry("Destination field not found")
                    }
                }
            }

            OlaState.FIELD_FOCUSED -> {
                val edits  = collectAllEditTexts(root)
                val target = edits.firstOrNull { it.isFocused } ?: edits.getOrNull(1)
                if (target != null) {
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            pendingOlaDestination
                        )
                    }
                    target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    olaState = OlaState.SUGGESTIONS_LOADED
                    log("Typed destination: $pendingOlaDestination")
                } else {
                    olaRetry("EditText not focused yet")
                }
            }

            OlaState.SUGGESTIONS_LOADED -> {
                val dest  = pendingOlaDestination
                val nodes = root.findAccessibilityNodeInfosByText(dest)
                val match = nodes.firstOrNull { it.isVisibleToUser }
                if (match != null) {
                    val target = if (match.isClickable) match else findClickableParent(match)
                    target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    toast("📍 Destination set: $dest")
                    log("Selected suggestion: $dest ✅")
                    resetOla()
                } else {
                    val first = findFirstListItem(root)
                    if (first != null) {
                        first.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        toast("📍 Destination selected!")
                        log("Selected first suggestion row ✅")
                        resetOla()
                    } else {
                        olaRetry("Suggestions not loaded yet")
                    }
                }
            }

            else -> {}
        }
    }

    private fun olaRetry(reason: String) {
        oRetries++
        log("$reason (retry $oRetries/5)")
        if (oRetries < 5) handler.postDelayed({ runOlaStep() }, 1500)
        else { toast("⚠️ Could not set Ola destination automatically"); resetOla() }
    }

    private fun resetOla() {
        pendingOlaDestination = ""
        olaState              = OlaState.IDLE
        oRetries              = 0
    }

    // ═══════════════════════════════════════════════════════════════
    //  TREE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private fun findCardAncestor(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var best: AccessibilityNodeInfo? = null
        repeat(depth) {
            n = n?.parent ?: return best
            if (n!!.isClickable) best = n
        }
        return best
    }

    private fun findAddButtonInSubtree(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = root.text?.toString()?.trim()
        val desc = root.contentDescription?.toString()?.trim()
        val isAdd = text == "ADD" || text == "Add" || text == "+"
                || desc?.lowercase()?.contains("add") == true
        if (isAdd && root.isVisibleToUser) {
            return if (root.isClickable) root else findClickableParent(root)
        }
        for (i in 0 until root.childCount) {
            val f = findAddButtonInSubtree(root.getChild(i) ?: continue)
            if (f != null) return f
        }
        return null
    }

    private fun findNodesByContentDesc(
        root: AccessibilityNodeInfo,
        descriptions: List<String>
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun dfs(n: AccessibilityNodeInfo) {
            val cd = n.contentDescription?.toString()?.lowercase() ?: ""
            if (descriptions.any { cd.contains(it.lowercase()) }) result.add(n)
            for (i in 0 until n.childCount) dfs(n.getChild(i) ?: return)
        }
        dfs(root)
        return result
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        repeat(6) { n?.let { if (it.isClickable) return it else n = it.parent } }
        return null
    }

    private fun findEditableInSubtree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val f = findEditableInSubtree(node.getChild(i) ?: continue)
            if (f != null) return f
        }
        return null
    }

    private fun collectAllEditTexts(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val r = mutableListOf<AccessibilityNodeInfo>()
        fun dfs(n: AccessibilityNodeInfo) {
            if (n.isEditable) r.add(n)
            for (i in 0 until n.childCount) dfs(n.getChild(i) ?: return)
        }
        dfs(root)
        return r
    }

    private fun findFirstListItem(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val cls = root.className?.toString() ?: ""
        if ((cls.contains("RecyclerView") || cls.contains("ListView")) && root.childCount > 0) {
            val child = root.getChild(0)
            return if (child?.isClickable == true) child
            else findClickableParent(child ?: return null)
        }
        for (i in 0 until root.childCount) {
            val f = findFirstListItem(root.getChild(i) ?: continue)
            if (f != null) return f
        }
        return null
    }

    private fun toast(msg: String) =
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    private fun log(msg: String) = Log.d(TAG, msg)
}