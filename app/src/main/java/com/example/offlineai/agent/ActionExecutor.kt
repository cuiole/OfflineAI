package com.example.offlineai.agent.executor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.offlineai.LogManager
import com.example.offlineai.agent.model.AgentAction
import com.example.offlineai.agent.model.ExecutionResult
import com.example.offlineai.agent.service.AgentAccessibilityService
import com.example.offlineai.agent.utils.AppNameMapper
import kotlinx.coroutines.delay

/**
 * Action Executor - executes agent actions using Accessibility Service
 * Based on MAI-UI action space
 */
class ActionExecutor(private val context: Context) {
    
    companion object {
        private const val TAG = "ActionExecutor"
        private const val SCALE_FACTOR = 999
        private const val ACTION_DELAY_MS = 1000L // Wait after each action for UI to stabilize
    }
    
    private val accessibilityService: AgentAccessibilityService?
        get() = AgentAccessibilityService.getInstance()
    
    private val screenWidth: Int
    private val screenHeight: Int
    
    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        
        LogManager.logI(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")
    }
    
    /**
     * Execute an agent action
     */
    suspend fun execute(action: AgentAction): ExecutionResult {
        if (accessibilityService == null) {
            return ExecutionResult(
                success = false,
                message = "Accessibility service not available. Please enable it in Settings."
            )
        }
        
        LogManager.logI(TAG, "Executing action: ${action.javaClass.simpleName}")
        
        val result = when (action) {
            is AgentAction.Click -> executeClick(action)
            is AgentAction.LongPress -> executeLongPress(action)
            is AgentAction.DoubleClick -> executeDoubleClick(action)
            is AgentAction.Type -> executeType(action)
            is AgentAction.Swipe -> executeSwipe(action)
            is AgentAction.Open -> executeOpen(action)
            is AgentAction.Drag -> executeDrag(action)
            is AgentAction.SystemButton -> executeSystemButton(action)
            is AgentAction.Wait -> executeWait()
            is AgentAction.Terminate -> executeTerminate(action)
            is AgentAction.Answer -> executeAnswer(action)
            is AgentAction.AskUser -> executeAskUser(action)
        }
        
        // Wait for UI to stabilize after action
        if (result.success && action !is AgentAction.Wait) {
            delay(ACTION_DELAY_MS)
        }
        
        return result
    }
    
    private fun executeClick(action: AgentAction.Click): ExecutionResult {
        val (screenX, screenY) = normalizeCoordinate(action.x, action.y)
        val success = accessibilityService?.clickAtPosition(screenX, screenY) ?: false
        
        return ExecutionResult(
            success = success,
            message = if (success) "Clicked at ($screenX, $screenY)" else "Failed to click"
        )
    }
    
    private fun executeLongPress(action: AgentAction.LongPress): ExecutionResult {
        val (screenX, screenY) = normalizeCoordinate(action.x, action.y)
        val success = accessibilityService?.longPressAtPosition(screenX, screenY) ?: false
        
        return ExecutionResult(
            success = success,
            message = if (success) "Long pressed at ($screenX, $screenY)" else "Failed to long press"
        )
    }
    
    private fun executeDoubleClick(action: AgentAction.DoubleClick): ExecutionResult {
        val (screenX, screenY) = normalizeCoordinate(action.x, action.y)
        val success = accessibilityService?.doubleClickAtPosition(screenX, screenY) ?: false
        
        return ExecutionResult(
            success = success,
            message = if (success) "Double clicked at ($screenX, $screenY)" else "Failed to double click"
        )
    }
    
    private fun executeType(action: AgentAction.Type): ExecutionResult {
        val success = accessibilityService?.inputText(action.text) ?: false
        
        return ExecutionResult(
            success = success,
            message = if (success) "Typed: ${action.text}" else "Failed to type text"
        )
    }
    
    private fun executeSwipe(action: AgentAction.Swipe): ExecutionResult {
        val (startX, startY, endX, endY) = calculateSwipeCoordinates(action)
        val success = accessibilityService?.swipe(startX, startY, endX, endY) ?: false
        
        return ExecutionResult(
            success = success,
            message = if (success) "Swiped ${action.direction.value}" else "Failed to swipe"
        )
    }
    
    private fun executeOpen(action: AgentAction.Open): ExecutionResult {
        LogManager.logI(TAG, "Opening app: ${action.appName}")
        
        // Priority 1 & 2: Check predefined launch strategy
        val strategy = AppNameMapper.getLaunchStrategy(action.appName)
        
        if (strategy != null) {
            return when (strategy) {
                is AppNameMapper.LaunchStrategy.IntentAction -> {
                    // Priority 1: Use Intent Action (system apps)
                    try {
                        val intent = strategy.createIntent()
                        context.startActivity(intent)
                        LogManager.logI(TAG, "Opened via Intent Action: ${action.appName}")
                        ExecutionResult(
                            success = true,
                            message = "Opened app: ${action.appName}"
                        )
                    } catch (e: Exception) {
                        LogManager.logE(TAG, "Failed to open via Intent: ${action.appName}", e)
                        ExecutionResult(
                            success = false,
                            message = "Failed to open app: ${action.appName}",
                            error = e
                        )
                    }
                }
                is AppNameMapper.LaunchStrategy.PackageName -> {
                    // Priority 2: Use package name (third-party apps)
                    openViaPackageName(action.appName, strategy.packageName)
                }
            }
        }
        
        // Priority 3: Fuzzy match on installed apps
        val fuzzyPackageName = AppNameMapper.getPackageName(context, action.appName)
        return if (fuzzyPackageName != null) {
            openViaPackageName(action.appName, fuzzyPackageName)
        } else {
            LogManager.logW(TAG, "App not found: ${action.appName}")
            ExecutionResult(
                success = false,
                message = "App not found: ${action.appName}"
            )
        }
    }
    
