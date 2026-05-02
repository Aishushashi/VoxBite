package com.voxbite.app.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.voxbite.app.model.OrderItem

class VoxBiteAccessibilityService : AccessibilityService() {

    companion object {
        var instance: VoxBiteAccessibilityService? = null
        var isConnected = false
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isConnected = false
    }

    // ─── SWIGGY FLOW ───────────────────────────

    fun openSwiggyAndSearch(item: OrderItem, onDone: () -> Unit) {
        val launchIntent = packageManager
            .getLaunchIntentForPackage("in.swiggy.android")
        if (launchIntent == null) {
            onDone()
            return
        }
        startActivity(launchIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        handler.postDelayed({
            clickSearchBar()
            handler.postDelayed({
                typeInSearch(item.name)
                handler.postDelayed({
                    onDone()
                }, 3000)
            }, 1500)
        }, 3000)
    }

    private fun clickSearchBar() {
        val root = rootInActiveWindow ?: return
        val searchIds = listOf(
            "in.swiggy.android:id/search_hint",
            "in.swiggy.android:id/et_search",
            "in.swiggy.android:id/search_bar"
        )
        for (id in searchIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
        findAndClickByText("Search")
    }

    private fun typeInSearch(query: String) {
        val root = rootInActiveWindow ?: return
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        findEditableNodes(root, editableNodes)
        if (editableNodes.isNotEmpty()) {
            val bundle = Bundle()
            bundle.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                query
            )
            editableNodes[0].performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT, bundle
            )
        }
    }

    fun clickAddButton(onAdded: () -> Unit) {
        handler.postDelayed({
            findAndClickByText("ADD") ||
                    findAndClickByText("Add") ||
                    findAndClickByText("+")
            onAdded()
        }, 2000)
    }

    // ─── OLA FLOW ──────────────────────────────

    fun openOlaForDestination(destination: String, onDone: () -> Unit) {
        val launchIntent = packageManager
            .getLaunchIntentForPackage("com.olacabs.customer")
        if (launchIntent == null) { onDone(); return }

        startActivity(launchIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        handler.postDelayed({
            findAndClickByText("Where are you going?")
            handler.postDelayed({
                typeInSearch(destination)
                handler.postDelayed({ onDone() }, 2000)
            }, 1500)
        }, 3000)
    }

    // ─── HELPERS ───────────────────────────────

    private fun findAndClickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return if (nodes.isNotEmpty()) {
            nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            true
        } else false
    }

    private fun findEditableNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isEditable) result.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findEditableNodes(it, result) }
        }
    }
}