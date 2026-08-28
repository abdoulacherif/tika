package com.abdoula.screenrecorder

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var statusDot: android.view.View
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var cameraToggleButton: Button

    private lateinit var statCount: TextView
    private lateinit var statSize: TextView
    private lateinit var statLast: TextView

    private var cameraEnabled = false

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

                    if (cameraEnabled && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        ContextCompat.startForegroundService(this, Intent(this, CameraBubbleService::class.java))
                    }
                }

                updateUiRecording(true)
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
        statusDot = findViewById(R.id.statusDot)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        cameraToggleButton = findViewById(R.id.cameraToggleButton)

        statCount = findViewById(R.id.statCount)
        statSize = findViewById(R.id.statSize)
        statLast = findViewById(R.id.statLast)

        startButton.setOnClickListener { startCountdownThenRecord() }

        stopButton.setOnClickListener {
            stopService(Intent(this, ScreenRecordService::class.java))
            stopService(Intent(this, OverlayDrawingService::class.java))
            stopService(Intent(this, CameraBubbleService::class.java))
            updateUiRecording(false)
        }

        findViewById<Button>(R.id.overlayPermButton).setOnClickListener {
            requestOverlayPermission()
        }

        cameraToggleButton.setOnClickListener { toggleCamera() }

        findViewById<ImageButton>(R.id.menuButton).setOnClickListener { view ->
            showTopMenu(view)
        }

        setupBottomNav()
    }

    // Compte à rebours 3-2-1 avant de demander la permission d'enregistrement,
    // le temps de se positionner (ouvrir l'appli à filmer, etc.)
    private fun startCountdownThenRecord() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            Toast.makeText(this, "Autorise l'affichage par-dessus d'abord, puis relance", Toast.LENGTH_LONG).show()
            return
        }

        val countdownText = TextView(this).apply {
            textSize = 72f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }

        val dialog = AlertDialog.Builder(this)
            .setView(countdownText)
            .setCancelable(false)
            .create()
        dialog.show()

        object : CountDownTimer(3500, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownText.text = if (secondsLeft > 3) "3" else secondsLeft.toString()
            }

            override fun onFinish() {
                dialog.dismiss()
                requestPermissionsThenStart()
            }
        }.start()
    }

    private fun toggleCamera() {
        if (!cameraEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 200)
                return
            }
        }
        cameraEnabled = !cameraEnabled
        cameraToggleButton.text = if (cameraEnabled) "📷 Caméra flottante : activée" else "📷 Caméra flottante : désactivée"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraEnabled = true
            cameraToggleButton.text = "📷 Caméra flottante : activée"
        }
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_gallery -> {
                    startActivity(Intent(this, GalleryActivity::class.java))
                    true
                }
                R.id.nav_tools -> {
                    showComingSoonDialog(
                        "Outils avancés",
                        "Taille du trait, formes supplémentaires, son interne du téléphone… Ces outils arrivent dans la version Pro."
                    )
                    bottomNav.postDelayed({ bottomNav.selectedItemId = R.id.nav_home }, 150)
                    true
                }
                R.id.nav_settings -> {
                    showQualityDialog()
                    bottomNav.postDelayed({ bottomNav.selectedItemId = R.id.nav_home }, 150)
                    true
                }
                else -> false
            }
        }
    }

    private fun showQualityDialog() {
        val current = SettingsManager.getQuality(this)
        val options = arrayOf("720p (fichiers plus légers)", "1080p (qualité maximale)")
        val checkedItem = if (current == "720") 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Qualité d'enregistrement")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val newQuality = if (which == 0) "720" else "1080"
                SettingsManager.setQuality(this, newQuality)
                Toast.makeText(this, "Qualité réglée sur ${options[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun showComingSoonDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showTopMenu(anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("À propos")
        popup.menu.add("Passer à la version Pro")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "À propos" -> showComingSoonDialog("Screen Recorder", "Version 1.0 — développé pour enregistrer et annoter ton écran facilement.")
                "Passer à la version Pro" -> showComingSoonDialog("Version Pro", "Les fonctionnalités payantes arrivent bientôt.")
            }
            true
        }
        popup.show()
    }

    override fun onResume() {
        super.onResume()
        updateUiRecording(ScreenRecordService.isRunning)
        loadStats()
    }

    private fun loadStats() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val files = dir?.listFiles { f -> f.extension == "mp4" } ?: emptyArray()

        statCount.text = files.size.toString()

        val totalBytes = files.sumOf { it.length() }
        val totalMb = totalBytes / (1024 * 1024)
        statSize.text = if (totalMb > 1024) String.format("%.1f Go", totalMb / 1024.0) else "$totalMb Mo"

        val latest = files.maxByOrNull { it.lastModified() }
        statLast.text = if (latest != null) {
            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(latest.lastModified()))
        } else {
            "—"
        }
    }

    private fun updateUiRecording(recording: Boolean) {
        if (recording) {
            statusText.text = "Enregistrement en cours…"
            statusDot.setBackgroundColor(Color.parseColor("#E53935"))
            startButton.isEnabled = false
            startButton.alpha = 0.5f
            stopButton.isEnabled = true
            stopButton.alpha = 1f
        } else {
            statusText.text = "Prêt à enregistrer"
            statusDot.setBackgroundColor(Color.parseColor("#757575"))
            startButton.isEnabled = true
            startButton.alpha = 1f
            stopButton.isEnabled = false
            stopButton.alpha = 0.5f
        }
    }

    private fun requestPermissionsThenStart() {
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