package com.abdoula.screenrecorder

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RedeemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_redeem)

        val codeInput = findViewById<EditText>(R.id.codeInput)
        val resultText = findViewById<TextView>(R.id.redeemResultText)

        findViewById<Button>(R.id.redeemButton).setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isEmpty()) return@setOnClickListener

            when (val result = CodeManager.redeem(this, code)) {
                is CodeManager.RedeemResult.Success -> {
                    AnalyticsManager.logEvent(this, "pro_activated")
                    resultText.setTextColor(Color.parseColor("#4CAF50"))
                    resultText.text = "✅ Pro activé pour ${result.durationDays} jours !"
                }
                CodeManager.RedeemResult.Expired -> {
                    resultText.setTextColor(Color.parseColor("#E53935"))
                    resultText.text = "⏰ Ce code a expiré"
                }
                CodeManager.RedeemResult.AlreadyUsed -> {
                    resultText.setTextColor(Color.parseColor("#E53935"))
                    resultText.text = "⚠️ Ce code a déjà été utilisé sur cet appareil"
                }
                CodeManager.RedeemResult.InvalidFormat -> {
                    resultText.setTextColor(Color.parseColor("#E53935"))
                    resultText.text = "❌ Code invalide, vérifie la saisie"
                }
            }
        }
    }
}