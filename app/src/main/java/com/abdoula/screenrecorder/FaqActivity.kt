package com.abdoula.screenrecorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

data class FaqItem(val question: String, val answer: String)

class FaqActivity : AppCompatActivity() {

    private val faqs = listOf(
        FaqItem("L'écran reste noir après enregistrement ?", "Vérifie que la qualité est réglée en 720p dans les réglages, et évite de fermer l'appli pendant l'enregistrement."),
        FaqItem("Comment déplacer la bulle d'outils ?", "Maintiens ton doigt dessus et glisse. Un tap simple ouvre le panneau d'outils."),
        FaqItem("Où sont sauvegardées mes vidéos ?", "Dans la galerie de l'appli (onglet du bas), accessible aussi via le dossier Movies de l'appli."),
        FaqItem("Comment récupérer une vidéo supprimée ?", "Menu ☰ → Vidéos supprimées. Les fichiers y restent jusqu'à suppression définitive."),
        FaqItem("La musique de fond ne s'ajoute pas ?", "Utilise un fichier audio de ton stockage (MP3 ou autre), et vérifie que la vidéo n'est pas trop longue."),
        FaqItem("Comment partager l'appli à un ami ?", "Menu ☰ → Partager l'application. Ça envoie directement le fichier installable.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faq)

        val listView = findViewById<ListView>(R.id.faqListView)
        listView.adapter = object : ArrayAdapter<FaqItem>(this, R.layout.item_faq, faqs) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_faq, parent, false)
                val item = faqs[position]
                view.findViewById<TextView>(R.id.faqQuestion).text = item.question
                view.findViewById<TextView>(R.id.faqAnswer).text = item.answer
                return view
            }
        }
    }
}