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

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var statusDot: android.view.View
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var cameraToggleButton: Button

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

        startButton.setOnClickListener { requestPermissionsThenStart() }

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
                    showComingSoonDialog(
                        "Réglages & Version Pro",
                        "Bientôt : qualité vidéo réglable, export sans filigrane, stockage cloud. Passe à la version Pro pour tout débloquer dès sa sortie."
                    )
                    bottomNav.postDelayed({ bottomNav.selectedItemId = R.id.nav_home }, 150)
                    true
                }
                else -> false
            }
        }
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