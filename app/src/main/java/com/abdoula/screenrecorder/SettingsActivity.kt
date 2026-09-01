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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var resolutionValue: TextView
    private lateinit var bitrateValue: TextView
    private lateinit var frameRateValue: TextView
    private lateinit var countdownValue: TextView
    private lateinit var bubblePositionValue: TextView
    private lateinit var watermarkTextInput: EditText
    private lateinit var backupFolderValue: TextView
    private lateinit var trialTitle: TextView
    private lateinit var trialSubtitle: TextView
    private lateinit var trialButton: android.widget.Button

    private val resolutionOptions = listOf(0, 1080, 720, 640, 540, 480, 360, 240)
    private val bitrateOptions = listOf(0, 16, 14, 12, 10, 8, 6, 4, 2, 1)
    private val frameRateOptions = listOf(0, 60, 50, 40, 30, 25, 20, 15)
    private val countdownOptions = listOf(0, 1, 2, 3, 5, 10)
    private val bubblePositions = listOf("top_left", "top_right", "bottom_left", "bottom_right")
    private val bubblePositionLabels = listOf("Haut à gauche", "Haut à droite", "Bas à gauche", "Bas à droite")

    private val logoPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (!SettingsManager.isProUser(this)) {
                showProDialog("Utiliser un logo personnalisé est réservé à la version Pro.")
            } else {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                SettingsManager.setWatermarkLogoUri(this, uri.toString())
                Toast.makeText(this, "Logo enregistré", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val backupFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (!SettingsManager.isProUser(this)) {
                showProDialog("La sauvegarde automatique est réservée à la version Pro.")
            } else {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                SettingsManager.setBackupFolderUri(this, uri.toString())
                refreshLabels()
                Toast.makeText(this, "Dossier de sauvegarde défini", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        resolutionValue = findViewById(R.id.resolutionValue)
        bitrateValue = findViewById(R.id.bitrateValue)
        frameRateValue = findViewById(R.id.frameRateValue)
        countdownValue = findViewById(R.id.countdownValue)
        bubblePositionValue = findViewById(R.id.bubblePositionValue)
        watermarkTextInput = findViewById(R.id.watermarkTextInput)
        backupFolderValue = findViewById(R.id.backupFolderValue)
        trialTitle = findViewById(R.id.trialTitle)
        trialSubtitle = findViewById(R.id.trialSubtitle)
        trialButton = findViewById(R.id.trialButton)

        findViewById<LinearLayout>(R.id.resolutionRow).setOnClickListener { showResolutionDialog() }
        findViewById<LinearLayout>(R.id.bitrateRow).setOnClickListener { showBitrateDialog() }
        findViewById<LinearLayout>(R.id.frameRateRow).setOnClickListener { showFrameRateDialog() }
        findViewById<LinearLayout>(R.id.countdownRow).setOnClickListener { showCountdownDialog() }
        findViewById<LinearLayout>(R.id.bubblePositionRow).setOnClickListener { showBubblePositionDialog() }
        findViewById<LinearLayout>(R.id.batteryRow).setOnClickListener { requestBatteryExemption() }
        findViewById<LinearLayout>(R.id.showTapsRow).setOnClickListener { openDeveloperOptions() }
        findViewById<LinearLayout>(R.id.backupFolderRow).setOnClickListener { backupFolderLauncher.launch(null) }
        findViewById<android.widget.Button>(R.id.logoPickerButton).setOnClickListener {
            logoPickerLauncher.launch(arrayOf("image/*"))
        }
        findViewById<android.widget.Button>(R.id.redeemCodeButton).setOnClickListener {
            startActivity(Intent(this, RedeemActivity::class.java))
        }

        trialButton.setOnClickListener { startTrialOrShowStatus() }

        val watermarkCheck = findViewById<CheckBox>(R.id.watermarkCheck)
        watermarkCheck.isChecked = SettingsManager.isWatermarkEnabled(this)
        watermarkCheck.setOnCheckedChangeListener { _, checked ->
            if (!checked && !SettingsManager.isProUser(this)) {
                watermarkCheck.isChecked = true
                showProDialog("Retirer le filigrane est réservé à la version Pro.")
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

        val popupCheck = findViewById<CheckBox>(R.id.popupCheck)
        popupCheck.isChecked = SettingsManager.isPostRecordingPopupEnabled(this)
        popupCheck.setOnCheckedChangeListener { _, checked ->
            if (!checked && !SettingsManager.isProUser(this)) {
                popupCheck.isChecked = true
                showProDialog("Masquer le résumé après l'enregistrement est réservé à la version Pro.")
            } else {
                SettingsManager.setPostRecordingPopupEnabled(this, checked)
            }
        }

        val remindersCheck = findViewById<CheckBox>(R.id.remindersCheck)
        remindersCheck.isChecked = SettingsManager.areRemindersEnabled(this)
        remindersCheck.setOnCheckedChangeListener { _, checked ->
            SettingsManager.setRemindersEnabled(this, checked)
        }

        refreshLabels()
        refreshTrialCard()
    }

    private fun startTrialOrShowStatus() {
        if (SettingsManager.isProUser(this) && SettingsManager.isTrialActive(this)) {
            val hours = SettingsManager.getTrialRemainingHours(this)
            Toast.makeText(this, "Il te reste environ $hours h d'essai Pro", Toast.LENGTH_LONG).show()
            return
        }
        if (SettingsManager.hasUsedTrial(this)) {
            Toast.makeText(this, "Tu as déjà utilisé ton essai gratuit", Toast.LENGTH_LONG).show()
            return
        }
        SettingsManager.startTrial(this)
        Toast.makeText(this, "Essai Pro activé pour 3 jours 🎉", Toast.LENGTH_LONG).show()
        refreshTrialCard()
    }

    private fun refreshTrialCard() {
        when {
            SettingsManager.isTrialActive(this) -> {
                val hours = SettingsManager.getTrialRemainingHours(this)
                trialTitle.text = "💎 Essai Pro actif"
                trialSubtitle.text = "Il te reste environ $hours heures"
                trialButton.text = "Voir mon essai"
            }
            CodeManager.isCodeProActive(this) -> {
                val days = CodeManager.getRemainingDays(this)
                trialTitle.text = "💎 Pro actif (code)"
                trialSubtitle.text = "Il te reste $days jour(s)"
                trialButton.text = "Voir mon essai"
            }
            SettingsManager.hasUsedTrial(this) -> {
                trialTitle.text = "💎 Essai déjà utilisé"
                trialSubtitle.text = "Ton essai gratuit de 3 jours est terminé"
                trialButton.text = "Essai terminé"
            }
            else -> {
                trialTitle.text = "💎 Essaie la version Pro"
                trialSubtitle.text = "3 jours gratuits, sans engagement"
                trialButton.text = "Commencer l'essai gratuit"
            }
        }
    }

    private fun showProDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("💎 Fonctionnalité Pro")
            .setMessage("$message Toutes les autres fonctionnalités restent gratuites et illimitées.")
            .setPositiveButton("OK", null)
            .show()
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
        resolutionValue.text = if (res == 0) "Automatique (recommandé)" else "${res}p" + if (res == 1080) " 💎" else ""

        val bitrate = SettingsManager.getBitrateMbps(this)
        bitrateValue.text = if (bitrate == 0) "Automatique (recommandé)" else "$bitrate Mbps"

        val fps = SettingsManager.getFrameRate(this)
        frameRateValue.text = if (fps == 0) "Automatique (30 FPS)" else "$fps FPS"

        val countdown = SettingsManager.getCountdownSeconds(this)
        countdownValue.text = if (countdown == 0) "Aucun" else "${countdown}s"

        val position = SettingsManager.getBubblePosition(this)
        val index = bubblePositions.indexOf(position).coerceAtLeast(0)
        bubblePositionValue.text = bubblePositionLabels[index]

        val backupFolder = SettingsManager.getBackupFolderUri(this)
        backupFolderValue.text = if (backupFolder != null) "Dossier configuré ✅" else "Aucun dossier choisi"
    }

    private fun showResolutionDialog() {
        val labels = resolutionOptions.map {
            when {
                it == 0 -> "Automatique (recommandé)"
                it == 1080 -> "1080p 💎 Pro"
                else -> "${it}p"
            }
        }.toTypedArray()
        val current = resolutionOptions.indexOf(SettingsManager.getResolutionHeight(this))
        AlertDialog.Builder(this)
            .setTitle("Résolution")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val chosen = resolutionOptions[which]
                if (chosen == 1080 && !SettingsManager.isProUser(this)) {
                    dialog.dismiss()
                    showProDialog("La résolution 1080p est réservée à la version Pro.")
                } else {
                    SettingsManager.setResolutionHeight(this, chosen)
                    refreshLabels()
                    dialog.dismiss()
                }
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
        val labels = countdownOptions.map { if (it == 0) "Aucun" else if (it == 10) "10s 💎 Pro" else "${it}s" }.toTypedArray()
        val current = countdownOptions.indexOf(SettingsManager.getCountdownSeconds(this))
        AlertDialog.Builder(this)
            .setTitle("Compte à rebours")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val chosen = countdownOptions[which]
                if (chosen == 10 && !SettingsManager.isProUser(this)) {
                    dialog.dismiss()
                    showProDialog("Le compte à rebours de 10s est réservé à la version Pro.")
                } else {
                    SettingsManager.setCountdownSeconds(this, chosen)
                    refreshLabels()
                    dialog.dismiss()
                }
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

    override fun onResume() {
        super.onResume()
        refreshTrialCard()
    }

    override fun onPause() {
        super.onPause()
        val text = watermarkTextInput.text.toString().trim()
        if (text.isNotEmpty()) SettingsManager.setWatermarkText(this, text)
    }
}