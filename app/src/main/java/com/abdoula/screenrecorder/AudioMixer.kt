package com.abdoula.screenrecorder

import android.content.Context
import android.media.*
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

// Mélange la voix originale de la vidéo AVEC une musique de fond (au lieu de
// remplacer l'une par l'autre). Décode les deux pistes en PCM, additionne les
// échantillons avec un léger gain réduit sur la musique pour ne pas couvrir la
// voix, puis ré-encode en AAC.
object AudioMixer {

    fun mix(context: Context, videoPath: String, musicUri: Uri, outputPath: String, musicGain: Float = 0.5f): Boolean {
        var muxer: MediaMuxer? = null
        try {
            val videoRetriever = MediaMetadataRetriever()
            videoRetriever.setDataSource(videoPath)
            val videoDurationMs = videoRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            videoRetriever.release()
            if (videoDurationMs <= 0) return false

            val videoPcm = decodeToPcm(videoPath, null)
            val musicPcm = decodeToPcm(null, context.contentResolver.openFileDescriptor(musicUri, "r")?.fileDescriptor)

            if (videoPcm == null) return false

            val sampleRate = videoPcm.sampleRate
            val channelCount = videoPcm.channelCount
            val mixedSamples = ShortArray(videoPcm.samples.size)

            for (i in videoPcm.samples.indices) {
                val voiceSample = videoPcm.samples[i].toInt()
                val musicSample = if (musicPcm != null && i < musicPcm.samples.size) {
                    (musicPcm.samples[i] * musicGain).toInt()
                } else 0
                val mixed = voiceSample + musicSample
                mixedSamples[i] = max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), mixed)).toShort()
            }

            val encodedAac = encodePcmToAac(mixedSamples, sampleRate, channelCount) ?: return false

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

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(encodedAac.format)
            muxer.start()

            videoExtractor.selectTrack(videoTrack)
            val vBuffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val vInfo = MediaCodec.BufferInfo()
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

            for (chunk in encodedAac.chunks) {
                muxer.writeSampleData(muxerAudioTrack, chunk.buffer, chunk.info)
            }

            muxer.stop()
            muxer.release()
            return true
        } catch (e: Exception) {
            try { muxer?.release() } catch (ignored: Exception) {}
            return false
        }
    }

    private class PcmResult(val samples: ShortArray, val sampleRate: Int, val channelCount: Int)

    private fun decodeToPcm(path: String?, fd: java.io.FileDescriptor?): PcmResult? {
        try {
            val extractor = MediaExtractor()
            if (path != null) extractor.setDataSource(path) else extractor.setDataSource(fd!!)

            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { audioTrack = i; audioFormat = format; break }
            }
            if (audioTrack == -1 || audioFormat == null) return null

            val decoder = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()
            extractor.selectTrack(audioTrack)

            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val outputSamples = ArrayList<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    val outBuffer = decoder.getOutputBuffer(outIndex)
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (bufferInfo.size > 0 && outBuffer != null) {
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        outBuffer.position(bufferInfo.offset)
                        var i = 0
                        while (i < bufferInfo.size - 1) {
                            outputSamples.add(outBuffer.getShort(bufferInfo.offset + i))
                            i += 2
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (eos) outputDone = true
                }
            }

            decoder.stop(); decoder.release()
            extractor.release()

            return PcmResult(outputSamples.toShortArray(), sampleRate, channelCount)
        } catch (e: Exception) {
            return null
        }
    }

    private class AacChunk(val buffer: ByteBuffer, val info: MediaCodec.BufferInfo)
    private class AacResult(val format: MediaFormat, val chunks: List<AacChunk>)

    private fun encodePcmToAac(samples: ShortArray, sampleRate: Int, channelCount: Int): AacResult? {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val pcmBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) pcmBuffer.putShort(s)
            pcmBuffer.flip()

            val chunks = ArrayList<AacChunk>()
            var outputFormat: MediaFormat? = null
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var inputDone = false
            var outputDone = false
            var presentationTimeUs = 0L
            val bytesPerSample = 2 * channelCount
            val chunkSize = 4096

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuffer = encoder.getInputBuffer(inIndex)!!
                        inBuffer.clear()
                        val remaining = pcmBuffer.remaining()
                        if (remaining <= 0) {
                            encoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val toRead = min(chunkSize, remaining)
                            val slice = pcmBuffer.slice()
                            slice.limit(toRead)
                            inBuffer.put(slice)
                            pcmBuffer.position(pcmBuffer.position() + toRead)
                            encoder.queueInputBuffer(inIndex, 0, toRead, presentationTimeUs, 0)
                            presentationTimeUs += (toRead / bytesPerSample) * 1_000_000L / sampleRate
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputFormat = encoder.outputFormat
                } else if (outIndex >= 0) {
                    val outBuffer = encoder.getOutputBuffer(outIndex)!!
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                        val copy = ByteBuffer.allocate(bufferInfo.size)
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        copy.put(outBuffer)
                        copy.flip()
                        val infoCopy = MediaCodec.BufferInfo()
                        infoCopy.set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                        chunks.add(AacChunk(copy, infoCopy))
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (eos) outputDone = true
                }
            }

            encoder.stop(); encoder.release()
            return if (outputFormat != null) AacResult(outputFormat, chunks) else null
        } catch (e: Exception) {
            return null
        }
    }
}