package com.abdoula.screenrecorder

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecordService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val channelId = "screen_record_channel"

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            isRunning = false
        }
    }

    companion object {
        const val ACTION_STOP = "com.abdoula.screenrecorder.STOP"
        var isRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        startForegroundNotification()
        requestAudioFocus()

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, null)

        startRecording()

        return START_NOT_STICKY
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .build()

        audioManager.requestAudioFocus(audioFocusRequest!!)
    }

    private fun releaseAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Enregistrement d'écran", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Enregistrement en cours")
            .setContentText("Appuie ici pour arrêter")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(stopPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Arrêter", stopPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startRecording() {
        val metrics = resources.displayMetrics
        val deviceWidth = metrics.widthPixels
        val deviceHeight = metrics.heightPixels
        val density = metrics.densityDpi

        val targetHeight = SettingsManager.getResolutionHeight(this)

        val width: Int
        val height: Int
        if (targetHeight == 0 || targetHeight >= deviceHeight) {
            width = (deviceWidth / 2) * 2
            height = (deviceHeight / 2) * 2
        } else {
            val scale = targetHeight.toDouble() / deviceHeight.toDouble()
            height = (targetHeight / 2) * 2
            width = ((deviceWidth * scale).toInt() / 2) * 2
        }

        val bitrate = SettingsManager.resolveBitrate(this, height)
        val frameRate = SettingsManager.resolveFrameRate(this)

        val outputFile = getOutputFile()

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)

            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setAudioChannels(1)

            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoEncodingBitRate(bitrate)
            setVideoFrameRate(frameRate)

            setOutputFile(outputFile.absolutePath)
            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecord",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            mediaRecorder!!.surface, null, null
        )

        mediaRecorder?.start()
        isRunning = true
    }

    private fun getOutputFile(): java.io.File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (dir != null && !dir.exists()) dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return java.io.File(dir, "enregistrement_$timestamp.mp4")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
        } catch (e: Exception) {
        }
        mediaRecorder?.release()
        virtualDisplay?.release()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        releaseAudioFocus()
        stopService(Intent(this, OverlayDrawingService::class.java))
        stopService(Intent(this, CameraBubbleService::class.java))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}