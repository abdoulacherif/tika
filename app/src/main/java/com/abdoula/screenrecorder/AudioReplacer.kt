package com.abdoula.screenrecorder

import android.content.Context
import android.media.*
import android.net.Uri
import java.nio.ByteBuffer

// Remplace la piste audio d'une vidéo par une musique choisie. La musique est
// décodée puis ré-encodée en AAC (format accepté par le conteneur MP4), quel
// que soit son format d'origine (MP3, WAV, etc.) — c'est indispensable, MP4
// n'accepte pas le MP3 brut.
object AudioReplacer {

    fun replaceAudio(context: Context, videoPath: String, musicUri: Uri, outputPath: String): Boolean {
        var muxer: MediaMuxer? = null
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            val videoDurationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
            retriever.release()
            if (videoDurationUs <= 0L) return false

            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoPath)
            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) { videoTrack = i; videoFormat = format; break }
            }
            if (videoTrack == -1 || videoFormat == null) return false

            val pfd = context.contentResolver.openFileDescriptor(musicUri, "r") ?: return false
            val musicExtractor = MediaExtractor()
            musicExtractor.setDataSource(pfd.fileDescriptor)
            var musicTrack = -1
            var musicInFormat: MediaFormat? = null
            for (i in 0 until musicExtractor.trackCount) {
                val format = musicExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { musicTrack = i; musicInFormat = format; break }
            }
            if (musicTrack == -1 || musicInFormat == null) { pfd.close(); return false }

            val decoder = MediaCodec.createDecoderByType(musicInFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(musicInFormat, null, null, 0)
            decoder.start()
            musicExtractor.selectTrack(musicTrack)

            val sampleRate = if (musicInFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) musicInFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (musicInFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) musicInFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            val encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
            encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            var muxerAudioTrack = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var inputDone = false
            var decoderDone = false
            var encoderDone = false

            while (!encoderDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = musicExtractor.readSampleData(buffer, 0)
                        if (sampleSize < 0 || musicExtractor.sampleTime > videoDurationUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, musicExtractor.sampleTime, 0)
                            musicExtractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val decOutIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (decOutIndex >= 0) {
                        val outBuffer = decoder.getOutputBuffer(decOutIndex)
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (bufferInfo.size > 0 && outBuffer != null) {
                            val encInIndex = encoder.dequeueInputBuffer(timeoutUs)
                            if (encInIndex >= 0) {
                                val encInBuffer = encoder.getInputBuffer(encInIndex)!!
                                encInBuffer.clear()
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                encInBuffer.put(outBuffer)
                                encoder.queueInputBuffer(encInIndex, 0, bufferInfo.size, bufferInfo.presentationTimeUs, 0)
                            }
                        }
                        decoder.releaseOutputBuffer(decOutIndex, false)
                        if (eos) {
                            val encInIndex = encoder.dequeueInputBuffer(timeoutUs)
                            if (encInIndex >= 0) {
                                encoder.queueInputBuffer(encInIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                            decoderDone = true
                        }
                    }
                }

                var encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (encOutIndex >= 0 || encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxerAudioTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else {
                        val encodedBuffer = encoder.getOutputBuffer(encOutIndex)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerAudioTrack, encodedBuffer, bufferInfo)
                        }
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(encOutIndex, false)
                        if (eos) encoderDone = true
                    }
                    encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                }
            }

            decoder.stop(); decoder.release()
            encoder.stop(); encoder.release()
            musicExtractor.release()
            pfd.close()

            if (!muxerStarted) { muxer.release(); return false }

            val vBuffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val vInfo = MediaCodec.BufferInfo()
            videoExtractor.selectTrack(videoTrack)
            while (true) {
                vBuffer.clear()
                val size = videoExtractor.readSampleData(vBuffer, 0)
                if (size < 0) break
                vInfo.offset = 0
                vInfo.size = size
                vInfo.presentationTimeUs = videoExtractor.sampleTime
                vInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, vBuffer, vInfo)
                videoExtractor.advance()
            }
            videoExtractor.release()

            muxer.stop()
            muxer.release()
            return true
        } catch (e: Exception) {
            try { muxer?.release() } catch (ignored: Exception) {}
            return false
        }
    }
}