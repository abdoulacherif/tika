package com.abdoula.screenrecorder

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AppLockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)

        val pinInput = findViewById<EditText>(R.id.pinInput)
        val errorText = findViewById<TextView>(R.id.lockErrorText)

        findViewById<Button>(R.id.unlockButton).setOnClickListener {
            val entered = pinInput.text.toString()
            if (SettingsManager.checkPin(this, entered)) {
                AppLockState.unlocked = true
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                errorText.text = "Code incorrect, réessaie"
                pinInput.text.clear()
            }
        }
    }

    override fun onBackPressed() {
        // On empêche de contourner le verrouillage en revenant en arrière —
        // ça renvoie simplement à l'écran d'accueil du téléphone.
        moveTaskToBack(true)
    }
}