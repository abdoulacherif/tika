package com.abdoula.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView

    private val screenCaptureLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                ContextCompat.startForegroundService(this, serviceIntent)

                if (Settings.canDrawOverlays(this)) {
                    startService(Intent(this, OverlayDrawingService::class.java))
                }

                statusText.text = "Enregistrement en cours…"
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "Permission d'enregistrement refusée", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.startButton).setOnClickListener {
            requestPermissionsThenStart()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, ScreenRecordService::class.java))
            stopService(Intent(this, OverlayDrawingService::class.java))
            statusText.text = "Arrêté"
        }

        findViewById<Button>(R.id.overlayPermButton).setOnClickListener {
            requestOverlayPermission()
        }
    }

    private fun requestPermissionsThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            Toast.makeText(this, "Autorise l'affichage par-dessus d'abord, puis relance", Toast.LENGTH_LONG).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}