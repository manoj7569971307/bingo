package com.example.myapplication

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        var pointer1X = 540
        var pointer1Y = 300
        var pointer2X = 100
        var pointer2Y = 800
        var pointer2Width = 900
        var pointer2Height = 900
        
        fun updateConfig(context: Context) {
            val prefs = context.getSharedPreferences("OCRAutoClick", Context.MODE_PRIVATE)
            pointer1X = prefs.getInt("p1x", 540)
            pointer1Y = prefs.getInt("p1y", 300)
            pointer2X = prefs.getInt("p2x", 100)
            pointer2Y = prefs.getInt("p2y", 800)
            pointer2Width = prefs.getInt("p2w", 900)
            pointer2Height = prefs.getInt("p2h", 900)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, createNotification())
        
        updateConfig(this)
        
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        
        startProjection(resultCode, data)
        startCapture()
        
        return START_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
    }

    private fun startCapture() {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.densityDpi
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (OCRClickService.isRunning) {
                    captureAndProcess()
                    handler.postDelayed(this, 2000) // Capture every 2 seconds
                }
            }
        }, 2000)
    }

    private fun captureAndProcess() {
        val image = imageReader?.acquireLatestImage() ?: return
        
        try {
            val bitmap = imageToBitmap(image)
            
            // Step 1: Read number from pointer 1 location
            val detectedNum = readNumberAtLocation(bitmap, pointer1X, pointer1Y)
            
            if (detectedNum != null) {
                OCRClickService.detectedNumber = detectedNum
                
                // Step 2: Find that number in pointer 2 box and click it
                findAndClickNumber(bitmap, detectedNum)
            }
            
        } finally {
            image.close()
        }
    }

    private fun readNumberAtLocation(bitmap: Bitmap, x: Int, y: Int): String? {
        val cropSize = 100
        val left = maxOf(0, x - cropSize / 2)
        val top = maxOf(0, y - cropSize / 2)
        val right = minOf(bitmap.width, x + cropSize / 2)
        val bottom = minOf(bitmap.height, y + cropSize / 2)
        
        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
        
        var detectedNumber: String? = null
        val latch = CountDownLatch(1)
        
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                for (block in visionText.textBlocks) {
                    val text = block.text.trim()
                    if (text.matches(Regex("[0-9]+"))) {
                        detectedNumber = text
                        break
                    }
                }
                latch.countDown()
            }
            .addOnFailureListener { 
                latch.countDown()
            }
        
        try {
            latch.await()
        } catch (e: InterruptedException) {
            // Handle interruption
        }
        
        return detectedNumber
    }

    private fun findAndClickNumber(bitmap: Bitmap, targetNumber: String) {
        val left = pointer2X
        val top = pointer2Y
        val right = minOf(bitmap.width, left + pointer2Width)
        val bottom = minOf(bitmap.height, top + pointer2Height)
        
        val searchBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val inputImage = InputImage.fromBitmap(searchBitmap, 0)
        
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                for (block in visionText.textBlocks) {
                    if (block.text.trim() == targetNumber) {
                        val clickX = left + (block.boundingBox?.centerX()?.toFloat() ?: 0f)
                        val clickY = top + (block.boundingBox?.centerY()?.toFloat() ?: 0f)
                        
                        OCRClickService.performClick(clickX, clickY)
                        break
                    }
                }
            }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_capture",
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "screen_capture")
            .setContentTitle("OCR Auto Clicker")
            .setContentText("Reading and clicking numbers...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}