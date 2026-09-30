package com.abdoula.screenrecorder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.net.Inet4Address
import java.net.NetworkInterface

class WifiTransferActivity : AppCompatActivity() {

    private var server: LocalTransferServer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var linkUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_transfer)

        val videoPath = intent.getStringExtra("videoPath")
        if (videoPath == null) { finish(); return }

        val ip = getLocalIpAddress()
        if (ip == null) {
            findViewById<TextView>(R.id.statusText).text = "Connecte-toi d'abord à un réseau Wifi"
            return
        }

        val port = 8080
        linkUrl = "http://$ip:$port/"

        findViewById<TextView>(R.id.linkText).text = linkUrl
        showQrCode(linkUrl)

        findViewById<Button>(R.id.copyLinkButton).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("lien", linkUrl))
            Toast.makeText(this, "Lien copié", Toast.LENGTH_SHORT).show()
        }

        server = LocalTransferServer(videoPath, port).apply {
            onTransferStarted = {
                mainHandler.post { updateStatus("Transfert en cours…", "#FFC107") }
            }
            onTransferCompleted = {
                mainHandler.post { updateStatus("✅ Transfert terminé !", "#4CAF50") }
            }
            onTransferFailed = {
                mainHandler.post { updateStatus("⚠️ Le transfert a échoué, réessaie", "#E53935") }
            }
            start()
        }

        updateStatus("En attente d'un appareil…", "#757575")
    }

    private fun updateStatus(text: String, colorHex: String) {
        findViewById<TextView>(R.id.statusText).text = text
        findViewById<android.view.View>(R.id.statusDot).setBackgroundColor(Color.parseColor(colorHex))
    }

    private fun showQrCode(text: String) {
        try {
            val size = 600
            val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            findViewById<ImageView>(R.id.qrImageView).setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible de générer le QR code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }
}