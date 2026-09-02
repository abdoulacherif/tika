package com.abdoula.screenrecorder

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
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

        loadStats()
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
}