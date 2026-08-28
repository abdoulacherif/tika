package com.abdoula.screenrecorder

import android.content.Context

object SettingsManager {
    private const val PREFS = "app_settings"
    private const val KEY_QUALITY = "video_quality"
    private const val KEY_WATERMARK = "watermark_enabled"

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

    fun getScaleForQuality(quality: String): Double {
        return if (quality == "720") 0.75 else 1.0
    }

    // Filigrane discret affiché sur les enregistrements (version gratuite).
    // Activé par défaut ; désactivable via les réglages (base pour la version Pro).
    fun isWatermarkEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WATERMARK, true)
    }

    fun setWatermarkEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WATERMARK, enabled).apply()
    }
}