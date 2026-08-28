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
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        listView = findViewById(R.id.videoListView)
        emptyText = findViewById(R.id.emptyText)

        loadVideos()
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

            val uri: Uri = FileProvider.getUriForFile(
                this@GalleryActivity, "$packageName.fileprovider", file
            )

            view.findViewById<Button>(R.id.playButton).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Aucune appli pour lire la vidéo", Toast.LENGTH_SHORT).show()
                }
            }

            view.findViewById<Button>(R.id.trimButton).setOnClickListener {
                val intent = Intent(this@GalleryActivity, TrimActivity::class.java)
                intent.putExtra("videoPath", file.absolutePath)
                startActivity(intent)
            }

            view.findViewById<Button>(R.id.renameButton).setOnClickListener {
                showRenameDialog(file)
            }

            view.findViewById<Button>(R.id.whatsappButton).setOnClickListener {
                shareToApp(uri, "com.whatsapp")
            }

            view.findViewById<Button>(R.id.telegramButton).setOnClickListener {
                shareToApp(uri, "org.telegram.messenger")
            }

            view.findViewById<Button>(R.id.shareButton).setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Partager la vidéo"))
            }

            view.findViewById<Button>(R.id.deleteButton).setOnClickListener {
                file.delete()
                Toast.makeText(context, "Vidéo supprimée", Toast.LENGTH_SHORT).show()
                loadVideos()
            }

            return view
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