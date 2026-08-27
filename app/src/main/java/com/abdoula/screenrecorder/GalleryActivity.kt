package com.abdoula.screenrecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyText: TextView

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
}