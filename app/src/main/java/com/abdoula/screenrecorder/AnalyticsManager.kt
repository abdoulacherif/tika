package com.abdoula.screenrecorder

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AnalyticsManager {

    private const val SUPABASE_URL = "https://dwfecbladynxlaryxkcj.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR3ZmVjYmxhZHlueGxhcnl4a2NqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgzMTI2OTYsImV4cCI6MjEwMzg4ODY5Nn0.B8NHywjkkr9hGMcXMHBixAGj4mfOEbkAiKZWFOzemlQ"

    fun logEvent(context: Context, eventType: String) {
        Thread {
            try {
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
                val json = JSONObject().apply {
                    put("event_type", eventType)
                    put("device_id", deviceId)
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
}