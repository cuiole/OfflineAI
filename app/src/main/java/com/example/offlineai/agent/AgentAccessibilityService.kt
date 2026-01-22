package com.example.offlineai.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.offlineai.LogManager
import com.example.offlineai.MainActivity
import com.example.offlineai.R
import com.example.offlineai.RagQueryManager
import com.example.offlineai.agent.core.AgentEngine
import com.example.offlineai.agent.ui.AgentFloatingWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Accessibility Service for Agent execution
 * Provides UI automation capabilities: click, type, swipe, etc.
 */
class AgentAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AgentAccessibilityService"
        private const val NOTIFICATION_CHANNEL_ID = "agent_execution_channel"
        private const val NOTIFICATION_ID = 1001
        
        @Volatile
        private var instance: AgentAccessibilityService? = null
        
        fun getInstance(): AgentAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    private var ragQueryManager: RagQueryManager? = null
    private var agentEngine: AgentEngine? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var agentLoopJob: Job? = null
    private var floatingWindow: AgentFloatingWindow? = null
    private var currentStep = 0
    private val maxSteps = 20
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        createNotificationChannel()
        initializeFloatingWindow()
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
        stopAgentLoop()
        floatingWindow?.hide()
        floatingWindow = null
        instance = null
        LogManager.logI(TAG, "Accessibility service destroyed")
    }
    
    /**
     * Set RagQueryManager reference for Agent loop execution
     */
    fun setRagQueryManager(manager: RagQueryManager?) {
        this.ragQueryManager = manager
        LogManager.logI(TAG, "RagQueryManager reference set: ${manager != null}")
    }
    
    /**
     * Set AgentEngine reference for Agent loop execution
     */
    fun setAgentEngine(engine: AgentEngine?) {
        this.agentEngine = engine
        LogManager.logI(TAG, "AgentEngine reference set: ${engine != null}")
    }
    
    /**
     * Start Agent autonomous loop execution
     */
    fun startAgentLoop(taskGoal: String) {
        if (ragQueryManager == null) {
            LogManager.logE(TAG, "Cannot start Agent loop: RagQueryManager is null")
            return
        }
        
        if (agentEngine == null) {
            LogManager.logE(TAG, "Cannot start Agent loop: AgentEngine is null")
            return
        }
        
        // Stop existing loop if any
        stopAgentLoop()
        
        LogManager.logI(TAG, "Starting Agent loop: $taskGoal")
        setAgentActive(true)
        currentStep = 0
        
        // Show floating window (non-blocking, failure won't stop Agent)
        try {
            floatingWindow?.show()
            floatingWindow?.updateTask(taskGoal)
            floatingWindow?.updateStep(0, maxSteps)
            floatingWindow?.updateStatus("Initializing...")
            LogManager.logI(TAG, "Floating window shown successfully")
        } catch (e: Exception) {
            LogManager.logE(TAG, "Failed to show floating window, but Agent will continue", e)
        }
        showNotification("Agent Executing", "Task: $taskGoal")
        
        agentLoopJob = serviceScope.launch {
            try {
                LogManager.logI(TAG, "Agent loop coroutine started, calling AgentEngine.executeTask()")
                
                // Call AgentEngine.executeTask with model inference callback
                agentEngine?.executeTask(taskGoal) { instruction, screenshot, history ->
                    // This callback will be called by AgentEngine for each step
                    // We need to call RagQueryManager synchronously here
                    LogManager.logI(TAG, "Agent loop step callback invoked, calling RagQueryManager")
                    callRagQueryManagerSync(instruction, screenshot)
                }
                
                LogManager.logI(TAG, "Agent loop completed")
                try {
                    floatingWindow?.updateStatus("Task completed")
                } catch (e: Exception) {
                    LogManager.logW(TAG, "Failed to update floating window on completion")
                }
                showNotification("Agent Completed", "Task finished")
                
                // Hide floating window after delay
                delay(2000)
                try {
                    floatingWindow?.hide()
                } catch (e: Exception) {
                    LogManager.logW(TAG, "Failed to hide floating window")
                }
                
            } catch (e: Exception) {
                LogManager.logE(TAG, "Agent loop failed: ${e.message}", e)
                e.printStackTrace()
                try {
                    floatingWindow?.updateStatus("Error: ${e.message}")
                } catch (ex: Exception) {
                    LogManager.logW(TAG, "Failed to update floating window on error")
                }
                showNotification("Agent Error", "Error: ${e.message}")
                delay(3000)
                try {
                    floatingWindow?.hide()
                } catch (ex: Exception) {
                    LogManager.logW(TAG, "Failed to hide floating window on error")
                }
            } finally {
                setAgentActive(false)
            }
        }
    }
    
    /**
     * Stop Agent loop execution
     */
    fun stopAgentLoop() {
        agentLoopJob?.cancel()
        agentLoopJob = null
        setAgentActive(false)
        floatingWindow?.hide()
        hideNotification()
        LogManager.logI(TAG, "Agent loop stopped")
    }
    
    /**
     * Call RagQueryManager synchronously for Agent inference
     * This is a blocking call that waits for model response
     */
    private suspend fun callRagQueryManagerSync(instruction: String, screenshot: Bitmap?): String {
        LogManager.logI(TAG, "Calling RagQueryManager synchronously for Agent inference (screenshot=${screenshot != null})")
        
        currentStep++
        try {
            floatingWindow?.updateStep(currentStep, maxSteps)
            floatingWindow?.updateStatus("Thinking...")
        } catch (e: Exception) {
            LogManager.logW(TAG, "Failed to update floating window: ${e.message}")
        }
        updateNotification("Agent Thinking", "Step $currentStep")
        
        return withContext(Dispatchers.IO) {
            try {
                val ragMgr = ragQueryManager ?: run {
                    LogManager.logE(TAG, "RagQueryManager reference is null")
                    return@withContext "<tool_call>{\"action\":\"terminate\",\"text\":\"Error: RagQueryManager not available\"}</tool_call>"
                }
                
                // Agent module builds its own QueryRequest with Agent-specific configuration
                val context = applicationContext
                val apiUrl = com.example.offlineai.ConfigManager.getString(context, com.example.offlineai.ConfigManager.KEY_API_URL, com.example.offlineai.AppConstants.ApiUrl.LOCAL)
                val apiKey = com.example.offlineai.ConfigManager.getString(context, com.example.offlineai.ConfigManager.KEY_API_KEY, "")
                val model = com.example.offlineai.ConfigManager.getString(context, com.example.offlineai.ConfigManager.KEY_MODEL_NAME, "")
                
                // Agent module manages its own prompt (MAI-UI standard format)
                val noThinking = com.example.offlineai.ConfigManager.getBoolean(context, com.example.offlineai.ConfigManager.KEY_NO_THINKING, false)
                val systemPrompt = com.example.offlineai.agent.AgentPrompts.getAgentSystemPrompt(noThinking)
                
                // Build QueryRequest for Agent
                val request = com.example.offlineai.RagQueryManager.QueryRequest(
                    apiUrl,
                    apiKey,
                    model,
                    "", // knowledgeBase - Agent doesn't need it
                    systemPrompt,
                    instruction, // userPrompt
                    null, // imagePaths - TODO: convert screenshot to image path if needed
                    null, // audioPaths
                    0f, // audioDuration
                    0, // searchDepth - Agent doesn't need RAG
                    false, // graphRagEnabled
                    false, // needsAsr
                    null // asrModel
                )
                
                // Call generic synchronous query method (not Agent-specific)
                val result = ragMgr.querySync(request)
                
                if (result.isNullOrEmpty()) {
                    LogManager.logE(TAG, "RagQueryManager returned null or empty result")
                    floatingWindow?.updateStatus("Error: No response")
                    return@withContext "<tool_call>{\"action\":\"terminate\",\"text\":\"Error: Model returned no response\"}</tool_call>"
                }
                
                LogManager.logI(TAG, "RagQueryManager returned result: ${result.take(100)}...")
                floatingWindow?.updateStatus("Action received")
                
                result
            } catch (e: Exception) {
                LogManager.logE(TAG, "Error calling RagQueryManager", e)
                floatingWindow?.updateStatus("Error: ${e.message}")
                "<tool_call>{\"action\":\"terminate\",\"text\":\"Error: ${e.message}\"}</tool_call>"
            }
        }
    }
    
    /**
     * Create notification channel for Agent execution status
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Agent Execution",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Agent execution status"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show notification with Agent status
     */
    private fun showNotification(title: String, content: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Update notification content
     */
    private fun updateNotification(title: String, content: String) {
        showNotification(title, content)
    }
    
    /**
     * Hide notification
     */
    private fun hideNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIFICATION_ID)
    }
    
    /**
     * Initialize floating window
     */
    private fun initializeFloatingWindow() {
        floatingWindow = AgentFloatingWindow(this)
        floatingWindow?.setOnStopClickListener {
            LogManager.logI(TAG, "User clicked stop button in floating window")
            stopAgentLoop()
        }
        LogManager.logI(TAG, "Floating window initialized")
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
