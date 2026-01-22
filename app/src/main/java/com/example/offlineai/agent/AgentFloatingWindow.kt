package com.example.offlineai.agent.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.example.offlineai.LogManager
import com.example.offlineai.R

/**
 * Floating window to display Agent execution status
 * Shows real-time Agent state without interrupting the target app
 */
class AgentFloatingWindow(private val context: Context) {
    
    companion object {
        private const val TAG = "AgentFloatingWindow"
    }
    
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var isShowing = false
    private var isMinimized = false
    
    // UI components
    private var textViewTask: TextView? = null
    private var textViewStep: TextView? = null
    private var textViewStatus: TextView? = null
    private var buttonMinimize: ImageButton? = null
    private var buttonStop: ImageButton? = null
    private var layoutExpanded: LinearLayout? = null
    private var layoutMinimized: LinearLayout? = null
    
    // Callbacks
    private var onStopClickListener: (() -> Unit)? = null
    
    // Drag support
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) {
            LogManager.logD(TAG, "Floating window already showing")
            return
        }
        
        try {
            // Inflate layout
            val inflater = LayoutInflater.from(context)
            floatingView = inflater.inflate(R.layout.agent_floating_window, null)
            
            // Initialize UI components
            textViewTask = floatingView?.findViewById(R.id.textViewTask)
            textViewStep = floatingView?.findViewById(R.id.textViewStep)
            textViewStatus = floatingView?.findViewById(R.id.textViewStatus)
            buttonMinimize = floatingView?.findViewById(R.id.buttonMinimize)
            buttonStop = floatingView?.findViewById(R.id.buttonStop)
            layoutExpanded = floatingView?.findViewById(R.id.layoutExpanded)
            layoutMinimized = floatingView?.findViewById(R.id.layoutMinimized)
            
            // Setup window parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 100
            params.y = 100
            
            // Add view to window
            windowManager.addView(floatingView, params)
            isShowing = true
            
            // Setup button listeners
            buttonMinimize?.setOnClickListener {
                toggleMinimize()
            }
            
            buttonStop?.setOnClickListener {
                onStopClickListener?.invoke()
            }
            
            // Setup drag listener
            floatingView?.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        true
                    }
                    else -> false
                }
            }
            
            // Setup minimized view click listener
            layoutMinimized?.setOnClickListener {
                toggleMinimize()
            }
            
            LogManager.logI(TAG, "Floating window shown")
        } catch (e: Exception) {
            LogManager.logE(TAG, "Failed to show floating window", e)
        }
    }
    
    fun hide() {
        if (!isShowing) {
            return
        }
        
        try {
            floatingView?.let { windowManager.removeView(it) }
            floatingView = null
            isShowing = false
            LogManager.logI(TAG, "Floating window hidden")
        } catch (e: Exception) {
            LogManager.logE(TAG, "Failed to hide floating window", e)
        }
    }
    
    fun updateTask(task: String) {
        textViewTask?.text = task
    }
    
    fun updateStep(currentStep: Int, maxSteps: Int) {
        textViewStep?.text = "Step: $currentStep/$maxSteps"
        // Update minimized view step count
        layoutMinimized?.findViewById<TextView>(R.id.textViewMinimizedStep)?.text = currentStep.toString()
    }
    
    fun updateStatus(status: String) {
        textViewStatus?.text = status
    }
    
    fun setOnStopClickListener(listener: () -> Unit) {
        onStopClickListener = listener
    }
    
    private fun toggleMinimize() {
        isMinimized = !isMinimized
        if (isMinimized) {
            layoutExpanded?.visibility = View.GONE
            layoutMinimized?.visibility = View.VISIBLE
        } else {
            layoutExpanded?.visibility = View.VISIBLE
            layoutMinimized?.visibility = View.GONE
        }
        LogManager.logD(TAG, "Floating window ${if (isMinimized) "minimized" else "expanded"}")
    }
}
