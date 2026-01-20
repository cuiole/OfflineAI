package com.example.offlineai.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.offlineai.LogManager

/**
 * Accessibility Service for Agent execution
 * Provides UI automation capabilities: click, type, swipe, etc.
 */
class AgentAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AgentAccessibilityService"
        
        @Volatile
        private var instance: AgentAccessibilityService? = null
        
        fun getInstance(): AgentAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogManager.logI(TAG, "Accessibility service connected")
    }
    
    private var isAgentActive = false
    
    fun setAgentActive(active: Boolean) {
        isAgentActive = active
        LogManager.logI(TAG, "Agent active state changed: $active")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only process events when Agent is actively executing
        if (!isAgentActive) {
            return
        }
        
        // Monitor UI changes for action completion detection
        event?.let {
            LogManager.logD(TAG, "Accessibility event: ${it.eventType}, package: ${it.packageName}")
        }
    }
    
    override fun onInterrupt() {
        LogManager.logW(TAG, "Accessibility service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isAgentActive = false
        instance = null
        LogManager.logI(TAG, "Accessibility service destroyed")
    }
    
    /**
     * Click at screen position
     */
    fun clickAtPosition(x: Int, y: Int): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        val result = dispatchGesture(gesture, null, null)
        LogManager.logD(TAG, "Click at ($x, $y): ${if (result) "success" else "failed"}")
        return result
    }
    
    /**
     * Long press at screen position
     */
    fun longPressAtPosition(x: Int, y: Int, durationMs: Long = 1000): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        val result = dispatchGesture(gesture, null, null)
        LogManager.logD(TAG, "Long press at ($x, $y): ${if (result) "success" else "failed"}")
        return result
    }
    
    /**
     * Double click at screen position
     */
    fun doubleClickAtPosition(x: Int, y: Int): Boolean {
        val path1 = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val path2 = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, 100))
            .addStroke(GestureDescription.StrokeDescription(path2, 150, 100))
            .build()
        
        val result = dispatchGesture(gesture, null, null)
        LogManager.logD(TAG, "Double click at ($x, $y): ${if (result) "success" else "failed"}")
        return result
    }
    
    /**
     * Input text to focused input field
     */
    fun inputText(text: String): Boolean {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        
        if (focusedNode == null) {
            LogManager.logW(TAG, "No focused input field found")
            return false
        }
        
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        
        val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        @Suppress("DEPRECATION")
        focusedNode.recycle()
        
        LogManager.logD(TAG, "Input text '$text': ${if (result) "success" else "failed"}")
        return result
    }
    
    /**
     * Swipe gesture
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        val result = dispatchGesture(gesture, null, null)
        LogManager.logD(TAG, "Swipe from ($startX, $startY) to ($endX, $endY): ${if (result) "success" else "failed"}")
        return result
    }
    
    /**
     * Drag gesture (longer duration than swipe)
     */
    fun drag(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 600): Boolean {
        return swipe(startX, startY, endX, endY, durationMs)
    }
    
    /**
     * Press back button
     */
    fun pressBack(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        LogManager.logD(TAG, "Press back: ${if (result) "success" else "failed"}")
        return result
    }
    
    /**
     * Press home button
     */
    fun pressHome(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        LogManager.logD(TAG, "Press home: ${if (result) "success" else "failed"}")
        return result
    }
    
    /**
     * Press recent apps button
     */
    fun pressRecents(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
        LogManager.logD(TAG, "Press recents: ${if (result) "success" else "failed"}")
        return result
    }
    
    /**
     * Get current window root node (for debugging)
     */
    fun getCurrentWindowRoot(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }
}
