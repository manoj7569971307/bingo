package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class OCRClickService : AccessibilityService() {

    companion object {
        var isRunning = false
        var detectedNumber = "None"
        var serviceInstance: OCRClickService? = null
        
        fun performClick(x: Float, y: Float) {
            serviceInstance?.let { service ->
                val path = Path()
                path.moveTo(x, y)
                
                val gestureBuilder = GestureDescription.Builder()
                gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                
                val gesture = gestureBuilder.build()
                service.dispatchGesture(gesture, null, null)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for this implementation
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
    }
}