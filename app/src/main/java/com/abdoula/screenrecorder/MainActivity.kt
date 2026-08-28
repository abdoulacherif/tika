package com.abdoula.screenrecorder

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
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
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
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

        if (intent?.getBooleanExtra("autoStart", false) == true) {
            startCountdownThenRecord()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("autoStart", false)) {
            startCountdownThenRecord()
        }
    }

    private fun startCountdownThenRecord() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            Toast.makeText(this, "Autorise l'affichage par-dessus d'abord, puis relance", Toast.LENGTH_LONG).show()
            return
        }

        val countdownLayout = layoutInflater.inflate(R.layout.dialog_countdown, null)
        val countdownNumber = countdownLayout.findViewById<TextView>(R.id.countdownNumber)

        val dialog = AlertDialog.Builder(this)
            .setView(countdownLayout)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        object : CountDownTimer(3500, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownNumber.text = if (secondsLeft > 3) "3" else secondsLeft.toString()
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
                    showScheduleDialog()
                    bottomNav.postDelayed({ bottomNav.selectedItemId = R.id.nav_home }, 150)
                    true
                }
                R.id.nav_settings -> {
                    showSettingsDialog()
                    bottomNav.postDelayed({ bottomNav.selectedItemId = R.id.nav_home }, 150)
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Enregistrement programmé ----------

    private fun showScheduleDialog() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            scheduleRecording(hour, minute)
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }

    private fun scheduleRecording(hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, RecordingAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
            )
            val timeStr = String.format("%02d:%02d", hour, minute)
            Toast.makeText(this, "Enregistrement programmé à $timeStr", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Autorise les alarmes exactes dans les réglages système pour cette appli", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Réglages (qualité + filigrane) ----------

    private fun showSettingsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val qualityLabel = TextView(this).apply {
            text = "Qualité d'enregistrement"
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        container.addView(qualityLabel)

        val current = SettingsManager.getQuality(this)
        val qualityButton = Button(this).apply {
            text = if (current == "720") "720p (fichiers plus légers)" else "1080p (qualité maximale)"
            setOnClickListener {
                val newQuality = if (SettingsManager.getQuality(this@MainActivity) == "720") "1080" else "720"
                SettingsManager.setQuality(this@MainActivity, newQuality)
                text = if (newQuality == "720") "720p (fichiers plus légers)" else "1080p (qualité maximale)"
            }
        }
        container.addView(qualityButton)

        val watermarkCheck = CheckBox(this).apply {
            text = "Afficher le filigrane Screen Recorder"
            setTextColor(Color.WHITE)
            isChecked = SettingsManager.isWatermarkEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                SettingsManager.setWatermarkEnabled(this@MainActivity, checked)
            }
        }
        container.addView(watermarkCheck)

        AlertDialog.Builder(this)
            .setTitle("Réglages")
            .setView(container)
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun showComingSoonDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------- Menu ☰ ----------

    private fun showTopMenu(anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("⭐ Passer à la version Pro")
        popup.menu.add("🗑️ Vidéos supprimées")
        popup.menu.add("💬 Envoyer un commentaire")
        popup.menu.add("📤 Partager l'application")
        popup.menu.add("❓ Questions fréquentes")
        popup.menu.add("ℹ️ À propos")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "⭐ Passer à la version Pro" -> showComingSoonDialog("Version Pro", "Les fonctionnalités payantes arrivent bientôt.")
                "🗑️ Vidéos supprimées" -> showComingSoonDialog("Vidéos supprimées", "La corbeille (récupération des vidéos supprimées) arrive dans une prochaine version.")
                "💬 Envoyer un commentaire" -> sendFeedbackEmail()
                "📤 Partager l'application" -> shareApp()
                "❓ Questions fréquentes" -> showComingSoonDialog("Questions fréquentes", "Une page d'aide complète arrive bientôt.")
                "ℹ️ À propos" -> showComingSoonDialog("Screen Recorder", "Version 1.0 — développé pour enregistrer et annoter ton écran facilement.")
            }
            true
        }
        popup.show()
    }

    private fun sendFeedbackEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, "Retour sur Screen Recorder")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Aucune appli mail installée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Découvre Screen Recorder, l'appli pour enregistrer et annoter ton écran !")
        }
        startActivity(Intent.createChooser(intent, "Partager l'application"))
    }

    // ---------- Statistiques et état ----------

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