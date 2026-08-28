package com.abdoula.screenrecorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer

// Remplace la piste audio d'une vidéo par un fichier audio choisi (musique de fond).
// La musique est coupée à la durée de la vidéo ; si elle est plus courte que la
// vidéo, elle s'arrête simplement avant la fin (pas de boucle automatique).
object AudioReplacer {

    fun replaceAudio(context: Context, videoPath: String, musicUri: Uri, outputPath: String): Boolean {
        var muxer: MediaMuxer? = null
        try {
            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoPath)
            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            var videoDurationUs = 0L
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrack == -1) {
                    videoTrack = i
                    videoFormat = format
                    videoDurationUs = format.getLong(MediaFormat.KEY_DURATION)
                }
            }
            if (videoTrack == -1 || videoFormat == null) return false

            val pfd = context.contentResolver.openFileDescriptor(musicUri, "r") ?: return false
            val musicExtractor = MediaExtractor()
            musicExtractor.setDataSource(pfd.fileDescriptor)
            var musicTrack = -1
            var musicFormat: MediaFormat? = null
            for (i in 0 until musicExtractor.trackCount) {
                val format = musicExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    musicTrack = i
                    musicFormat = format
                    break
                }
            }
            if (musicTrack == -1 || musicFormat == null) {
                pfd.close()
                return false
            }

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(musicFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            videoExtractor.selectTrack(videoTrack)
            while (true) {
                buffer.clear()
                val size = videoExtractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, info)
                videoExtractor.advance()
            }
            videoExtractor.release()

            musicExtractor.selectTrack(musicTrack)
            while (true) {
                buffer.clear()
                val size = musicExtractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (musicExtractor.sampleTime > videoDurationUs) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = musicExtractor.sampleTime
                info.flags = musicExtractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, info)
                musicExtractor.advance()
            }
            musicExtractor.release()
            pfd.close()

            muxer.stop()
            muxer.release()
            return true
        } catch (e: Exception) {
            try { muxer?.release() } catch (ignored: Exception) {}
            return false
        }
    }
}