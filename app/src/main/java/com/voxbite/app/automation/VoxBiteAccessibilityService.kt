package com.voxbite.app.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.voxbite.app.model.OrderItem

class VoxBiteAccessibilityService : AccessibilityService() {

    private val TAG = "VoxBite"

    companion object {
        var instance: VoxBiteAccessibilityService? = null
        var isConnected: Boolean = false
    }

    override fun onServiceConnected() {
        instance = this
        isConnected = true
        Log.d(TAG, "AccessibilityService connected")

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Currently handled via deep links — no scraping needed
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isConnected = false
        Log.d(TAG, "AccessibilityService destroyed")
    }

    // Opens Swiggy and searches for a food item via deep link
    fun openSwiggyAndSearch(item: OrderItem, onDone: () -> Unit) {
        try {
            val query = item.name.replace(" ", "+")
            val uri = Uri.parse("https://www.swiggy.com/search?query=$query")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d(TAG, "Opened Swiggy for: ${item.name}")
            onDone()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Swiggy: ${e.message}")
            onDone()
        }
    }

    // Opens Ola with destination via deep link
    fun openOlaForDestination(destination: String, onDone: () -> Unit) {
        try {
            val encodedDest = Uri.encode(destination)
            val uri = Uri.parse("https://book.olacabs.com/?drop=$encodedDest")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d(TAG, "Opened Ola for destination: $destination")
            onDone()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Ola: ${e.message}")
            onDone()
        }
    }

    // Utility: find a node by text on screen (for future use)
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNode(root, text)
    }

    private fun findNode(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, text)
            if (found != null) return found
        }
        return null
    }

    // Utility: click a button by text on screen (for future use)
    fun clickButtonByText(text: String): Boolean {
        val node = findNodeByText(text)
        return if (node != null && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Clicked button: $text")
            true
        } else {
            Log.d(TAG, "Button not found or not clickable: $text")
            false
        }
    }

    // Placeholder kept for compatibility with MainActivity calls
    fun clickAddButton(onDone: () -> Unit) {
        val clicked = clickButtonByText("ADD") || clickButtonByText("Add")
        Log.d(TAG, "clickAddButton result: $clicked")
        onDone()
    }
}