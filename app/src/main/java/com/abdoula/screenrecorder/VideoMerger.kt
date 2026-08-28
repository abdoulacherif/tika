package com.abdoula.screenrecorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

// Fusionne plusieurs vidéos MP4 bout à bout, sans ré-encodage.
// Fonctionne de façon fiable quand les vidéos viennent toutes de cette appli
// (même résolution/codec) — c'est le cas normal d'utilisation.
object VideoMerger {

    fun merge(inputPaths: List<String>, outputPath: String): Boolean {
        if (inputPaths.isEmpty()) return false

        var muxer: MediaMuxer? = null
        try {
            val firstExtractor = MediaExtractor()
            firstExtractor.setDataSource(inputPaths[0])
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until firstExtractor.trackCount) {
                val format = firstExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoFormat == null) videoFormat = format
                else if (mime.startsWith("audio/") && audioFormat == null) audioFormat = format
            }
            firstExtractor.release()
            if (videoFormat == null) return false

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = if (audioFormat != null) muxer.addTrack(audioFormat) else -1
            muxer.start()

            var videoOffsetUs = 0L
            var audioOffsetUs = 0L
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            for (path in inputPaths) {
                val extractor = MediaExtractor()
                extractor.setDataSource(path)

                var vTrack = -1
                var aTrack = -1
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && vTrack == -1) vTrack = i
                    else if (mime.startsWith("audio/") && aTrack == -1) aTrack = i
                }

                var maxVideoPts = 0L
                if (vTrack != -1) {
                    extractor.selectTrack(vTrack)
                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = extractor.sampleTime + videoOffsetUs
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerVideoTrack, buffer, info)
                        maxVideoPts = extractor.sampleTime
                        extractor.advance()
                    }
                    extractor.unselectTrack(vTrack)
                }
                videoOffsetUs += maxVideoPts + 33_000L

                var maxAudioPts = 0L
                if (aTrack != -1 && muxerAudioTrack != -1) {
                    extractor.selectTrack(aTrack)
                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = extractor.sampleTime + audioOffsetUs
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerAudioTrack, buffer, info)
                        maxAudioPts = extractor.sampleTime
                        extractor.advance()
                    }
                    extractor.unselectTrack(aTrack)
                }
                audioOffsetUs += maxAudioPts + 23_000L

                extractor.release()
            }

            muxer.stop()
            muxer.release()
            return true
        } catch (e: Exception) {
            try { muxer?.release() } catch (ignored: Exception) {}
            return false
        }
    }
}