    /**
     * Open app via package name
     */
    private fun openViaPackageName(appName: String, packageName: String): ExecutionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                LogManager.logI(TAG, "Opened via package: $appName -> $packageName")
                ExecutionResult(
                    success = true,
                    message = "Opened app: $appName"
                )
            } else {
                LogManager.logW(TAG, "Cannot launch app: $appName (no launch intent)")
                ExecutionResult(
                    success = false,
                    message = "Cannot launch app: $appName"
                )
            }
        } catch (e: Exception) {
            LogManager.logE(TAG, "Failed to open app: $appName", e)
            ExecutionResult(
                success = false,
                message = "Failed to open app: $appName",
                error = e
            )
        }
    }
    
    private fun executeDrag(action: AgentAction.Drag): ExecutionResult {
        val (startX, startY) = normalizeCoordinate(action.startX, action.startY)
        val (endX, endY) = normalizeCoordinate(action.endX, action.endY)
        val success = accessibilityService?.drag(startX, startY, endX, endY) ?: false
        
        return ExecutionResult(
            success = success,
            message = if (success) "Dragged from ($startX, $startY) to ($endX, $endY)" else "Failed to drag"
        )
    }
    
    private fun executeSystemButton(action: AgentAction.SystemButton): ExecutionResult {
        val success = when (action.button) {
            AgentAction.SystemButton.Button.BACK -> accessibilityService?.pressBack()
            AgentAction.SystemButton.Button.HOME -> accessibilityService?.pressHome()
            AgentAction.SystemButton.Button.MENU -> accessibilityService?.pressRecents()
            AgentAction.SystemButton.Button.ENTER -> {
                // Enter is typically handled by typing \n or clicking a button
                accessibilityService?.inputText("\n")
            }
        } ?: false
        
        return ExecutionResult(
            success = success,
            message = if (success) "Pressed ${action.button.value} button" else "Failed to press button"
        )
    }
    
    private suspend fun executeWait(): ExecutionResult {
        delay(2000) // Wait 2 seconds for UI to stabilize
        return ExecutionResult(
            success = true,
            message = "Waited for UI to stabilize"
        )
    }
    
    private fun executeTerminate(action: AgentAction.Terminate): ExecutionResult {
        return ExecutionResult(
            success = true,
            message = "Task terminated with status: ${action.status.value}"
        )
    }
    
    private fun executeAnswer(action: AgentAction.Answer): ExecutionResult {
        // Answer action is handled by the UI layer (display to user)
        return ExecutionResult(
            success = true,
            message = "Answer: ${action.text}"
        )
    }
    
    private fun executeAskUser(action: AgentAction.AskUser): ExecutionResult {
        // AskUser action is handled by the UI layer (prompt user for input)
        return ExecutionResult(
            success = true,
            message = "Ask user: ${action.text}"
        )
    }
    
    /**
     * Normalize coordinate from model output [0-999] to actual screen coordinate
     */
    private fun normalizeCoordinate(modelX: Int, modelY: Int): Pair<Int, Int> {
        val screenX = (modelX.toFloat() / SCALE_FACTOR * screenWidth).toInt()
        val screenY = (modelY.toFloat() / SCALE_FACTOR * screenHeight).toInt()
        return Pair(screenX, screenY)
    }
    
    /**
     * Calculate swipe coordinates based on direction
     */
    private fun calculateSwipeCoordinates(action: AgentAction.Swipe): SwipeCoordinates {
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2
        val swipeDistance = screenHeight / 3 // Swipe 1/3 of screen height/width
        
        // If specific coordinate provided, use it as center point
        val (startCenterX, startCenterY) = if (action.x != null && action.y != null) {
            normalizeCoordinate(action.x, action.y)
        } else {
            Pair(centerX, centerY)
        }
        
        return when (action.direction) {
            AgentAction.Swipe.Direction.UP -> SwipeCoordinates(
                startCenterX, startCenterY + swipeDistance / 2,
                startCenterX, startCenterY - swipeDistance / 2
            )
            AgentAction.Swipe.Direction.DOWN -> SwipeCoordinates(
                startCenterX, startCenterY - swipeDistance / 2,
                startCenterX, startCenterY + swipeDistance / 2
            )
            AgentAction.Swipe.Direction.LEFT -> SwipeCoordinates(
                startCenterX + swipeDistance / 2, startCenterY,
                startCenterX - swipeDistance / 2, startCenterY
            )
            AgentAction.Swipe.Direction.RIGHT -> SwipeCoordinates(
                startCenterX - swipeDistance / 2, startCenterY,
                startCenterX + swipeDistance / 2, startCenterY
            )
        }
    }
    
    private data class SwipeCoordinates(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int
    )
}
