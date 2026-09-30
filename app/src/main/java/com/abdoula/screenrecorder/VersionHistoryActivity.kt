package com.abdoula.screenrecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VersionHistoryActivity : AppCompatActivity() {

    // Repère la partie "enregistrement_AAAAMMJJ_HHMMSS" ou "fusion_HHMMSS" qui
    // sert de clé commune entre l'original et toutes ses variantes, quel que
    // soit le nombre de transformations appliquées ensuite.
    private val rootKeyRegex = Regex("(enregistrement_\\d{8}_\\d{6}|fusion_\\d{6})")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_version_history)

        loadGroups()
    }

    private fun loadGroups() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val files = dir?.listFiles { f -> f.extension == "mp4" }?.toList() ?: emptyList()

        val groups = LinkedHashMap<String, MutableList<File>>()
        for (file in files) {
            val match = rootKeyRegex.find(file.name)
            val key = match?.value ?: file.name // pas de correspondance = groupe à part
            groups.getOrPut(key) { mutableListOf() }.add(file)
        }

        // Ne garde que les groupes ayant plusieurs versions (l'intérêt de cet écran)
        val multiVersionGroups = groups.filter { it.value.size > 1 }

        val container = findViewById<LinearLayout>(R.id.versionsContainer)
        container.removeAllViews()

        val emptyText = findViewById<TextView>(R.id.versionsEmptyText)
        if (multiVersionGroups.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            return
        }
        emptyText.visibility = View.GONE

        for ((key, groupFiles) in multiVersionGroups) {
            val sorted = groupFiles.sortedByDescending { it.lastModified() }
            addGroupView(key, sorted, container)
        }
    }

    private fun addGroupView(key: String, files: List<File>, container: LinearLayout) {
        val groupView = LayoutInflater.from(this).inflate(R.layout.item_version_group, container, false)

        groupView.findViewById<TextView>(R.id.groupTitle).text = "📹 $key"
        groupView.findViewById<TextView>(R.id.groupCount).text = "${files.size} versions"

        val versionsList = groupView.findViewById<LinearLayout>(R.id.groupVersionsList)
        for (file in files) {
            versionsList.addView(buildVersionRow(file))
        }

        groupView.findViewById<LinearLayout>(R.id.groupHeader).setOnClickListener {
            versionsList.visibility = if (versionsList.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        container.addView(groupView)
    }

    private fun buildVersionRow(file: File): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_version_row, null)

        val dateStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        val sizeMb = file.length() / (1024 * 1024)
        row.findViewById<TextView>(R.id.versionLabel).text = "${file.name}\n$dateStr • $sizeMb Mo"

        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        row.findViewById<ImageButton>(R.id.versionPlayButton).setOnClickListener {
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

        row.findViewById<ImageButton>(R.id.versionDeleteButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Supprimer cette version ?")
                .setMessage(file.name)
                .setPositiveButton("Supprimer") { _, _ ->
                    file.delete()
                    Toast.makeText(this, "Version supprimée", Toast.LENGTH_SHORT).show()
                    loadGroups()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        return row
    }
}