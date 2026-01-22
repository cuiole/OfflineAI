package com.example.offlineai.agent.core

import android.content.Context
import android.graphics.Bitmap
import com.example.offlineai.LogManager
import com.example.offlineai.agent.executor.ActionExecutor
import com.example.offlineai.agent.model.AgentAction
import com.example.offlineai.agent.model.AgentResponse
import com.example.offlineai.agent.model.ExecutionResult
import com.example.offlineai.agent.model.TrajectoryMemory
import com.example.offlineai.agent.model.TrajectoryStep
import com.example.offlineai.agent.parser.ActionParser
import com.example.offlineai.agent.service.AgentAccessibilityService
import com.example.offlineai.agent.utils.ScreenshotCapture
import kotlinx.coroutines.*

/**
 * Agent Engine - core orchestrator for MAI-UI agent execution
 * Manages the agent execution loop: screenshot → model inference → action execution
 */
class AgentEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "AgentEngine"
        private const val MAX_STEPS = 50 // Maximum steps to prevent infinite loops
        private const val STEP_DELAY_MS = 1500L // Delay between steps for UI stabilization
    }
    
    private val executor = ActionExecutor(context)
    private val memory = TrajectoryMemory(maxHistorySteps = 3)
    private var screenshotCapture = ScreenshotCapture(context)
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var shouldStop = false
    
    private var currentJob: Job? = null
    
    /**
     * Callback interface for agent execution events
     */
    interface AgentCallback {
        fun onStepStarted(stepIndex: Int, thinking: String)
        fun onStepCompleted(stepIndex: Int, action: AgentAction, result: ExecutionResult)
        fun onTaskCompleted(success: Boolean, message: String)
        fun onError(error: String)
        fun onAnswer(text: String)
        fun onAskUser(question: String, callback: (String) -> Unit)
    }
    
    private var callback: AgentCallback? = null
    
    fun setCallback(callback: AgentCallback?) {
        this.callback = callback
    }
    
    /**
     * Set ScreenshotCapture instance (for sharing initialized MediaProjection)
     */
    fun setScreenshotCapture(capture: ScreenshotCapture) {
        this.screenshotCapture = capture
        LogManager.logI(TAG, "ScreenshotCapture instance set")
    }
    
    /**
     * Initialize MediaProjection for screenshot capture
     */
    fun initScreenCapture(resultCode: Int, data: android.content.Intent) {
        screenshotCapture.initMediaProjection(resultCode, data)
    }
    
    /**
     * Check if accessibility service is available
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return AgentAccessibilityService.isServiceEnabled()
    }
    
    /**
     * Execute agent task based on model streaming output
     * This is called when <tool_call> is detected in model output
     */
    suspend fun executeFromModelOutput(
        taskGoal: String,
        modelOutput: String,
        screenshot: Bitmap?
    ): ExecutionResult = withContext(Dispatchers.IO) {
        
        if (!isAccessibilityServiceEnabled()) {
            return@withContext ExecutionResult(
                success = false,
                message = "Accessibility service not enabled"
            )
        }
        
        LogManager.logI(TAG, "Executing agent action from model output")
        LogManager.logD(TAG, "Task goal: $taskGoal")
        LogManager.logD(TAG, "Model output: $modelOutput")
        
        // Parse model output
        val response = ActionParser.parse(modelOutput)
        if (response == null) {
            LogManager.logE(TAG, "Failed to parse model output")
            return@withContext ExecutionResult(
                success = false,
                message = "Failed to parse agent action"
            )
        }
        
        LogManager.logI(TAG, "Parsed action: ${response.action.javaClass.simpleName}")
        LogManager.logD(TAG, "Thinking: ${response.thinking}")
        
        // Handle special actions that don't require execution
        when (response.action) {
            is AgentAction.Answer -> {
                callback?.onAnswer(response.action.text)
                return@withContext ExecutionResult(
                    success = true,
                    message = "Answer: ${response.action.text}"
                )
            }
            is AgentAction.AskUser -> {
                // This will be handled by callback
                return@withContext ExecutionResult(
                    success = true,
                    message = "Ask user: ${response.action.text}"
                )
            }
            is AgentAction.Terminate -> {
                val success = response.action.status == AgentAction.Terminate.Status.SUCCESS
                callback?.onTaskCompleted(success, "Task terminated: ${response.action.status.value}")
                return@withContext ExecutionResult(
                    success = success,
                    message = "Task terminated: ${response.action.status.value}"
                )
            }
            else -> {
                // Execute the action
                val result = executor.execute(response.action)
                
                // Store in memory
                val step = TrajectoryStep(
                    stepIndex = memory.getStepCount(),
                    screenshot = screenshot,
                    thinking = response.thinking,
                    action = response.action,
                    executionResult = result
                )
                memory.addStep(step)
                
                callback?.onStepCompleted(step.stepIndex, response.action, result)
                
                return@withContext result
            }
        }
    }
    
    /**
     * Execute a complete agent task (autonomous multi-step execution)
     * This is for future use when we want fully autonomous agent execution
     */
    suspend fun executeTask(
        taskGoal: String,
        modelInferenceCallback: suspend (instruction: String, screenshot: Bitmap?, history: List<TrajectoryStep>) -> String
    ) = withContext(Dispatchers.IO) {
        
        if (isRunning) {
            LogManager.logW(TAG, "Agent is already running")
            return@withContext
        }
        
        if (!isAccessibilityServiceEnabled()) {
            callback?.onError("Accessibility service not enabled")
            return@withContext
        }
        
        // Set accessibility service to active state
        AgentAccessibilityService.getInstance()?.setAgentActive(true)
        
        isRunning = true
        shouldStop = false
        memory.clear()
        memory.setTaskGoal(taskGoal)
        
        LogManager.logI(TAG, "Starting agent task: $taskGoal")
        
        currentJob = launch {
            try {
                var stepIndex = 0
                
                while (stepIndex < MAX_STEPS && !shouldStop) {
                    LogManager.logI(TAG, "Step $stepIndex: Capturing screenshot...")
                    
                    // Capture screenshot
                    val screenshot = screenshotCapture.captureScreen()
                    if (screenshot == null) {
                        callback?.onError("Failed to capture screenshot")
                        break
                    }
                    
                    // First step: skip screenshot for faster inference, only use task instruction
                    // Screenshot is still captured to verify MediaProjection works
                    val screenshotForInference = if (stepIndex == 0) {
                        LogManager.logI(TAG, "Step 0: Skipping screenshot for LLM (using task instruction only for faster inference)")
                        null
                    } else {
                        screenshot
                    }
                    
                    // Get model inference
                    LogManager.logI(TAG, "Step $stepIndex: Requesting model inference (screenshot=${screenshotForInference != null})...")
                    val modelOutput = modelInferenceCallback(
                        taskGoal,
                        screenshotForInference,
                        memory.getRecentSteps()
                    )
                    
                    // Parse response
                    val response = ActionParser.parse(modelOutput)
                    if (response == null) {
                        LogManager.logE(TAG, "Failed to parse model output at step $stepIndex")
                        callback?.onError("Failed to parse model output")
                        break
                    }
                    
                    LogManager.logI(TAG, "Step $stepIndex: Action=${response.action.javaClass.simpleName}")
                    callback?.onStepStarted(stepIndex, response.thinking)
                    
                    // Handle special actions
                    when (response.action) {
                        is AgentAction.Terminate -> {
                            val success = response.action.status == AgentAction.Terminate.Status.SUCCESS
                            LogManager.logI(TAG, "Task terminated: ${response.action.status.value}")
                            callback?.onTaskCompleted(success, "Task completed")
                            break
                        }
                        is AgentAction.Answer -> {
                            callback?.onAnswer(response.action.text)
                            break
                        }
                        is AgentAction.AskUser -> {
                            // Wait for user response (this would need to be handled differently)
                            LogManager.logI(TAG, "Agent asking user: ${response.action.text}")
                            callback?.onAskUser(response.action.text) { _userResponse ->
                                // Continue with user response
                            }
                            break
                        }
                        else -> {
                            // Execute action
                            val result = executor.execute(response.action)
                            
                            // Store step
                            val step = TrajectoryStep(
                                stepIndex = stepIndex,
                                screenshot = screenshot,
                                thinking = response.thinking,
                                action = response.action,
                                executionResult = result
                            )
                            memory.addStep(step)
                            
                            callback?.onStepCompleted(stepIndex, response.action, result)
                            
                            if (!result.success) {
                                LogManager.logE(TAG, "Action failed: ${result.message}")
                                // Continue anyway, let model decide what to do
                            }
                        }
                    }
                    
                    // Wait for UI to stabilize
                    delay(STEP_DELAY_MS)
                    stepIndex++
                }
                
                if (stepIndex >= MAX_STEPS) {
                    LogManager.logW(TAG, "Reached maximum steps limit")
                    callback?.onTaskCompleted(false, "Reached maximum steps limit")
                }
                
            } catch (e: Exception) {
                LogManager.logE(TAG, "Agent execution error", e)
                e.printStackTrace()
                callback?.onError("Execution error: ${e.message}")
            } finally {
                isRunning = false
                // Deactivate accessibility service
                AgentAccessibilityService.getInstance()?.setAgentActive(false)
                LogManager.logI(TAG, "Agent task completed")
            }
        }
    }
    
    /**
     * Stop agent execution
     */
    fun stop() {
        LogManager.logI(TAG, "Stopping agent execution")
        shouldStop = true
        currentJob?.cancel()
        isRunning = false
        // Deactivate accessibility service
        AgentAccessibilityService.getInstance()?.setAgentActive(false)
    }
    
    /**
     * Get current task status
     */
    fun getTaskStatus(): String {
        return if (isRunning) {
            "执行中 (${memory.getStepCount()} 步)"
        } else {
            memory.getTaskStatus()
        }
    }
    
    /**
     * Get execution history
     */
    fun getHistory(): List<TrajectoryStep> {
        return memory.getAllSteps()
    }
    
    /**
     * Clear execution history
     */
    fun clearHistory() {
        memory.clear()
    }
    
    /**
     * Release resources
     */
    fun release() {
        stop()
        screenshotCapture.release()
        LogManager.logI(TAG, "Agent engine released")
    }
}
