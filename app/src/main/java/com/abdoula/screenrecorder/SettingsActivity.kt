package com.abdoula.screenrecorder

import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var resolutionValue: TextView
    private lateinit var bitrateValue: TextView
    private lateinit var frameRateValue: TextView

    private val resolutionOptions = listOf(0, 1080, 720, 640, 540, 480, 360, 240)
    private val bitrateOptions = listOf(0, 16, 14, 12, 10, 8, 6, 4, 2, 1)
    private val frameRateOptions = listOf(0, 60, 50, 40, 30, 25, 20, 15)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        resolutionValue = findViewById(R.id.resolutionValue)
        bitrateValue = findViewById(R.id.bitrateValue)
        frameRateValue = findViewById(R.id.frameRateValue)

        findViewById<LinearLayout>(R.id.resolutionRow).setOnClickListener { showResolutionDialog() }
        findViewById<LinearLayout>(R.id.bitrateRow).setOnClickListener { showBitrateDialog() }
        findViewById<LinearLayout>(R.id.frameRateRow).setOnClickListener { showFrameRateDialog() }

        val watermarkCheck = findViewById<CheckBox>(R.id.watermarkCheck)
        watermarkCheck.isChecked = SettingsManager.isWatermarkEnabled(this)
        watermarkCheck.setOnCheckedChangeListener { _, checked ->
            SettingsManager.setWatermarkEnabled(this, checked)
        }

        refreshLabels()
    }

    private fun refreshLabels() {
        val res = SettingsManager.getResolutionHeight(this)
        resolutionValue.text = if (res == 0) "Automatique (recommandé)" else "${res}p"

        val bitrate = SettingsManager.getBitrateMbps(this)
        bitrateValue.text = if (bitrate == 0) "Automatique (recommandé)" else "$bitrate Mbps"

        val fps = SettingsManager.getFrameRate(this)
        frameRateValue.text = if (fps == 0) "Automatique (30 FPS)" else "$fps FPS"
    }

    private fun showResolutionDialog() {
        val labels = resolutionOptions.map { if (it == 0) "Automatique (recommandé)" else "${it}p" }.toTypedArray()
        val current = resolutionOptions.indexOf(SettingsManager.getResolutionHeight(this))
        AlertDialog.Builder(this)
            .setTitle("Résolution")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                SettingsManager.setResolutionHeight(this, resolutionOptions[which])
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showBitrateDialog() {
        val labels = bitrateOptions.map { if (it == 0) "Automatique (recommandé)" else "$it Mbps" }.toTypedArray()
        val current = bitrateOptions.indexOf(SettingsManager.getBitrateMbps(this))
        AlertDialog.Builder(this)
            .setTitle("Bitrate")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                SettingsManager.setBitrateMbps(this, bitrateOptions[which])
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showFrameRateDialog() {
        val labels = frameRateOptions.map { if (it == 0) "Automatique (recommandé)" else "$it FPS" }.toTypedArray()
        val current = frameRateOptions.indexOf(SettingsManager.getFrameRate(this))
        AlertDialog.Builder(this)
            .setTitle("Fréquence d'images")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                SettingsManager.setFrameRate(this, frameRateOptions[which])
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}