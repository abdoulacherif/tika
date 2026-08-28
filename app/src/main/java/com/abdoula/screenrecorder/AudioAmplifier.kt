package com.abdoula.screenrecorder

import android.media.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

// Augmente le volume de la piste audio d'une vidéo (décode en PCM, multiplie
// l'amplitude, ré-encode en AAC). La piste vidéo est copiée sans y toucher.
object AudioAmplifier {

    fun amplify(inputPath: String, outputPath: String, gain: Float = 2.5f): Boolean {
        var muxer: MediaMuxer? = null
        try {
            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(inputPath)
            var videoTrack = -1
            var audioTrack = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrack == -1) { videoTrack = i; videoFormat = format }
                else if (mime.startsWith("audio/") && audioTrack == -1) { audioTrack = i; audioFormat = format }
            }
            if (videoTrack == -1 || videoFormat == null) return false
            if (audioTrack == -1 || audioFormat == null) return false

            val decoder = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

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

            videoExtractor.selectTrack(audioTrack)
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
                        val outBuffer = decoder.getOutputBuffer(decOutIndex)
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (bufferInfo.size > 0 && outBuffer != null) {
                            amplifyPcm(outBuffer, bufferInfo.offset, bufferInfo.size, gain)
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
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bufferInfo.size = 0
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
            videoExtractor.unselectTrack(audioTrack)

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

    // Multiplie chaque échantillon PCM 16 bits par le gain, en évitant la
    // saturation (clipping) au-delà des limites du format.
    private fun amplifyPcm(buffer: ByteBuffer, offset: Int, size: Int, gain: Float) {
        val original = buffer.order()
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        var i = offset
        while (i < offset + size - 1) {
            val sample = buffer.getShort(i)
            val amplified = (sample * gain).toInt()
            val clamped = max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), amplified))
            buffer.putShort(i, clamped.toShort())
            i += 2
        }
        buffer.order(original)
    }
}