package com.abdoula.screenrecorder

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File

// Petit résumé affiché juste après l'arrêt de l'enregistrement, avec des
// boutons de partage rapide — fonctionne même si l'appli n'est pas au premier
// plan, car il s'affiche comme un calque système (même technique que la bulle).
object PostRecordingPopup {

    fun show(context: Context, videoFile: File) {
        if (!videoFile.exists() || videoFile.length() == 0L) return

        val sizeMb = videoFile.length() / (1024 * 1024)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(40, 40, 40, 30)
        }

        container.addView(TextView(context).apply {
            text = "✅ Enregistrement terminé"
            setTextColor(Color.WHITE)
            textSize = 17f
        })

        container.addView(TextView(context).apply {
            text = "${videoFile.name} • $sizeMb Mo"
            setTextColor(Color.parseColor("#B388FF"))
            textSize = 12f
            setPadding(0, 6, 0, 20)
        })

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)

        val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeActionButton(context, "▶️ Voir") {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        })
        row1.addView(makeActionButton(context, "💬 WhatsApp") {
            shareTo(context, uri, "com.whatsapp")
        })
        container.addView(row1)

        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        row2.addView(makeActionButton(context, "✈️ Telegram") {
            shareTo(context, uri, "org.telegram.messenger")
        })
        row2.addView(makeActionButton(context, "🎬 Galerie") {
            val intent = Intent(context, GalleryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        })
        container.addView(row2)

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setNegativeButton("Fermer", null)
            .create()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        dialog.window?.setType(overlayType)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        try {
            dialog.show()
        } catch (e: Exception) {
            // Si l'overlay ne peut pas s'afficher (permission retirée entre-temps), on ignore
        }
    }

    private fun makeActionButton(context: Context, label: String, action: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7C4DFF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            setOnClickListener { action() }
        }
    }

    private fun shareTo(context: Context, uri: Uri, packageName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(packageName)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Application non installée : on ignore silencieusement
        }
    }
}