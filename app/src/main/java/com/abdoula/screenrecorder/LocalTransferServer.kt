package com.abdoula.screenrecorder

import java.io.File
import java.net.ServerSocket
import java.net.Socket

// Petit serveur web local qui sert un seul fichier vidéo sur le réseau Wifi
// local — aucune connexion internet nécessaire, l'autre téléphone doit juste
// être sur le même réseau Wifi (ou l'un des deux a activé son point d'accès
// personnel et l'autre s'y est connecté).
class LocalTransferServer(private val filePath: String, private val port: Int = 8080) {

    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    var onTransferStarted: (() -> Unit)? = null
    var onTransferCompleted: (() -> Unit)? = null
    var onTransferFailed: (() -> Unit)? = null

    fun start() {
        running = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                }
            } catch (e: Exception) {
                // Le serveur s'est arrêté (normal quand on appelle stop())
            }
        }.start()
    }

    private fun handleClient(client: Socket) {
        try {
            onTransferStarted?.invoke()
            val file = File(filePath)
            val output = client.getOutputStream()
            val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: video/mp4\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
                "Connection: close\r\n\r\n"
            output.write(header.toByteArray())
            file.inputStream().use { input -> input.copyTo(output) }
            output.flush()
            onTransferCompleted?.invoke()
        } catch (e: Exception) {
            onTransferFailed?.invoke()
        } finally {
            try { client.close() } catch (e: Exception) {}
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (e: Exception) {}
    }
}