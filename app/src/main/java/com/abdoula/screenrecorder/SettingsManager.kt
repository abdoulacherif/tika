package com.abdoula.screenrecorder

import android.content.Context

// Centralise tous les réglages de l'appli (qualité vidéo, etc.)
// pour que les autres écrans/services puissent les lire facilement.
object SettingsManager {
    private const val PREFS = "app_settings"
    private const val KEY_QUALITY = "video_quality"

    // Valeurs possibles : "720", "1080"
    fun getQuality(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_QUALITY, "1080") ?: "1080"
    }

    fun setQuality(context: Context, quality: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_QUALITY, quality).apply()
    }

    fun getBitrateForQuality(quality: String): Int {
        return if (quality == "720") 5_000_000 else 8_000_000
    }

    // Facteur d'échelle appliqué à la résolution réelle de l'écran
    fun getScaleForQuality(quality: String): Double {
        return if (quality == "720") 0.75 else 1.0
    }
}