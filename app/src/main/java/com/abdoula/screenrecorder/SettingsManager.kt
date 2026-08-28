package com.abdoula.screenrecorder

import android.content.Context

object SettingsManager {
    private const val PREFS = "app_settings"
    private const val KEY_RESOLUTION_HEIGHT = "resolution_height"
    private const val KEY_BITRATE_MBPS = "bitrate_mbps"
    private const val KEY_FRAMERATE = "frame_rate"
    private const val KEY_WATERMARK = "watermark_enabled"

    // 0 = Automatique (résolution native de l'écran)
    fun getResolutionHeight(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_RESOLUTION_HEIGHT, 0)
    }

    fun setResolutionHeight(context: Context, height: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_RESOLUTION_HEIGHT, height).apply()
    }

    // 0 = Automatique (calculé selon la résolution choisie)
    fun getBitrateMbps(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_BITRATE_MBPS, 0)
    }

    fun setBitrateMbps(context: Context, mbps: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_BITRATE_MBPS, mbps).apply()
    }

    // 0 = Automatique (30 FPS)
    fun getFrameRate(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FRAMERATE, 0)
    }

    fun setFrameRate(context: Context, fps: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FRAMERATE, fps).apply()
    }

    fun isWatermarkEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WATERMARK, true)
    }

    fun setWatermarkEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WATERMARK, enabled).apply()
    }

    // Calcule le bitrate effectif : la valeur choisie, ou une valeur par
    // défaut cohérente avec la résolution si réglé sur Automatique.
    fun resolveBitrate(context: Context, effectiveHeight: Int): Int {
        val chosen = getBitrateMbps(context)
        if (chosen > 0) return chosen * 1_000_000
        return when {
            effectiveHeight >= 1080 -> 8_000_000
            effectiveHeight >= 720 -> 5_000_000
            effectiveHeight >= 480 -> 2_500_000
            else -> 1_200_000
        }
    }

    fun resolveFrameRate(context: Context): Int {
        val chosen = getFrameRate(context)
        return if (chosen > 0) chosen else 30
    }
}