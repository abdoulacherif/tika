package com.abdoula.screenrecorder

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Locale

data class SubtitleEntry(val startMs: Int, val text: String)

class SubtitleActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var playPauseButton: ImageButton
    private lateinit var currentTimeText: TextView
    private lateinit var listView: ListView
    private lateinit var videoPath: String

    private val entries = mutableListOf<SubtitleEntry>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPlaying = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (videoView.isPlaying) {
                currentTimeText.text = formatTime(videoView.currentPosition)
            }
            mainHandler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subtitle)

        videoPath = intent.getStringExtra("videoPath") ?: run { finish(); return }

        videoView = findViewById(R.id.videoView)
        playPauseButton = findViewById(R.id.playPauseButton)
        currentTimeText = findViewById(R.id.currentTimeText)
        listView = findViewById(R.id.subtitleListView)

        videoView.setVideoURI(Uri.fromFile(File(videoPath)))
        videoView.setOnPreparedListener { it.isLooping = false }

        playPauseButton.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                playPauseButton.setImageResource(R.drawable.ic_play)
            } else {
                videoView.start()
                playPauseButton.setImageResource(R.drawable.ic_stop)
            }
        }

        findViewById<Button>(R.id.addSubtitleButton).setOnClickListener {
            showAddSubtitleDialog()
        }

        findViewById<Button>(R.id.saveSubtitlesButton).setOnClickListener {
            saveSrtFile()
        }

        refreshList()
        mainHandler.post(tickRunnable)
    }

    private fun showAddSubtitleDialog() {
        val wasPlaying = videoView.isPlaying
        videoView.pause()
        val pausedAtMs = videoView.currentPosition

        val input = EditText(this).apply { hint = "Texte du sous-titre…" }

        AlertDialog.Builder(this)
            .setTitle("Sous-titre à ${formatTime(pausedAtMs)}")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    entries.add(SubtitleEntry(pausedAtMs, text))
                    entries.sortBy { it.startMs }
                    refreshList()
                }
                if (wasPlaying) videoView.start()
            }
            .setNegativeButton("Annuler") { _, _ -> if (wasPlaying) videoView.start() }
            .show()
    }

    private fun refreshList() {
        listView.adapter = object : ArrayAdapter<SubtitleEntry>(this, R.layout.item_subtitle, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_subtitle, parent, false)
                val entry = entries[position]
                view.findViewById<TextView>(R.id.subtitleTime).text = formatTime(entry.startMs)
                view.findViewById<TextView>(R.id.subtitleText).text = entry.text
                view.findViewById<ImageButton>(R.id.subtitleDeleteButton).setOnClickListener {
                    entries.removeAt(position)
                    refreshList()
                }
                return view
            }
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun formatSrtTime(ms: Int): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, millis)
    }

    private fun saveSrtFile() {
        if (entries.isEmpty()) {
            Toast.makeText(this, "Ajoute au moins un sous-titre d'abord", Toast.LENGTH_SHORT).show()
            return
        }

        val videoFile = File(videoPath)
        val srtFile = File(videoFile.parent, "${videoFile.nameWithoutExtension}.srt")

        val builder = StringBuilder()
        entries.forEachIndexed { index, entry ->
            val endMs = if (index < entries.size - 1) entries[index + 1].startMs else entry.startMs + 4000
            builder.append("${index + 1}\n")
            builder.append("${formatSrtTime(entry.startMs)} --> ${formatSrtTime(endMs)}\n")
            builder.append("${entry.text}\n\n")
        }

        srtFile.writeText(builder.toString())
        Toast.makeText(this, "Sous-titres enregistrés : ${srtFile.name}", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(tickRunnable)
    }
}