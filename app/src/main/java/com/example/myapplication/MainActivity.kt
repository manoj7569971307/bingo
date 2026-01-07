package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var detectedNumberText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var configButton: Button
    
    private lateinit var pointer1XEdit: EditText
    private lateinit var pointer1YEdit: EditText
    private lateinit var pointer2XEdit: EditText
    private lateinit var pointer2YEdit: EditText
    private lateinit var pointer2WidthEdit: EditText
    private lateinit var pointer2HeightEdit: EditText

    private val SCREEN_CAPTURE_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        loadSavedConfig()
        updateStatus()

        startButton.setOnClickListener {
            if (isAccessibilityServiceEnabled()) {
                requestScreenCapture()
            } else {
                showAccessibilityDialog()
            }
        }

        stopButton.setOnClickListener {
            stopAutoClicker()
        }

        configButton.setOnClickListener {
            saveConfig()
            AlertDialog.Builder(this)
                .setTitle("Configuration Saved")
                .setMessage("Pointer positions updated successfully!")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        detectedNumberText = findViewById(R.id.detectedNumberText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        configButton = findViewById(R.id.configButton)
        
        pointer1XEdit = findViewById(R.id.pointer1XEdit)
        pointer1YEdit = findViewById(R.id.pointer1YEdit)
        pointer2XEdit = findViewById(R.id.pointer2XEdit)
        pointer2YEdit = findViewById(R.id.pointer2YEdit)
        pointer2WidthEdit = findViewById(R.id.pointer2WidthEdit)
        pointer2HeightEdit = findViewById(R.id.pointer2HeightEdit)
    }

    private fun loadSavedConfig() {
        val prefs = getSharedPreferences("OCRAutoClick", MODE_PRIVATE)
        pointer1XEdit.setText(prefs.getInt("p1x", 540).toString())
        pointer1YEdit.setText(prefs.getInt("p1y", 300).toString())
        pointer2XEdit.setText(prefs.getInt("p2x", 100).toString())
        pointer2YEdit.setText(prefs.getInt("p2y", 800).toString())
        pointer2WidthEdit.setText(prefs.getInt("p2w", 900).toString())
        pointer2HeightEdit.setText(prefs.getInt("p2h", 900).toString())
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences("OCRAutoClick", MODE_PRIVATE)
        prefs.edit().apply {
            putInt("p1x", pointer1XEdit.text.toString().toIntOrNull() ?: 540)
            putInt("p1y", pointer1YEdit.text.toString().toIntOrNull() ?: 300)
            putInt("p2x", pointer2XEdit.text.toString().toIntOrNull() ?: 100)
            putInt("p2y", pointer2YEdit.text.toString().toIntOrNull() ?: 800)
            putInt("p2w", pointer2WidthEdit.text.toString().toIntOrNull() ?: 900)
            putInt("p2h", pointer2HeightEdit.text.toString().toIntOrNull() ?: 900)
            apply()
        }
        
        ScreenCaptureService.updateConfig(this)
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java)
                serviceIntent.putExtra("resultCode", resultCode)
                serviceIntent.putExtra("data", data)
                startForegroundService(serviceIntent)
                
                OCRClickService.isRunning = true
                updateStatus()
            }
        }
    }

    private fun stopAutoClicker() {
        OCRClickService.isRunning = false
        stopService(Intent(this, ScreenCaptureService::class.java))
        updateStatus()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/.OCRClickService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage("Please enable the Accessibility Service:\n\n" +
                    "Settings → Accessibility → OCR Auto Clicker → Turn ON")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatus() {
        if (OCRClickService.isRunning) {
            statusText.text = "Status: RUNNING"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            startButton.isEnabled = false
            stopButton.isEnabled = true
        } else {
            statusText.text = "Status: STOPPED"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
        detectedNumberText.text = "Detected: ${OCRClickService.detectedNumber}"
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}