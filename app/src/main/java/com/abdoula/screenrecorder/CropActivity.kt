package com.abdoula.screenrecorder

import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class CropActivity : AppCompatActivity() {

    private lateinit var videoPath: String
    private var videoWidth = 0
    private var videoHeight = 0
    private lateinit var overlay: CropOverlayView
    private lateinit var previewImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        videoPath = intent.getStringExtra("videoPath") ?: run { finish(); return }
        overlay = findViewById(R.id.cropOverlayView)
        previewImage = findViewById(R.id.cropPreviewImage)

        loadPreviewFrame()

        findViewById<Button>(R.id.applyCropButton).setOnClickListener { applyCrop() }
    }

    private fun loadPreviewFrame() {
        Thread {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            val bitmap = retriever.getFrameAtTime(0)
            videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            retriever.release()

            runOnUiThread {
                if (bitmap != null) {
                    previewImage.setImageBitmap(bitmap)
                    previewImage.post {
                        val w = previewImage.width.toFloat()
                        val h = previewImage.height.toFloat()
                        overlay.cropRect = RectF(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.85f)
                        overlay.invalidate()
                    }
                }
            }
        }.start()
    }

    private fun applyCrop() {
        if (videoWidth == 0 || videoHeight == 0) return

        val imgW = previewImage.width.toFloat()
        val imgH = previewImage.height.toFloat()
        val rect = overlay.cropRect

        val uMin = (rect.left / imgW).coerceIn(0f, 1f)
        val uMax = (rect.right / imgW).coerceIn(0f, 1f)
        val vMin = (rect.top / imgH).coerceIn(0f, 1f)
        val vMax = (rect.bottom / imgH).coerceIn(0f, 1f)

        val outWidth = ((uMax - uMin) * videoWidth).toInt().coerceAtLeast(64) / 2 * 2
        val outHeight = ((vMax - vMin) * videoHeight).toInt().coerceAtLeast(64) / 2 * 2

        val progress = findViewById<ProgressBar>(R.id.cropProgress)
        progress.visibility = View.VISIBLE
        findViewById<Button>(R.id.applyCropButton).isEnabled = false

        Thread {
            val original = File(videoPath)
            val outputFile = File(original.parent, "${original.nameWithoutExtension}_recadre.mp4")
            val success = VideoReformatter.cropToRegion(videoPath, outputFile.absolutePath, uMin, uMax, vMin, vMax, outWidth, outHeight)

            runOnUiThread {
                progress.visibility = View.GONE
                if (success) {
                    Toast.makeText(this, "Recadrage terminé : ${outputFile.name}", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    findViewById<Button>(R.id.applyCropButton).isEnabled = true
                    Toast.makeText(this, "Le recadrage a échoué, réessaie", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}