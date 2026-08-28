package com.abdoula.screenrecorder

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class TrashActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        listView = findViewById(R.id.trashListView)
        emptyText = findViewById(R.id.trashEmptyText)

        loadTrash()
    }

    private fun trashDir(): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), ".trash")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun loadTrash() {
        val files = trashDir().listFiles { f -> f.extension == "mp4" }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            listView.visibility = View.GONE
            return
        }
        emptyText.visibility = View.GONE
        listView.visibility = View.VISIBLE

        listView.adapter = object : ArrayAdapter<File>(this, R.layout.item_trash, files) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_trash, parent, false)
                val file = files[position]
                view.findViewById<TextView>(R.id.trashFileName).text = file.name

                view.findViewById<ImageButton>(R.id.restoreButton).setOnClickListener {
                    val restoredFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), file.name)
                    if (file.renameTo(restoredFile)) {
                        Toast.makeText(this@TrashActivity, "Vidéo restaurée", Toast.LENGTH_SHORT).show()
                        loadTrash()
                    }
                }

                view.findViewById<ImageButton>(R.id.deleteForeverButton).setOnClickListener {
                    file.delete()
                    Toast.makeText(this@TrashActivity, "Supprimée définitivement", Toast.LENGTH_SHORT).show()
                    loadTrash()
                }

                return view
            }
        }
    }
}