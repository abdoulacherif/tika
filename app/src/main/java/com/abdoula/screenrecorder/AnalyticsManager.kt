package com.abdoula.screenrecorder

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AnnouncementInfo(
    val versionCode: Long,
    val versionName: String,
    val message: String,
    val downloadUrl: String
)

object AnalyticsManager {

    private const val SUPABASE_URL = "https://dwfecbladynxlaryxkcj.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR3ZmVjYmxhZHlueGxhcnl4a2NqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgzMTI2OTYsImV4cCI6MjEwMzg4ODY5Nn0.B8NHywjkkr9hGMcXMHBixAGj4mfOEbkAiKZWFOzemlQ"

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    fun logEvent(context: Context, eventType: String) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("event_type", eventType)
                    put("device_id", getDeviceId(context))
                    put("app_version", BuildConfig.VERSION_NAME)
                }

                val url = URL("$SUPABASE_URL/rest/v1/analytics_events")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(json.toString().toByteArray()) }
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
            }
        }.start()
    }

    fun fetchStats(callback: (opens: Int, recordings: Int, proActivations: Int) -> Unit) {
        Thread {
            try {
                val url = URL("$SUPABASE_URL/rest/v1/analytics_events?select=event_type&limit=5000&order=created_at.desc")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val array = JSONArray(response)
                var opens = 0; var recordings = 0; var proActivations = 0
                for (i in 0 until array.length()) {
                    when (array.getJSONObject(i).optString("event_type")) {
                        "app_open" -> opens++
                        "recording_completed" -> recordings++
                        "pro_activated" -> proActivations++
                    }
                }
                callback(opens, recordings, proActivations)
            } catch (e: Exception) {
                callback(-1, -1, -1)
            }
        }.start()
    }

    fun logLocation(context: Context, latitude: Double, longitude: Double, callback: (success: Boolean) -> Unit) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("device_id", getDeviceId(context))
                    put("latitude", latitude)
                    put("longitude", longitude)
                }

                val url = URL("$SUPABASE_URL/rest/v1/device_locations")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8000
                connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Prefer", "resolution=merge-duplicates")
                connection.doOutput = true
                connection.outputStream.use { it.write(json.toString().toByteArray()) }
                val code = connection.responseCode
                connection.disconnect()
                callback(code in 200..299)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    // Lit l'annonce actuelle pilotée depuis le tableau de bord admin — aucun
    // besoin de recompiler l'appli pour changer ce message.
    fun fetchAnnouncement(callback: (AnnouncementInfo?) -> Unit) {
        Thread {
            try {
                val url = URL("$SUPABASE_URL/rest/v1/app_announcement?id=eq.1&select=*")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val array = JSONArray(response)
                if (array.length() == 0) { callback(null); return@Thread }
                val obj = array.getJSONObject(0)
                callback(
                    AnnouncementInfo(
                        versionCode = obj.optLong("version_code", 0L),
                        versionName = obj.optString("version_name", ""),
                        message = obj.optString("message", ""),
                        downloadUrl = obj.optString("download_url", "")
                    )
                )
            } catch (e: Exception) {
                callback(null)
            }
        }.start()
    }

    // Réservé à l'admin (écran du tableau de bord) : met à jour l'annonce
    // visible par tous les utilisateurs.
    fun pushAnnouncement(versionCode: Long, versionName: String, message: String, downloadUrl: String, callback: (success: Boolean) -> Unit) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("version_code", versionCode)
                    put("version_name", versionName)
                    put("message", message)
                    put("download_url", downloadUrl)
                }

                val url = URL("$SUPABASE_URL/rest/v1/app_announcement?id=eq.1")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PATCH"
                connection.connectTimeout = 8000
                connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.requestMethod = "PATCH"
                connection.outputStream.use { it.write(json.toString().toByteArray()) }
                val code = connection.responseCode
                connection.disconnect()
                callback(code in 200..299)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }
}