package com.example.offlineai.agent;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.example.offlineai.LogManager;
import com.example.offlineai.agent.core.AgentEngine;
import com.example.offlineai.agent.model.AgentAction;
import com.example.offlineai.agent.model.ExecutionResult;
import com.example.offlineai.agent.parser.ActionParser;
import com.example.offlineai.agent.utils.AccessibilityPermissionHelper;

import org.jetbrains.annotations.NotNull;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

/**
 * Agent Manager - Java bridge for Agent functionality
 * Provides easy-to-use Java API for RagQaFragment integration
 */
public class AgentManager {
    
    private static final String TAG = "AgentManager";
    
    private final Context context;
    private final AgentEngine engine;
    private final Handler mainHandler;
    
    private AgentCallback callback;
    
    /**
     * Callback interface for agent events
     */
    public interface AgentCallback {
        void onAgentActionDetected(String thinking, String actionType);
        void onAgentActionCompleted(boolean success, String message);
        void onAgentError(String error);
        void onAgentAnswer(String text);
    }
    
    public AgentManager(Context context) {
        this.context = context.getApplicationContext();
        this.engine = new AgentEngine(this.context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Set engine callback
        engine.setCallback(new AgentEngine.AgentCallback() {
            @Override
            public void onStepStarted(int stepIndex, @NotNull String thinking) {
                LogManager.logI(TAG, "Agent step " + stepIndex + " started: " + thinking);
            }
            
            @Override
            public void onStepCompleted(int stepIndex, @NotNull AgentAction action, @NotNull ExecutionResult result) {
                LogManager.logI(TAG, "Agent step " + stepIndex + " completed: " + result.getMessage());
                if (callback != null) {
                    callback.onAgentActionCompleted(result.getSuccess(), result.getMessage());
                }
            }
            
            @Override
            public void onTaskCompleted(boolean success, @NotNull String message) {
                LogManager.logI(TAG, "Agent task completed: " + message);
                if (callback != null) {
                    callback.onAgentActionCompleted(success, message);
                }
            }
            
            @Override
            public void onError(@NotNull String error) {
                LogManager.logE(TAG, "Agent error: " + error);
                if (callback != null) {
                    callback.onAgentError(error);
                }
            }
            
            @Override
            public void onAnswer(@NotNull String text) {
                LogManager.logI(TAG, "Agent answer: " + text);
                if (callback != null) {
                    callback.onAgentAnswer(text);
                }
            }
            
            @Override
            public void onAskUser(@NotNull String question, @NotNull kotlin.jvm.functions.Function1<? super String, Unit> callback) {
                // This would require UI interaction, not implemented in this version
                LogManager.logI(TAG, "Agent asking user: " + question);
            }
        });
    }
    
    /**
     * Set callback for agent events
     */
    public void setCallback(AgentCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Check if accessibility service is enabled
     */
    public boolean isAccessibilityServiceEnabled() {
        return AccessibilityPermissionHelper.INSTANCE.isAccessibilityServiceEnabled(context);
    }
    
    /**
     * Open accessibility settings
     */
    public void openAccessibilitySettings() {
        AccessibilityPermissionHelper.INSTANCE.openAccessibilitySettings(context);
    }
    
    /**
     * Get instructions for enabling accessibility service
     */
    public String getEnableInstructions() {
        return AccessibilityPermissionHelper.INSTANCE.getEnableInstructions();
    }
    
    /**
     * Check if model output contains agent action
     */
    public boolean containsAgentAction(String modelOutput) {
        return ActionParser.INSTANCE.containsAgentAction(modelOutput);
    }
    
    /**
     * Execute agent action from model output
     * This is called when <tool_call> is detected in streaming output
     */
    public void executeFromModelOutput(final String taskGoal, final String modelOutput, final Bitmap screenshot) {
        if (!isAccessibilityServiceEnabled()) {
            LogManager.logE(TAG, "Accessibility service not enabled");
            if (callback != null) {
                mainHandler.post(() -> callback.onAgentError("需要开启无障碍服务才能使用Agent功能"));
            }
            return;
        }
        
        LogManager.logI(TAG, "Executing agent from model output");
        
        // Execute in background thread
        new Thread(() -> {
            try {
                // Create a simple continuation for suspend function
                Continuation<ExecutionResult> continuation = new Continuation<ExecutionResult>() {
                    @NotNull
                    @Override
                    public CoroutineContext getContext() {
                        return EmptyCoroutineContext.INSTANCE;
                    }

                    @Override
                    public void resumeWith(@NotNull Object o) {
                        if (o instanceof ExecutionResult) {
                            ExecutionResult result = (ExecutionResult) o;
                            LogManager.logI(TAG, "Agent execution result: " + result.getMessage());
                        }
                    }
                };
                
                // Call suspend function
                Object result = engine.executeFromModelOutput(taskGoal, modelOutput, screenshot, continuation);
                
                // Handle result if it's not COROUTINE_SUSPENDED
                if (result instanceof ExecutionResult) {
                    ExecutionResult execResult = (ExecutionResult) result;
                    LogManager.logI(TAG, "Agent execution completed: " + execResult.getMessage());
                }
                
            } catch (Exception e) {
                LogManager.logE(TAG, "Agent execution failed: " + e.getMessage());
                e.printStackTrace();
                if (callback != null) {
                    mainHandler.post(() -> callback.onAgentError("执行失败: " + e.getMessage()));
                }
            }
        }).start();
    }
    
    /**
     * Stop current agent execution
     */
    public void stop() {
        engine.stop();
    }
    
    /**
     * Release resources
     */
    public void release() {
        engine.release();
    }
}
