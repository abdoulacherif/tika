package com.abdoula.screenrecorder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CodeGeneratorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_generator)

        val durationGroup = findViewById<RadioGroup>(R.id.durationGroup)
        val validityGroup = findViewById<RadioGroup>(R.id.validityGroup)
        val resultCard = findViewById<android.widget.LinearLayout>(R.id.resultCard)
        val generatedCodeText = findViewById<TextView>(R.id.generatedCodeText)

        findViewById<Button>(R.id.generateButton).setOnClickListener {
            val durationDays = when (durationGroup.checkedRadioButtonId) {
                R.id.dur90 -> 90
                R.id.dur365 -> 365
                else -> 30
            }
            val validForDays = when (validityGroup.checkedRadioButtonId) {
                R.id.valid14 -> 14
                R.id.valid30 -> 30
                else -> 7
            }

            val code = CodeManager.generateCode(durationDays, validForDays)
            generatedCodeText.text = code
            resultCard.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.copyCodeButton).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("code", generatedCodeText.text.toString()))
            Toast.makeText(this, "Code copié", Toast.LENGTH_SHORT).show()
        }
    }
}