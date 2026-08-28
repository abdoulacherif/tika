package com.abdoula.screenrecorder

import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GalleryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var selectModeButton: TextView
    private lateinit var mergeBar: LinearLayout
    private lateinit var selectionCountText: TextView
    private val mainHandler = Handler(Looper.getMainLooper())

    private var selectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    private var pendingMusicTargetFile: File? = null

    private val musicPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingMusicTargetFile
        if (uri != null && target != null) {
            applyMusic(target, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        listView = findViewById(R.id.videoListView)
        emptyText = findViewById(R.id.emptyText)
        selectModeButton = findViewById(R.id.selectModeButton)
        mergeBar = findViewById(R.id.mergeBar)
        selectionCountText = findViewById(R.id.selectionCountText)

        selectModeButton.setOnClickListener { toggleSelectionMode() }
        findViewById<android.widget.Button>(R.id.mergeConfirmButton).setOnClickListener { confirmMerge() }

        loadVideos()
    }

    private fun toggleSelectionMode() {
        selectionMode = !selectionMode
        selectedFiles.clear()
        selectModeButton.text = if (selectionMode) "Annuler" else "Sélectionner"
        mergeBar.visibility = if (selectionMode) View.VISIBLE else View.GONE
        updateSelectionCount()
        loadVideos()
    }

    private fun updateSelectionCount() {
        selectionCountText.text = "${selectedFiles.size} sélectionnée(s)"
    }

    private fun confirmMerge() {
        if (selectedFiles.size < 2) {
            Toast.makeText(this, "Choisis au moins 2 vidéos à fusionner", Toast.LENGTH_SHORT).show()
            return
        }

        val orderedFiles = selectedFiles.sortedBy { it.lastModified() }
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Fusion en cours…")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val timestamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
            val outputFile = File(dir, "fusion_$timestamp.mp4")
            val success = VideoMerger.merge(orderedFiles.map { it.absolutePath }, outputFile.absolutePath)

            mainHandler.post {
                dialog.dismiss()
                if (success) {
                    Toast.makeText(this, "Vidéos fusionnées : ${outputFile.name}", Toast.LENGTH_LONG).show()
                    toggleSelectionMode()
                } else {
                    Toast.makeText(this, "La fusion a échoué (vidéos de formats différents ?)", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun applyMusic(videoFile: File, musicUri: Uri) {
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Ajout de la musique…")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val outputFile = File(videoFile.parent, "${videoFile.nameWithoutExtension}_musique.mp4")
            val success = AudioReplacer.replaceAudio(this, videoFile.absolutePath, musicUri, outputFile.absolutePath)

            mainHandler.post {
                dialog.dismiss()
                if (success) {
                    Toast.makeText(this, "Musique ajoutée : ${outputFile.name}", Toast.LENGTH_LONG).show()
                    loadVideos()
                } else {
                    Toast.makeText(this, "Impossible d'ajouter cette musique", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun amplifyAudio(file: File) {
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Amplification du son…")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val outputFile = File(file.parent, "${file.nameWithoutExtension}_fort.mp4")
            val success = AudioAmplifier.amplify(file.absolutePath, outputFile.absolutePath)

            mainHandler.post {
                dialog.dismiss()
                if (success) {
                    Toast.makeText(this, "Son amplifié : ${outputFile.name}", Toast.LENGTH_LONG).show()
                    loadVideos()
                } else {
                    Toast.makeText(this, "Impossible d'amplifier cette vidéo", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun loadVideos() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val files = dir?.listFiles { f -> f.extension == "mp4" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (files.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            listView.visibility = View.GONE
            return
        }

        emptyText.visibility = View.GONE
        listView.visibility = View.VISIBLE

        listView.adapter = VideoAdapter(files)
    }

    private inner class VideoAdapter(private val files: List<File>) :
        ArrayAdapter<File>(this@GalleryActivity, R.layout.item_video, files) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_video, parent, false)

            val file = files[position]
            view.findViewById<TextView>(R.id.fileName).text = file.name

            val thumbView = view.findViewById<ImageView>(R.id.thumbnail)
            thumbView.setImageBitmap(null)
            thumbView.tag = file.absolutePath
            loadThumbnailAsync(file, thumbView)

            val checkIcon = view.findViewById<ImageView>(R.id.checkIcon)
            checkIcon.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkIcon.alpha = if (selectedFiles.contains(file)) 1f else 0.25f

            val uri: Uri = FileProvider.getUriForFile(
                this@GalleryActivity, "$packageName.fileprovider", file
            )

            view.findViewById<LinearLayout>(R.id.itemClickArea).setOnClickListener {
                if (selectionMode) {
                    if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
                    updateSelectionCount()
                    notifyDataSetChanged()
                } else {
                    playVideo(uri)
                }
            }

            view.findViewById<ImageButton>(R.id.subtitleButton).setOnClickListener {
                val intent = Intent(this@GalleryActivity, SubtitleActivity::class.java)
                intent.putExtra("videoPath", file.absolutePath)
                startActivity(intent)
            }

            view.findViewById<ImageButton>(R.id.musicButton).setOnClickListener {
                pendingMusicTargetFile = file
                musicPickerLauncher.launch(arrayOf("audio/*"))
            }

            view.findViewById<ImageButton>(R.id.amplifyButton).setOnClickListener {
                amplifyAudio(file)
            }

            view.findViewById<ImageButton>(R.id.compressButton).setOnClickListener {
                compressAndShare(file)
            }

            view.findViewById<ImageButton>(R.id.trimButton).setOnClickListener {
                val intent = Intent(this@GalleryActivity, TrimActivity::class.java)
                intent.putExtra("videoPath", file.absolutePath)
                startActivity(intent)
            }

            view.findViewById<ImageButton>(R.id.renameButton).setOnClickListener {
                showRenameDialog(file)
            }

            view.findViewById<ImageButton>(R.id.whatsappButton).setOnClickListener {
                shareToApp(uri, "com.whatsapp")
            }

            view.findViewById<ImageButton>(R.id.telegramButton).setOnClickListener {
                shareToApp(uri, "org.telegram.messenger")
            }

            view.findViewById<ImageButton>(R.id.shareButton).setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Partager la vidéo"))
            }

            view.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
                val trashDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), ".trash")
                if (!trashDir.exists()) trashDir.mkdirs()
                val trashedFile = File(trashDir, file.name)
                if (file.renameTo(trashedFile)) {
                    Toast.makeText(context, "Vidéo déplacée vers la corbeille", Toast.LENGTH_SHORT).show()
                    loadVideos()
                }
            }

            return view
        }
    }

    private fun compressAndShare(file: File) {
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Compression en cours…")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val outputFile = File(file.parent, "${file.nameWithoutExtension}_whatsapp.mp4")
            val success = VideoCompressor.compress(file.absolutePath, outputFile.absolutePath)

            mainHandler.post {
                dialog.dismiss()
                if (success) {
                    val originalMb = file.length() / (1024 * 1024)
                    val newMb = outputFile.length() / (1024 * 1024)
                    Toast.makeText(this, "Compressé : ${originalMb} Mo → ${newMb} Mo", Toast.LENGTH_LONG).show()
                    loadVideos()

                    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outputFile)
                    shareToApp(uri, "com.whatsapp")
                } else {
                    Toast.makeText(this, "La compression a échoué, réessaie avec une vidéo plus courte", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun playVideo(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Aucune appli pour lire la vidéo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareToApp(uri: Uri, packageName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(packageName)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Application non installée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadThumbnailAsync(file: File, imageView: ImageView) {
        Thread {
            val bitmap: Bitmap? = try {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
            } catch (e: Exception) {
                null
            }
            mainHandler.post {
                if (imageView.tag == file.absolutePath && bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }.start()
    }

    private fun showRenameDialog(file: File) {
        val nameWithoutExt = file.nameWithoutExtension
        val input = EditText(this).apply {
            setText(nameWithoutExt)
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Renommer la vidéo")
            .setView(input)
            .setPositiveButton("Renommer") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newFile = File(file.parent, "$newName.mp4")
                    if (file.renameTo(newFile)) {
                        loadVideos()
                    } else {
                        Toast.makeText(this, "Impossible de renommer (nom déjà utilisé ?)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}