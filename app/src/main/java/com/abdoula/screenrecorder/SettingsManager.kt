package com.abdoula.screenrecorder

import android.content.Context

object SettingsManager {
    private const val PREFS = "app_settings"
    private const val KEY_RESOLUTION_HEIGHT = "resolution_height"
    private const val KEY_BITRATE_MBPS = "bitrate_mbps"
    private const val KEY_FRAMERATE = "frame_rate"
    private const val KEY_WATERMARK = "watermark_enabled"
    private const val KEY_WATERMARK_TEXT = "watermark_text"
    private const val KEY_WATERMARK_LOGO_URI = "watermark_logo_uri"
    private const val KEY_HIDE_BUBBLE = "hide_bubble_recording"
    private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
    private const val KEY_TRASH_ENABLED = "trash_enabled"
    private const val KEY_BUBBLE_POSITION = "bubble_position"
    private const val KEY_IS_PRO = "is_pro_user"
    private const val KEY_POST_RECORDING_POPUP = "post_recording_popup"
    private const val KEY_TRIAL_END_TIME = "trial_end_time"
    private const val KEY_BACKUP_FOLDER_URI = "backup_folder_uri"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version_code"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    private const val KEY_LAST_RECORDING_TIME = "last_recording_time"

    // PIN de l'application
    private const val KEY_PIN_CODE = "app_pin_code"
    private const val KEY_PIN_ENABLED = "app_pin_enabled"

    const val FREE_DURATION_LIMIT_MS = 15 * 60 * 1000L

    fun getResolutionHeight(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_RESOLUTION_HEIGHT, 0)
    }

    fun setResolutionHeight(context: Context, height: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_RESOLUTION_HEIGHT, height).apply()
    }

    fun getBitrateMbps(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_BITRATE_MBPS, 0)
    }

    fun setBitrateMbps(context: Context, mbps: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_BITRATE_MBPS, mbps).apply()
    }

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

    fun getWatermarkText(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(
            KEY_WATERMARK_TEXT,
            "🎬 Écran+"
        ) ?: "🎬 Écran+"
    }

    fun setWatermarkText(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WATERMARK_TEXT, text).apply()
    }

    fun getWatermarkLogoUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WATERMARK_LOGO_URI, null)
    }

    fun setWatermarkLogoUri(context: Context, uri: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WATERMARK_LOGO_URI, uri).apply()
    }

    fun isBubbleHiddenDuringRecording(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_BUBBLE, false)
    }

    fun setBubbleHiddenDuringRecording(
        context: Context,
        hidden: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HIDE_BUBBLE, hidden).apply()
    }

    fun getCountdownSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_COUNTDOWN_SECONDS, 3)

        if (stored == 10 && !isProUser(context))
            return 5

        return stored
    }

    fun setCountdownSeconds(
        context: Context,
        seconds: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_COUNTDOWN_SECONDS, seconds).apply()
    }

    fun isTrashEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TRASH_ENABLED, true)
    }

    fun setTrashEnabled(
        context: Context,
        enabled: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TRASH_ENABLED, enabled).apply()
    }

    fun getBubblePosition(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(
            KEY_BUBBLE_POSITION,
            "top_right"
        ) ?: "top_right"
    }

    fun setBubblePosition(
        context: Context,
        position: String
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUBBLE_POSITION, position).apply()
    }

    private fun isRealProUser(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_PRO, false)
    }

    fun setProUser(
        context: Context,
        isPro: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    fun isProUser(context: Context): Boolean {
        if (isRealProUser(context))
            return true

        if (CodeManager.isCodeProActive(context))
            return true

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trialEnd = prefs.getLong(KEY_TRIAL_END_TIME, 0L)

        return trialEnd > System.currentTimeMillis()
    }

    fun isTrialActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trialEnd = prefs.getLong(KEY_TRIAL_END_TIME, 0L)

        return trialEnd > System.currentTimeMillis()
    }

    fun hasUsedTrial(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return prefs.getLong(
            KEY_TRIAL_END_TIME,
            0L
        ) > 0L
    }

    fun startTrial(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val end = System.currentTimeMillis() +
            (3 * 24 * 60 * 60 * 1000L)

        prefs.edit()
            .putLong(KEY_TRIAL_END_TIME, end)
            .apply()
    }

    fun getTrialRemainingHours(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val trialEnd = prefs.getLong(
            KEY_TRIAL_END_TIME,
            0L
        )

        val remaining =
            trialEnd - System.currentTimeMillis()

        return if (remaining > 0)
            remaining / (60 * 60 * 1000L)
        else
            0L
    }

    fun isPostRecordingPopupEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(
            KEY_POST_RECORDING_POPUP,
            true
        )
    }

    fun setPostRecordingPopupEnabled(
        context: Context,
        enabled: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_POST_RECORDING_POPUP, enabled)
            .apply()
    }

    fun getBackupFolderUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(
            KEY_BACKUP_FOLDER_URI,
            null
        )
    }

    fun setBackupFolderUri(
        context: Context,
        uri: String?
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_BACKUP_FOLDER_URI, uri)
            .apply()
    }

    fun getLastSeenVersionCode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return prefs.getInt(
            KEY_LAST_SEEN_VERSION,
            0
        )
    }

    fun setLastSeenVersionCode(
        context: Context,
        versionCode: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        prefs.edit()
            .putInt(KEY_LAST_SEEN_VERSION, versionCode)
            .apply()
    }

    fun areRemindersEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return prefs.getBoolean(
            KEY_REMINDERS_ENABLED,
            true
        )
    }

    fun setRemindersEnabled(
        context: Context,
        enabled: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        prefs.edit()
            .putBoolean(KEY_REMINDERS_ENABLED, enabled)
            .apply()
    }

    fun setLastRecordingTime(
        context: Context,
        timeMs: Long
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        prefs.edit()
            .putLong(KEY_LAST_RECORDING_TIME, timeMs)
            .apply()
    }

    fun getLastRecordingTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return prefs.getLong(
            KEY_LAST_RECORDING_TIME,
            0L
        )
    }

    // ---------- PIN ----------

    fun isPinEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        return prefs.getBoolean(
            KEY_PIN_ENABLED,
            false
        )
    }

    fun setPin(
        context: Context,
        pin: String
    ) {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        prefs.edit()
            .putString(KEY_PIN_CODE, pin)
            .putBoolean(KEY_PIN_ENABLED, true)
            .apply()
    }

    fun disablePin(context: Context) {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        prefs.edit()
            .putBoolean(KEY_PIN_ENABLED, false)
            .apply()
    }

    fun checkPin(
        context: Context,
        pin: String
    ): Boolean {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        return prefs.getString(
            KEY_PIN_CODE,
            null
        ) == pin
    }

    // ---------- Résolution / qualité ----------

    fun resolveBitrate(
        context: Context,
        effectiveHeight: Int
    ): Int {
        val chosen = getBitrateMbps(context)

        if (chosen > 0)
            return chosen * 1_000_000

        return when {
            effectiveHeight >= 1080 -> 8_000_000
            effectiveHeight >= 720 -> 5_000_000
            effectiveHeight >= 480 -> 2_500_000
            else -> 1_200_000
        }
    }

    fun resolveFrameRate(context: Context): Int {
        val chosen = getFrameRate(context)

        return if (chosen > 0)
            chosen
        else
            30
    }

    fun resolveResolutionHeight(context: Context): Int {
        val stored = getResolutionHeight(context)

        if (stored == 1080 && !isProUser(context))
            return 720

        return stored
    }
}