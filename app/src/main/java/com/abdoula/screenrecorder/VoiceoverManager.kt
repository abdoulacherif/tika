package com.abdoula.screenrecorder

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale

// Génère une voix off à partir d'un texte tapé, avec le moteur vocal natif
// d'Android (gratuit, aucune clé API). Le fichier audio produit peut ensuite
// remplacer ou se mélanger avec le son de la vidéo.
//
// Pour rendre cette fonctionnalité payante plus tard, ajoute au tout début
// de generateSpeech() :
//   if (!SettingsManager.isProUser(context)) { onResult(null); return }
// et affiche le dialogue Pro habituel côté appelant (comme pour les autres
// fonctionnalités Pro de l'appli).
class VoiceoverManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    fun initialize(onReady: (success: Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.FRENCH
            }
            onReady(isReady)
        }
    }

    fun generateSpeech(text: String, outputFile: File, onResult: (File?) -> Unit) {
        val engine = tts
        if (engine == null || !isReady) {
            onResult(null)
            return
        }

        val utteranceId = "voiceover_${System.currentTimeMillis()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) onResult(outputFile)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) onResult(null)
            }
        })

        val params = Bundle()
        val result = engine.synthesizeToFile(text, params, outputFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) onResult(null)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
    }
}