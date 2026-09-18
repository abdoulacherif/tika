package com.abdoula.screenrecorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

data class RecordingTemplate(
    val title: String,
    val description: String
)

class TemplatesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_templates)

        val templates = buildTemplates()

        val listView = findViewById<ListView>(R.id.templateListView)
        listView.adapter = object : ArrayAdapter<RecordingTemplate>(this, R.layout.item_template, templates) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_template, parent, false)
                val template = templates[position]
                view.findViewById<TextView>(R.id.templateTitle).text = template.title
                view.findViewById<TextView>(R.id.templateDescription).text = template.description
                view.findViewById<Button>(R.id.templateApplyButton).setOnClickListener {
                    applyTemplate(position)
                }
                return view
            }
        }
    }

    private fun applyTemplate(index: Int) {
        when (index) {
            0 -> applyTutoPro()
            1 -> applyReseauxSociaux()
            2 -> applyPresentationLive()
            3 -> applyModeDiscret()
        }
        Toast.makeText(this, "Modèle appliqué ✅", Toast.LENGTH_SHORT).show()
    }

    private fun applyTutoPro() {
        SettingsManager.setResolutionHeight(this, 0)
        SettingsManager.setBitrateMbps(this, 0)
        SettingsManager.setFrameRate(this, 0)
        SettingsManager.setWatermarkEnabled(this, true)
        SettingsManager.setCountdownSeconds(this, 3)
        SettingsManager.setAnnotationDurationMs(this, 1500L)
        SettingsManager.setChronometerEnabled(this, false)
        SettingsManager.setBubbleHiddenDuringRecording(this, false)
    }

    private fun applyReseauxSociaux() {
        SettingsManager.setResolutionHeight(this, 720)
        SettingsManager.setBitrateMbps(this, 6)
        SettingsManager.setFrameRate(this, 30)
        SettingsManager.setCountdownSeconds(this, 3)
        SettingsManager.setAnnotationDurationMs(this, 1000L)
        SettingsManager.setChronometerEnabled(this, false)
        SettingsManager.setBubbleHiddenDuringRecording(this, false)
    }

    private fun applyPresentationLive() {
        SettingsManager.setResolutionHeight(this, 0)
        SettingsManager.setBitrateMbps(this, 0)
        SettingsManager.setFrameRate(this, 0)
        SettingsManager.setChronometerEnabled(this, true)
        SettingsManager.setAnnotationDurationMs(this, 2000L)
        SettingsManager.setCountdownSeconds(this, 5)
        SettingsManager.setBubbleHiddenDuringRecording(this, false)
    }

    private fun applyModeDiscret() {
        SettingsManager.setBubbleHiddenDuringRecording(this, true)
        SettingsManager.setChronometerEnabled(this, false)
        SettingsManager.setAnnotationDurationMs(this, 800L)
        SettingsManager.setCountdownSeconds(this, 0)
        if (SettingsManager.isProUser(this)) {
            SettingsManager.setWatermarkEnabled(this, false)
        }
    }

    private fun buildTemplates(): List<RecordingTemplate> {
        return listOf(
            RecordingTemplate(
                "🎓 Tuto Pro",
                "Qualité automatique, filigrane visible, compte à rebours 3s, annotations bien lisibles (1,5s). Idéal pour des tutoriels clairs et complets."
            ),
            RecordingTemplate(
                "📱 Réseaux sociaux",
                "720p / 6 Mbps pour des fichiers légers et rapides à partager, compte à rebours court, annotations rapides (1s)."
            ),
            RecordingTemplate(
                "🎥 Présentation live",
                "Chronomètre visible à l'écran, compte à rebours 5s pour te préparer, annotations qui restent plus longtemps (2s) pour un public en direct."
            ),
            RecordingTemplate(
                "🤫 Mode discret",
                "Bulle d'outils masquée, pas de compte à rebours, annotations rapides (0,8s)" +
                    if (SettingsManager.isProUser(this)) ", filigrane retiré." else " (le filigrane reste tant que la version Pro n'est pas active)."
            )
        )
    }
}