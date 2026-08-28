package com.abdoula.screenrecorder

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class TrimActivity : AppCompatActivity() {

    private lateinit var videoPath: String
    private var durationMs: Long = 0

    private lateinit var startSeekBar: SeekBar
    private lateinit var endSeekBar: SeekBar
    private lateinit var startTimeText: TextView
    private lateinit var endTimeText: TextView
    private lateinit var trimButton: Button
    private lateinit var trimProgress: ProgressBar

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trim)

        videoPath = intent.getStringExtra("videoPath") ?: run {
            finish(); return
        }

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoPath)
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        retriever.release()

        findViewById<TextView>(R.id.durationText).text = "Durée totale : ${formatTime(durationMs)}"

        startSeekBar = findViewById(R.id.startSeekBar)
        endSeekBar = findViewById(R.id.endSeekBar)
        startTimeText = findViewById(R.id.startTimeText)
        endTimeText = findViewById(R.id.endTimeText)
        trimButton = findViewById(R.id.trimConfirmButton)
        trimProgress = findViewById(R.id.trimProgress)

        startSeekBar.max = durationMs.toInt()
        endSeekBar.max = durationMs.toInt()
        endSeekBar.progress = durationMs.toInt()
        endTimeText.text = formatTime(durationMs)

        startSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress >= endSeekBar.progress) {
                    startSeekBar.progress = (endSeekBar.progress - 1000).coerceAtLeast(0)
                }
                startTimeText.text = formatTime(startSeekBar.progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        endSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress <= startSeekBar.progress) {
                    endSeekBar.progress = (startSeekBar.progress + 1000).coerceAtMost(durationMs.toInt())
                }
                endTimeText.text = formatTime(endSeekBar.progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        trimButton.setOnClickListener { startTrim() }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun startTrim() {
        val startUs = startSeekBar.progress * 1000L
        val endUs = endSeekBar.progress * 1000L

        trimButton.isEnabled = false
        trimProgress.visibility = View.VISIBLE

        Thread {
            var success = false
            var outputPath = ""
            try {
                outputPath = buildOutputPath()
                trimVideo(videoPath, outputPath, startUs, endUs)
                success = true
            } catch (e: Exception) {
                success = false
            }

            val finalSuccess = success
            mainHandler.post {
                trimProgress.visibility = View.GONE
                trimButton.isEnabled = true
                if (finalSuccess) {
                    Toast.makeText(this, "Vidéo recadrée enregistrée : ${File(outputPath).name}", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this, "Le recadrage a échoué, réessaie", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun buildOutputPath(): String {
        val original = File(videoPath)
        val timestamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
        return File(original.parent, "${original.nameWithoutExtension}_recadre_$timestamp.mp4").absolutePath
    }

    // Découpe la vidéo sans ré-encodage (copie directe des échantillons) :
    // rapide et sans perte de qualité, mais les coupures s'alignent sur les
    // images-clés les plus proches (précision à ~1 seconde près, suffisant
    // pour un usage tutoriel).
    private fun trimVideo(inputPath: String, outputPath: String, startUs: Long, endUs: Long) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val trackIndexMap = HashMap<Int, Int>()
        val trackCount = extractor.trackCount

        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val dstIndex = muxer.addTrack(format)
            trackIndexMap[i] = dstIndex
            extractor.selectTrack(i)
        }

        muxer.start()

        val bufferSize = 1 * 1024 * 1024
        val buffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = android.media.MediaCodec.BufferInfo()

        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        while (true) {
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime == -1L || sampleTime > endUs) break

            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = sampleTime - startUs
            bufferInfo.flags = extractor.sampleFlags

            val dstTrack = trackIndexMap[trackIndex] ?: continue
            muxer.writeSampleData(dstTrack, buffer, bufferInfo)

            extractor.advance()
        }

        muxer.stop()
        muxer.release()
        extractor.release()
    }
}