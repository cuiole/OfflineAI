package com.example.offlineai.agent.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.offlineai.LogManager
import java.nio.ByteBuffer

/**
 * Screenshot Capture - captures screen for agent vision
 * Reuses MediaProjection from existing screenshot functionality
 */
class ScreenshotCapture(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenshotCapture"
    }
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    
    private val screenWidth: Int
    private val screenHeight: Int
    private val screenDensity: Int
    
    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi
        
        LogManager.logI(TAG, "Screen: ${screenWidth}x${screenHeight}, density: $screenDensity")
    }
    
    /**
     * Initialize MediaProjection (requires user permission)
     */
    fun initMediaProjection(resultCode: Int, data: android.content.Intent) {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
            as MediaProjectionManager
        
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        LogManager.logI(TAG, "MediaProjection initialized")
    }
    
    /**
     * Capture current screen as Bitmap
     */
    fun captureScreen(): Bitmap? {
        if (mediaProjection == null) {
            LogManager.logE(TAG, "MediaProjection not initialized")
            return null
        }
        
        try {
            // Create ImageReader
            if (imageReader == null) {
                imageReader = ImageReader.newInstance(
                    screenWidth, 
                    screenHeight, 
                    PixelFormat.RGBA_8888, 
                    2
                )
            }
            
            // Create VirtualDisplay
            if (virtualDisplay == null) {
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "AgentScreenCapture",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    0,
                    imageReader?.surface,
                    null,
                    null
                )
            }
            
            // Wait a bit for the display to render
            Thread.sleep(100)
            
            // Acquire latest image
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                LogManager.logW(TAG, "No image available")
                return null
            }
            
            val bitmap = imageToBitmap(image)
            image.close()
            
            LogManager.logD(TAG, "Screenshot captured: ${bitmap?.width}x${bitmap?.height}")
            return bitmap
            
        } catch (e: Exception) {
            LogManager.logE(TAG, "Failed to capture screenshot: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Convert Image to Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        
        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        
        bitmap.copyPixelsFromBuffer(buffer)
        
        // Crop to actual screen size if there's padding
        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } else {
            bitmap
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        LogManager.logI(TAG, "Screenshot capture released")
    }
}
