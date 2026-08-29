package com.abdoula.screenrecorder

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    // Change ce code si tu veux, garde-le pour toi uniquement.
    private val secretPin = "260826"
    private var tapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val versionText = findViewById<TextView>(R.id.versionText)
        versionText?.setOnClickListener {
            tapCount++
            if (tapCount >= 7) {
                tapCount = 0
                showPinDialog()
            }
        }
    }

    private fun showPinDialog() {
        val input = EditText(this).apply {
            hint = "Code secret"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Accès générateur")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == secretPin) {
                    startActivity(Intent(this, CodeGeneratorActivity::class.java))
                } else {
                    Toast.makeText(this, "Code incorrect", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}