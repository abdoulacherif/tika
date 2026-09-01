package com.abdoula.screenrecorder

import android.content.Context

// Liste des nouveautés par version. Ajoute une entrée ici à chaque nouvelle
// version que tu publies (même versionCode que dans app/build.gradle), et le
// badge "Nouveautés" se met à jour automatiquement pour les utilisateurs qui
// mettent à jour depuis une version plus ancienne.
object WhatsNewManager {

    data class VersionNotes(val versionCode: Int, val versionName: String, val features: List<String>)

    private val history = listOf(
        VersionNotes(3, "1.2", listOf(
            "🎨 Nouveau nom et logo : Écran+",
            "🎟️ Codes d'activation Pro",
            "🐛 Correction du bug audio qui coupait pendant l'enregistrement",
            "✋ Bouton magique simplifié : un seul tap pour les outils"
        ))
        // Ajoute VersionNotes(4, "1.3", listOf("...")) à la prochaine version, etc.
    )

    fun getUnseenNotes(context: Context): List<VersionNotes> {
        val lastSeen = SettingsManager.getLastSeenVersionCode(context)
        return history.filter { it.versionCode > lastSeen }.sortedBy { it.versionCode }
    }
}