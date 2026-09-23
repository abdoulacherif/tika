package com.abdoula.screenrecorder

import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
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
    private lateinit var searchInput: EditText
    private val mainHandler = Handler(Looper.getMainLooper())

    private var selectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    private var pendingMusicTargetFile: File? = null
    private var pendingMusicMode: String = "replace"

    private var allFiles: List<File> = emptyList()
    private var searchQuery: String = ""

    private enum class SortOption { DATE_RECENT, DATE_OLD, SIZE_BIG, SIZE_SMALL, NAME_AZ }
    private var currentSort = SortOption.DATE_RECENT

    private val musicPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingMusicTargetFile
        if (uri != null && target != null) {
            if (pendingMusicMode == "mix") {
                applyMusicMix(target, uri)
            } else {
                applyMusic(target, uri)
            }
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
        searchInput = findViewById(R.id.searchInput)

        selectModeButton.setOnClickListener { toggleSelectionMode() }
        findViewById<android.widget.Button>(R.id.mergeConfirmButton).setOnClickListener { confirmMerge() }

        findViewById<ImageButton>(R.id.sortButton).setOnClickListener { showSortDialog() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                refreshDisplay()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadVideos()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            "📅 Plus récentes d'abord",
            "📅 Plus anciennes d'abord",
            "📦 Plus grosses d'abord",
            "📦 Plus légères d'abord",
            "🔤 Nom (A-Z)"
        )
        val current = SortOption.values().indexOf(currentSort)

        AlertDialog.Builder(this)
            .setTitle("Trier par")
            .setSingleChoiceItems(options, current) { dialog, which ->
                currentSort = SortOption.values()[which]
                refreshDisplay()
                dialog.dismiss()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun toggleSelectionMode() {
        selectionMode = !selectionMode
        selectedFiles.clear()
        selectModeButton.text = if (selectionMode) "Annuler" else "Sélectionner"
        mergeBar.visibility = if (selectionMode) View.VISIBLE else View.GONE
        updateSelectionCount()
        refreshDisplay()
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

    private fun showMusicChoiceDialog(file: File) {
        val layout = layoutInflater.inflate(R.layout.dialog_music_choice, null)

        val dialog = AlertDialog.Builder(this)
            .setView(layout)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        layout.findViewById<LinearLayout>(R.id.replaceOption).setOnClickListener {
            dialog.dismiss()
            pendingMusicTargetFile = file
            pendingMusicMode = "replace"
            musicPickerLauncher.launch(arrayOf("audio/*"))
        }

        layout.findViewById<LinearLayout>(R.id.mixOption).setOnClickListener {
            dialog.dismiss()
            if (!SettingsManager.isProUser(this)) {
                AlertDialog.Builder(this)
                    .setTitle("💎 Fonctionnalité Pro")
                    .setMessage("Mélanger ta voix avec une musique de fond est réservé à la version Pro. Le remplacement simple reste gratuit.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            pendingMusicTargetFile = file
            pendingMusicMode = "mix"
            musicPickerLauncher.launch(arrayOf("audio/*"))
        }

        layout.findViewById<TextView>(R.id.musicCancelText).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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

    private fun applyMusicMix(videoFile: File, musicUri: Uri) {
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Mélange en cours…")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val outputFile = File(videoFile.parent, "${videoFile.nameWithoutExtension}_mixe.mp4")
            val success = AudioMixer.mix(this, videoFile.absolutePath, musicUri, outputFile.absolutePath)

            mainHandler.post {
                dialog.dismiss()
                if (success) {
                    Toast.makeText(this, "Voix et musique mélangées : ${outputFile.name}", Toast.LENGTH_LONG).show()
                    loadVideos()
                } else {
                    Toast.makeText(this, "Le mélange a échoué, réessaie avec une vidéo plus courte", Toast.LENGTH_LONG).show()
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

    private fun showBudgetCompressDialog(file: File) {
        val input = EditText(this).apply {
            hint = "Taille cible en Mo (ex: 10)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        AlertDialog.Builder(this)
            .setTitle("🎯 Compresser à une taille précise")
            .setMessage("L'appli calcule automatiquement la qualité nécessaire pour atteindre cette taille.")
            .setView(input)
            .setPositiveButton("Compresser") { _, _ ->
                val targetMB = input.text.toString().toDoubleOrNull()
                if (targetMB == null || targetMB <= 0) {
                    Toast.makeText(this, "Entre une taille valide", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runBudgetCompression(file, targetMB)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun runBudgetCompression(file: File, targetMB: Double) {
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Compression en cours…")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val outputFile = File(file.parent, "${file.nameWithoutExtension}_${targetMB.toInt()}Mo.mp4")
            val success = VideoCompressor.compressToTargetSize(file.absolutePath, outputFile.absolutePath, targetMB)

            mainHandler.post {
                dialog.dismiss()
                if (success) {
                    val actualMb = outputFile.length() / (1024 * 1024)
                    Toast.makeText(this, "Fichier créé : ~${actualMb} Mo", Toast.LENGTH_LONG).show()
                    loadVideos()
                } else {
                    Toast.makeText(this, "La compression a échoué", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showExportFormatDialog(file: File) {
        val options = arrayOf("⬛ Carré (Instagram) — 1080×1080", "📱 Vertical (Stories/TikTok) — 1080×1920", "🖥️ Horizontal (YouTube) — 1920×1080")
        AlertDialog.Builder(this)
            .setTitle("Choisir un format d'export")
            .setItems(options) { _, which ->
                val (w, h) = when (which) {
                    0 -> 1080 to 1080
                    1 -> 1080 to 1920
                    else -> 1920 to 1080
                }
                runExportFormat(file, w, h)
            }
            .show()
    }

    private fun runExportFormat(file: File, width: Int, height: Int) {
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Export en cours… (peut prendre du temps)")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val suffix = when {
                width == height -> "carre"
                height > width -> "vertical"
                else -> "horizontal"
            }
            val outputFile = File(file.parent, "${file.nameWithoutExtension}_$suffix.mp4")
            val success = VideoReformatter.reformat(file.absolutePath, outputFile.absolutePath, width, height)

            mainHandler.post {
                dialog.dismiss()
                if (success) {
                    Toast.makeText(this, "Export terminé : ${outputFile.name}", Toast.LENGTH_LONG).show()
                    loadVideos()
                } else {
                    Toast.makeText(this, "L'export a échoué, réessaie avec une vidéo plus courte", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showVoiceoverDialog(file: File) {
        val layout = layoutInflater.inflate(R.layout.dialog_voiceover_choice, null)
        val textInput = layout.findViewById<EditText>(R.id.voiceoverTextInput)

        val dialog = AlertDialog.Builder(this)
            .setView(layout)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        layout.findViewById<LinearLayout>(R.id.voiceoverReplaceOption).setOnClickListener {
            val text = textInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Tape un texte d'abord", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            generateAndApplyVoiceover(file, text, mode = "replace")
        }

        layout.findViewById<LinearLayout>(R.id.voiceoverMixOption).setOnClickListener {
            val text = textInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Tape un texte d'abord", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            generateAndApplyVoiceover(file, text, mode = "mix")
        }

        layout.findViewById<TextView>(R.id.voiceoverCancelText).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun generateAndApplyVoiceover(file: File, text: String, mode: String) {
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("🎤 Génération de la voix off…")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        dialog.show()

        val voiceoverManager = VoiceoverManager(this)
        voiceoverManager.initialize { ready ->
            if (!ready) {
                dialog.dismiss()
                Toast.makeText(this, "Moteur vocal indisponible sur ce téléphone", Toast.LENGTH_LONG).show()
                return@initialize
            }

            val wavFile = File(cacheDir, "voiceover_${System.currentTimeMillis()}.wav")
            voiceoverManager.generateSpeech(text, wavFile) { resultFile ->
                if (resultFile == null) {
                    dialog.dismiss()
                    voiceoverManager.shutdown()
                    Toast.makeText(this, "La génération de la voix a échoué", Toast.LENGTH_LONG).show()
                    return@generateSpeech
                }

                dialog.setTitle("🎬 Application à la vidéo…")

                Thread {
                    val suffix = if (mode == "mix") "voixmixee" else "voixoff"
                    val outputFile = File(file.parent, "${file.nameWithoutExtension}_$suffix.mp4")
                    val success = if (mode == "mix") {
                        AudioMixer.mixFromFile(file.absolutePath, resultFile.absolutePath, outputFile.absolutePath)
                    } else {
                        AudioReplacer.replaceAudioFromFile(file.absolutePath, resultFile.absolutePath, outputFile.absolutePath)
                    }

                    voiceoverManager.shutdown()
                    resultFile.delete()

                    mainHandler.post {
                        dialog.dismiss()
                        if (success) {
                            Toast.makeText(this, "Voix off ajoutée : ${outputFile.name}", Toast.LENGTH_LONG).show()
                            loadVideos()
                        } else {
                            Toast.makeText(this, "Impossible d'ajouter la voix off", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }
    }

    private fun loadVideos() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        allFiles = dir?.listFiles { f -> f.extension == "mp4" }?.toList() ?: emptyList()
        refreshDisplay()
    }

    private fun refreshDisplay() {
        var files = allFiles

        if (searchQuery.isNotBlank()) {
            files = files.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        files = when (currentSort) {
            SortOption.DATE_RECENT -> files.sortedByDescending { it.lastModified() }
            SortOption.DATE_OLD -> files.sortedBy { it.lastModified() }
            SortOption.SIZE_BIG -> files.sortedByDescending { it.length() }
            SortOption.SIZE_SMALL -> files.sortedBy { it.length() }
            SortOption.NAME_AZ -> files.sortedBy { it.name.lowercase() }
        }

        if (files.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = if (searchQuery.isNotBlank()) "Aucune vidéo ne correspond à « $searchQuery »" else "Aucune vidéo pour le moment"
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

            var previewPlaying = false
            val clickArea = view.findViewById<LinearLayout>(R.id.itemClickArea)

            clickArea.setOnClickListener {
                if (selectionMode) {
                    if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
                    updateSelectionCount()
                    notifyDataSetChanged()
                } else {
                    playVideo(uri)
                }
            }

            clickArea.setOnLongClickListener {
                if (!selectionMode && !previewPlaying) {
                    previewPlaying = true
                    Toast.makeText(context, "▶️ Aperçu rapide…", Toast.LENGTH_SHORT).show()
                    playVideo(uri)
                    object : CountDownTimer(300, 300) {
                        override fun onTick(millisUntilFinished: Long) {}
                        override fun onFinish() { previewPlaying = false }
                    }.start()
                }
                true
            }

            view.findViewById<ImageButton>(R.id.subtitleButton).setOnClickListener {
                val intent = Intent(this@GalleryActivity, SubtitleActivity::class.java)
                intent.putExtra("videoPath", file.absolutePath)
                startActivity(intent)
            }

            view.findViewById<ImageButton>(R.id.voiceoverButton).setOnClickListener {
                showVoiceoverDialog(file)
            }

            view.findViewById<ImageButton>(R.id.musicButton).setOnClickListener {
                showMusicChoiceDialog(file)
            }

            view.findViewById<ImageButton>(R.id.amplifyButton).setOnClickListener {
                amplifyAudio(file)
            }

            view.findViewById<ImageButton>(R.id.budgetCompressButton).setOnClickListener {
                showBudgetCompressDialog(file)
            }

            view.findViewById<ImageButton>(R.id.compressButton).setOnClickListener {
                compressAndShare(file)
            }

            view.findViewById<ImageButton>(R.id.cropButton).setOnClickListener {
                val intent = Intent(this@GalleryActivity, CropActivity::class.java)
                intent.putExtra("videoPath", file.absolutePath)
                startActivity(intent)
            }

            view.findViewById<ImageButton>(R.id.exportFormatButton).setOnClickListener {
                showExportFormatDialog(file)
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
                if (SettingsManager.isTrashEnabled(context)) {
                    val trashDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), ".trash")
                    if (!trashDir.exists()) trashDir.mkdirs()
                    val trashedFile = File(trashDir, file.name)
                    if (file.renameTo(trashedFile)) {
                        Toast.makeText(context, "Vidéo déplacée vers la corbeille", Toast.LENGTH_SHORT).show()
                        loadVideos()
                    }
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("Supprimer définitivement ?")
                        .setMessage("La corbeille est désactivée, cette action est irréversible.")
                        .setPositiveButton("Supprimer") { _, _ ->
                            file.delete()
                            Toast.makeText(context, "Vidéo supprimée", Toast.LENGTH_SHORT).show()
                            loadVideos()
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
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