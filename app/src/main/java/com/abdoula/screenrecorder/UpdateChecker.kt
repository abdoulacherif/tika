package com.abdoula.screenrecorder

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionName: String, val downloadUrl: String, val changelog: String)

object UpdateChecker {

    // Remplace cette URL par celle de TON fichier version.json une fois créé sur GitHub
    private const val VERSION_URL = "https://raw.githubusercontent.com/abdoulacherif/tika/main/version.json"

    fun checkForUpdate(currentVersionCode: Int, callback: (UpdateInfo?) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val connection = URL(VERSION_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val obj = JSONObject(json)
                val remoteVersionCode = obj.getInt("versionCode")
                val versionName = obj.getString("versionName")
                val downloadUrl = obj.getString("downloadUrl")
                val changelog = obj.optString("changelog", "")

                if (remoteVersionCode > currentVersionCode) {
                    mainHandler.post { callback(UpdateInfo(versionName, downloadUrl, changelog)) }
                } else {
                    mainHandler.post { callback(null) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(null) }
            }
        }.start()
    }
}