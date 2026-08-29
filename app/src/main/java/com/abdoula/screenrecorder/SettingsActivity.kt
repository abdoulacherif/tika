package com.abdoula.screenrecorder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var resolutionValue: TextView
    private lateinit var bitrateValue: TextView
    private lateinit var frameRateValue: TextView
    private lateinit var countdownValue: TextView
    private lateinit var bubblePositionValue: TextView
    private lateinit var watermarkTextInput: EditText

    private val resolutionOptions = listOf(0, 1080, 720, 640, 540, 480, 360, 240)
    private val bitrateOptions = listOf(0, 16, 14, 12, 10, 8, 6, 4, 2, 1)
    private val frameRateOptions = listOf(0, 60, 50, 40, 30, 25, 20, 15)
    private val countdownOptions = listOf(0, 1, 2, 3, 5, 10)
    private val bubblePositions = listOf("top_left", "top_right", "bottom_left", "bottom_right")
    private val bubblePositionLabels = listOf("Haut à gauche", "Haut à droite", "Bas à gauche", "Bas à droite")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        resolutionValue = findViewById(R.id.resolutionValue)
        bitrateValue = findViewById(R.id.bitrateValue)
        frameRateValue = findViewById(R.id.frameRateValue)
        countdownValue = findViewById(R.id.countdownValue)
        bubblePositionValue = findViewById(R.id.bubblePositionValue)
        watermarkTextInput = findViewById(R.id.watermarkTextInput)

        findViewById<LinearLayout>(R.id.resolutionRow).setOnClickListener { showResolutionDialog() }
        findViewById<LinearLayout>(R.id.bitrateRow).setOnClickListener { showBitrateDialog() }
        findViewById<LinearLayout>(R.id.frameRateRow).setOnClickListener { showFrameRateDialog() }
        findViewById<LinearLayout>(R.id.countdownRow).setOnClickListener { showCountdownDialog() }
        findViewById<LinearLayout>(R.id.bubblePositionRow).setOnClickListener { showBubblePositionDialog() }
        findViewById<LinearLayout>(R.id.batteryRow).setOnClickListener { requestBatteryExemption() }
        findViewById<LinearLayout>(R.id.showTapsRow).setOnClickListener { openDeveloperOptions() }

        val watermarkCheck = findViewById<CheckBox>(R.id.watermarkCheck)
        watermarkCheck.isChecked = SettingsManager.isWatermarkEnabled(this)
        watermarkCheck.setOnCheckedChangeListener { _, checked ->
            if (!checked && !SettingsManager.isProUser(this)) {
                // Retirer le filigrane est réservé à la version Pro
                watermarkCheck.isChecked = true
                AlertDialog.Builder(this)
                    .setTitle("💎 Fonctionnalité Pro")
                    .setMessage("Retirer le filigrane est réservé à la version Pro. Toutes les autres fonctionnalités restent gratuites et illimitées.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                SettingsManager.setWatermarkEnabled(this, checked)
            }
        }

        watermarkTextInput.setText(SettingsManager.getWatermarkText(this))

        val hideBubbleCheck = findViewById<CheckBox>(R.id.hideBubbleCheck)
        hideBubbleCheck.isChecked = SettingsManager.isBubbleHiddenDuringRecording(this)
        hideBubbleCheck.setOnCheckedChangeListener { _, checked ->
            SettingsManager.setBubbleHiddenDuringRecording(this, checked)
        }

        val trashCheck = findViewById<CheckBox>(R.id.trashCheck)
        trashCheck.isChecked = SettingsManager.isTrashEnabled(this)
        trashCheck.setOnCheckedChangeListener { _, checked ->
            SettingsManager.setTrashEnabled(this, checked)
        }

        refreshLabels()
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Impossible d'ouvrir ce réglage sur ce téléphone", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Active d'abord le mode développeur (7 taps sur le numéro de build dans À propos du téléphone)", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshLabels() {
        val res = SettingsManager.getResolutionHeight(this)
        resolutionValue.text = if (res == 0) "Automatique (recommandé)" else "${res}p"

        val bitrate = SettingsManager.getBitrateMbps(this)
        bitrateValue.text = if (bitrate == 0) "Automatique (recommandé)" else "$bitrate Mbps"

        val fps = SettingsManager.getFrameRate(this)
        frameRateValue.text = if (fps == 0) "Automatique (30 FPS)" else "$fps FPS"

        val countdown = SettingsManager.getCountdownSeconds(this)
        countdownValue.text = if (countdown == 0) "Aucun" else "${countdown}s"

        val position = SettingsManager.getBubblePosition(this)
        val index = bubblePositions.indexOf(position).coerceAtLeast(0)
        bubblePositionValue.text = bubblePositionLabels[index]
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

    private fun showCountdownDialog() {
        val labels = countdownOptions.map { if (it == 0) "Aucun" else "${it}s" }.toTypedArray()
        val current = countdownOptions.indexOf(SettingsManager.getCountdownSeconds(this))
        AlertDialog.Builder(this)
            .setTitle("Compte à rebours")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                SettingsManager.setCountdownSeconds(this, countdownOptions[which])
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showBubblePositionDialog() {
        val current = bubblePositions.indexOf(SettingsManager.getBubblePosition(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Position du bouton magique")
            .setSingleChoiceItems(bubblePositionLabels.toTypedArray(), current) { dialog, which ->
                SettingsManager.setBubblePosition(this, bubblePositions[which])
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        val text = watermarkTextInput.text.toString().trim()
        if (text.isNotEmpty()) SettingsManager.setWatermarkText(this, text)
    }
}