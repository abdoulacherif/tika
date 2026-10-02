package com.abdoula.screenrecorder

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var opensValue: TextView
    private lateinit var recordingsValue: TextView
    private lateinit var proValue: TextView
    private lateinit var progress: ProgressBar
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        opensValue = findViewById(R.id.opensValue)
        recordingsValue = findViewById(R.id.recordingsValue)
        proValue = findViewById(R.id.proValue)
        progress = findViewById(R.id.dashboardProgress)

        findViewById<Button>(R.id.refreshButton).setOnClickListener { loadStats() }
        findViewById<Button>(R.id.publishAnnouncementButton).setOnClickListener { publishAnnouncement() }

        loadStats()
        loadCurrentAnnouncement()
    }

    private fun loadStats() {
        progress.visibility = View.VISIBLE
        AnalyticsManager.fetchStats { opens, recordings, proActivations ->
            mainHandler.post {
                progress.visibility = View.GONE
                if (opens == -1) {
                    opensValue.text = "Erreur"
                    recordingsValue.text = "Erreur"
                    proValue.text = "Erreur"
                } else {
                    opensValue.text = opens.toString()
                    recordingsValue.text = recordings.toString()
                    proValue.text = proActivations.toString()
                }
            }
        }
    }

    private fun loadCurrentAnnouncement() {
        AnalyticsManager.fetchAnnouncement { info ->
            mainHandler.post {
                if (info != null) {
                    findViewById<EditText>(R.id.announcementVersionCodeInput).setText(info.versionCode.toString())
                    findViewById<EditText>(R.id.announcementVersionNameInput).setText(info.versionName)
                    findViewById<EditText>(R.id.announcementMessageInput).setText(info.message)
                    findViewById<EditText>(R.id.announcementUrlInput).setText(info.downloadUrl)
                }
            }
        }
    }

    private fun publishAnnouncement() {
        val versionCode = findViewById<EditText>(R.id.announcementVersionCodeInput).text.toString().toLongOrNull()
        val versionName = findViewById<EditText>(R.id.announcementVersionNameInput).text.toString().trim()
        val message = findViewById<EditText>(R.id.announcementMessageInput).text.toString().trim()
        val url = findViewById<EditText>(R.id.announcementUrlInput).text.toString().trim()

        if (versionCode == null) {
            Toast.makeText(this, "Numéro de version invalide", Toast.LENGTH_SHORT).show()
            return
        }

        AnalyticsManager.pushAnnouncement(versionCode, versionName, message, url) { success ->
            mainHandler.post {
                if (success) {
                    Toast.makeText(this, "Annonce publiée ✅", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Échec de la publication", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}