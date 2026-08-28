package com.abdoula.screenrecorder

import android.media.*
import java.nio.ByteBuffer

object VideoCompressor {

    // Réencode uniquement la piste vidéo à un bitrate plus bas (la piste audio
    // est copiée telle quelle, sans perte) — réduit fortement la taille du
    // fichier pour un envoi facile sur WhatsApp.
    fun compress(inputPath: String, outputPath: String, targetBitrate: Int = 1_500_000): Boolean {
        val timeoutUs = 10_000L
        var muxer: MediaMuxer? = null

        try {
            val probeExtractor = MediaExtractor()
            probeExtractor.setDataSource(inputPath)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until probeExtractor.trackCount) {
                val format = probeExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i; videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i; audioFormat = format
                }
            }
            probeExtractor.release()
            if (videoTrackIndex == -1 || videoFormat == null) return false

            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val mime = videoFormat.getString(MediaFormat.KEY_MIME)!!

            val outFormat = MediaFormat.createVideoFormat(mime, width, height)
            outFormat.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            outFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            outFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            outFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

            val encoder = MediaCodec.createEncoderByType(mime)
            encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(inputPath)
            videoExtractor.selectTrack(videoTrackIndex)

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(videoFormat, inputSurface, null, 0)
            decoder.start()

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderDone = false

            while (!encoderDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = videoExtractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, videoExtractor.sampleTime, 0)
                            videoExtractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val decOutIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (decOutIndex >= 0) {
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(decOutIndex, bufferInfo.size > 0)
                        if (eos) {
                            encoder.signalEndOfInputStream()
                            decoderDone = true
                        }
                    }
                }

                var encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (encOutIndex >= 0 || encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        if (audioFormat != null) {
                            muxerAudioTrack = muxer.addTrack(audioFormat)
                        }
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
                            muxer.writeSampleData(muxerVideoTrack, encodedBuffer, bufferInfo)
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
            videoExtractor.release()

            if (audioTrackIndex != -1 && muxerAudioTrack != -1) {
                val audioExtractor = MediaExtractor()
                audioExtractor.setDataSource(inputPath)
                audioExtractor.selectTrack(audioTrackIndex)

                val audioBuffer = ByteBuffer.allocate(1 * 1024 * 1024)
                val audioInfo = MediaCodec.BufferInfo()

                while (true) {
                    val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break
                    audioInfo.offset = 0
                    audioInfo.size = sampleSize
                    audioInfo.presentationTimeUs = audioExtractor.sampleTime
                    audioInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioInfo)
                    audioExtractor.advance()
                }
                audioExtractor.release()
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