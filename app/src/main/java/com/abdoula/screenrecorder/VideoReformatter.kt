package com.abdoula.screenrecorder

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.nio.ByteBuffer

object VideoReformatter {

    // ============================================================
    // FORMATAGE AUTOMATIQUE
    // ============================================================

    fun reformat(
        inputPath: String,
        outputPath: String,
        targetWidth: Int,
        targetHeight: Int
    ): Boolean {

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var codecInputSurface: CodecInputSurface? = null
        var decoderSurface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            var videoTrack = -1
            var audioTrack = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/") && videoTrack == -1) {
                    videoTrack = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrack == -1) {
                    audioTrack = i
                    audioFormat = format
                }
            }

            if (videoTrack == -1 || videoFormat == null) {
                return false
            }

            val srcWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val srcHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val srcMime = videoFormat.getString(MediaFormat.KEY_MIME)
                ?: return false

            // ====================================================
            // ENCODEUR
            // ====================================================

            val outFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                targetWidth,
                targetHeight
            )

            outFormat.setInteger(
                MediaFormat.KEY_BIT_RATE,
                6_000_000
            )

            outFormat.setInteger(
                MediaFormat.KEY_FRAME_RATE,
                30
            )

            outFormat.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL,
                2
            )

            outFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )

            encoder = MediaCodec.createEncoderByType(
                MediaFormat.MIMETYPE_VIDEO_AVC
            )

            encoder.configure(
                outFormat,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            val encoderSurface = encoder.createInputSurface()

            encoder.start()

            codecInputSurface = CodecInputSurface(encoderSurface)
            codecInputSurface.makeCurrent()

            // ====================================================
            // RENDU OPENGL
            // ====================================================

            val textureRender = STextureRender()
            textureRender.surfaceCreated()

            // Calcul du recadrage automatique
            val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()
            val dstAspect = targetWidth.toFloat() / targetHeight.toFloat()

            if (srcAspect > dstAspect) {

                val cropWidthUV = dstAspect / srcAspect

                val uMin = (1f - cropWidthUV) / 2f
                val uMax = 1f - uMin

                textureRender.setCropUV(
                    uMin,
                    uMax,
                    0f,
                    1f
                )

            } else {

                val cropHeightUV = srcAspect / dstAspect

                val vMin = (1f - cropHeightUV) / 2f
                val vMax = 1f - vMin

                textureRender.setCropUV(
                    0f,
                    1f,
                    vMin,
                    vMax
                )
            }

            // IMPORTANT :
            // Utilisation d'une variable val locale.
            // Cela évite le problème de smart cast de Kotlin.
            val st = SurfaceTexture(
                textureRender.getTextureId()
            )

            surfaceTexture = st

            val frameLock = Object()

            var frameAvailable = false

            st.setOnFrameAvailableListener {
                synchronized(frameLock) {
                    frameAvailable = true
                    frameLock.notifyAll()
                }
            }

            decoderSurface = Surface(st)

            // ====================================================
            // DECODEUR
            // ====================================================

            decoder = MediaCodec.createDecoderByType(srcMime)

            decoder.configure(
                videoFormat,
                decoderSurface,
                null,
                0
            )

            decoder.start()

            extractor.selectTrack(videoTrack)

            // ====================================================
            // MUXER
            // ====================================================

            muxer = MediaMuxer(
                outputPath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()

            val timeoutUs = 10_000L

            var inputDone = false
            var decoderDone = false
            var encoderDone = false

            // ====================================================
            // BOUCLE DE TRAITEMENT
            // ====================================================

            while (!encoderDone) {

                // ------------------------------
                // ENTRÉE DU DÉCODEUR
                // ------------------------------

                if (!inputDone) {

                    val inputIndex = decoder.dequeueInputBuffer(
                        timeoutUs
                    )

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            decoder.getInputBuffer(inputIndex)

                        if (inputBuffer != null) {

                            val sampleSize =
                                extractor.readSampleData(
                                    inputBuffer,
                                    0
                                )

                            if (sampleSize < 0) {

                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )

                                inputDone = true

                            } else {

                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    extractor.sampleFlags
                                )

                                extractor.advance()
                            }
                        }
                    }
                }

                // ------------------------------
                // SORTIE DU DÉCODEUR
                // ------------------------------

                if (!decoderDone) {

                    val decoderOutputIndex =
                        decoder.dequeueOutputBuffer(
                            bufferInfo,
                            timeoutUs
                        )

                    if (decoderOutputIndex >= 0) {

                        val eos =
                            (bufferInfo.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        val doRender =
                            bufferInfo.size > 0

                        decoder.releaseOutputBuffer(
                            decoderOutputIndex,
                            doRender
                        )

                        if (doRender) {

                            synchronized(frameLock) {

                                while (!frameAvailable) {
                                    frameLock.wait(1000)
                                }

                                frameAvailable = false
                            }

                            // Utilisation directe de la variable val.
                            st.updateTexImage()

                            textureRender.drawFrame(
                                st,
                                targetWidth,
                                targetHeight
                            )

                            codecInputSurface.setPresentationTime(
                                bufferInfo.presentationTimeUs * 1000L
                            )

                            codecInputSurface.swapBuffers()
                        }

                        if (eos) {

                            encoder.signalEndOfInputStream()

                            decoderDone = true
                        }
                    }
                }

                // ------------------------------
                // SORTIE DE L'ENCODEUR
                // ------------------------------

                var encoderOutputIndex =
                    encoder.dequeueOutputBuffer(
                        bufferInfo,
                        timeoutUs
                    )

                while (
                    encoderOutputIndex >= 0 ||
                    encoderOutputIndex ==
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                ) {

                    if (
                        encoderOutputIndex ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                    ) {

                        muxerVideoTrack =
                            muxer.addTrack(
                                encoder.outputFormat
                            )

                        if (audioFormat != null) {

                            muxerAudioTrack =
                                muxer.addTrack(audioFormat)
                        }

                        muxer.start()

                        muxerStarted = true

                    } else {

                        val encodedBuffer =
                            encoder.getOutputBuffer(
                                encoderOutputIndex
                            )

                        if (encodedBuffer != null) {

                            if (
                                (bufferInfo.flags and
                                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            ) {
                                bufferInfo.size = 0
                            }

                            if (
                                bufferInfo.size > 0 &&
                                muxerStarted
                            ) {

                                encodedBuffer.position(
                                    bufferInfo.offset
                                )

                                encodedBuffer.limit(
                                    bufferInfo.offset +
                                            bufferInfo.size
                                )

                                muxer.writeSampleData(
                                    muxerVideoTrack,
                                    encodedBuffer,
                                    bufferInfo
                                )
                            }
                        }

                        val eos =
                            (bufferInfo.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        encoder.releaseOutputBuffer(
                            encoderOutputIndex,
                            false
                        )

                        if (eos) {
                            encoderDone = true
                        }
                    }

                    encoderOutputIndex =
                        encoder.dequeueOutputBuffer(
                            bufferInfo,
                            timeoutUs
                        )
                }
            }

            // ====================================================
            // ARRÊT VIDEO
            // ====================================================

            try {
                decoder.stop()
            } catch (_: Exception) {
            }

            try {
                decoder.release()
            } catch (_: Exception) {
            }

            decoder = null

            try {
                encoder.stop()
            } catch (_: Exception) {
            }

            try {
                encoder.release()
            } catch (_: Exception) {
            }

            encoder = null

            extractor.unselectTrack(videoTrack)

            // ====================================================
            // AJOUT AUDIO
            // ====================================================

            if (
                audioTrack != -1 &&
                muxerAudioTrack != -1
            ) {

                extractor.selectTrack(audioTrack)

                val audioBuffer =
                    ByteBuffer.allocate(
                        1 * 1024 * 1024
                    )

                val audioInfo =
                    MediaCodec.BufferInfo()

                while (true) {

                    audioBuffer.clear()

                    val sampleSize =
                        extractor.readSampleData(
                            audioBuffer,
                            0
                        )

                    if (sampleSize < 0) {
                        break
                    }

                    audioInfo.offset = 0
                    audioInfo.size = sampleSize
                    audioInfo.presentationTimeUs =
                        extractor.sampleTime
                    audioInfo.flags =
                        extractor.sampleFlags

                    muxer.writeSampleData(
                        muxerAudioTrack,
                        audioBuffer,
                        audioInfo
                    )

                    extractor.advance()
                }

                extractor.unselectTrack(audioTrack)
            }

            // ====================================================
            // LIBÉRATION
            // ====================================================

            extractor.release()
            extractor = null

            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }

            try {
                muxer.release()
            } catch (_: Exception) {
            }

            muxer = null

            try {
                codecInputSurface.release()
            } catch (_: Exception) {
            }

            codecInputSurface = null

            try {
                decoderSurface.release()
            } catch (_: Exception) {
            }

            decoderSurface = null

            try {
                st.release()
            } catch (_: Exception) {
            }

            surfaceTexture = null

            return true

        } catch (e: Exception) {

            e.printStackTrace()

            try {
                decoder?.stop()
            } catch (_: Exception) {
            }

            try {
                decoder?.release()
            } catch (_: Exception) {
            }

            try {
                encoder?.stop()
            } catch (_: Exception) {
            }

            try {
                encoder?.release()
            } catch (_: Exception) {
            }

            try {
                codecInputSurface?.release()
            } catch (_: Exception) {
            }

            try {
                decoderSurface?.release()
            } catch (_: Exception) {
            }

            try {
                surfaceTexture?.release()
            } catch (_: Exception) {
            }

            try {
                extractor?.release()
            } catch (_: Exception) {
            }

            try {
                muxer?.release()
            } catch (_: Exception) {
            }

            return false
        }
    }


    // ============================================================
    // RECADRAGE PERSONNALISÉ
    // ============================================================

    fun cropToRegion(
        inputPath: String,
        outputPath: String,
        uMin: Float,
        uMax: Float,
        vMin: Float,
        vMax: Float,
        outputWidth: Int,
        outputHeight: Int
    ): Boolean {

        return reformatWithCustomCrop(
            inputPath,
            outputPath,
            outputWidth,
            outputHeight,
            uMin,
            uMax,
            vMin,
            vMax
        )
    }


    // ============================================================
    // RECADRAGE PERSONNALISÉ
    // ============================================================

    private fun reformatWithCustomCrop(
        inputPath: String,
        outputPath: String,
        targetWidth: Int,
        targetHeight: Int,
        uMin: Float,
        uMax: Float,
        vMin: Float,
        vMax: Float
    ): Boolean {

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var codecInputSurface: CodecInputSurface? = null
        var decoderSurface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null

        try {

            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            var videoTrack = -1
            var audioTrack = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {

                val format = extractor.getTrackFormat(i)

                val mime =
                    format.getString(MediaFormat.KEY_MIME)
                        ?: continue

                if (
                    mime.startsWith("video/") &&
                    videoTrack == -1
                ) {

                    videoTrack = i
                    videoFormat = format

                } else if (
                    mime.startsWith("audio/") &&
                    audioTrack == -1
                ) {

                    audioTrack = i
                    audioFormat = format
                }
            }

            if (
                videoTrack == -1 ||
                videoFormat == null
            ) {
                return false
            }

            val srcMime =
                videoFormat.getString(
                    MediaFormat.KEY_MIME
                ) ?: return false

            // ====================================================
            // ENCODEUR
            // ====================================================

            val outFormat =
                MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    targetWidth,
                    targetHeight
                )

            outFormat.setInteger(
                MediaFormat.KEY_BIT_RATE,
                6_000_000
            )

            outFormat.setInteger(
                MediaFormat.KEY_FRAME_RATE,
                30
            )

            outFormat.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL,
                2
            )

            outFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )

            encoder =
                MediaCodec.createEncoderByType(
                    MediaFormat.MIMETYPE_VIDEO_AVC
                )

            encoder.configure(
                outFormat,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            val encoderSurface =
                encoder.createInputSurface()

            encoder.start()

            codecInputSurface =
                CodecInputSurface(encoderSurface)

            codecInputSurface.makeCurrent()

            // ====================================================
            // OPENGL
            // ====================================================

            val textureRender = STextureRender()

            textureRender.surfaceCreated()

            // Protection contre des valeurs incorrectes
            val safeUMin =
                uMin.coerceIn(0f, 1f)

            val safeUMax =
                uMax.coerceIn(0f, 1f)

            val safeVMin =
                vMin.coerceIn(0f, 1f)

            val safeVMax =
                vMax.coerceIn(0f, 1f)

            textureRender.setCropUV(
                safeUMin,
                safeUMax,
                safeVMin,
                safeVMax
            )

            // IMPORTANT :
            // val locale pour éviter le smart cast impossible
            val st = SurfaceTexture(
                textureRender.getTextureId()
            )

            surfaceTexture = st

            val frameLock = Object()

            var frameAvailable = false

            st.setOnFrameAvailableListener {

                synchronized(frameLock) {

                    frameAvailable = true

                    frameLock.notifyAll()
                }
            }

            decoderSurface = Surface(st)

            // ====================================================
            // DECODEUR
            // ====================================================

            decoder =
                MediaCodec.createDecoderByType(
                    srcMime
                )

            decoder.configure(
                videoFormat,
                decoderSurface,
                null,
                0
            )

            decoder.start()

            extractor.selectTrack(videoTrack)

            // ====================================================
            // MUXER
            // ====================================================

            muxer =
                MediaMuxer(
                    outputPath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var muxerStarted = false

            val bufferInfo =
                MediaCodec.BufferInfo()

            val timeoutUs = 10_000L

            var inputDone = false
            var decoderDone = false
            var encoderDone = false

            // ====================================================
            // TRAITEMENT
            // ====================================================

            while (!encoderDone) {

                if (!inputDone) {

                    val inputIndex =
                        decoder.dequeueInputBuffer(
                            timeoutUs
                        )

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            decoder.getInputBuffer(
                                inputIndex
                            )

                        if (inputBuffer != null) {

                            val sampleSize =
                                extractor.readSampleData(
                                    inputBuffer,
                                    0
                                )

                            if (sampleSize < 0) {

                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )

                                inputDone = true

                            } else {

                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    extractor.sampleFlags
                                )

                                extractor.advance()
                            }
                        }
                    }
                }

                if (!decoderDone) {

                    val decoderOutputIndex =
                        decoder.dequeueOutputBuffer(
                            bufferInfo,
                            timeoutUs
                        )

                    if (decoderOutputIndex >= 0) {

                        val eos =
                            (bufferInfo.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        val doRender =
                            bufferInfo.size > 0

                        decoder.releaseOutputBuffer(
                            decoderOutputIndex,
                            doRender
                        )

                        if (doRender) {

                            synchronized(frameLock) {

                                while (!frameAvailable) {
                                    frameLock.wait(1000)
                                }

                                frameAvailable = false
                            }

                            st.updateTexImage()

                            textureRender.drawFrame(
                                st,
                                targetWidth,
                                targetHeight
                            )

                            codecInputSurface.setPresentationTime(
                                bufferInfo.presentationTimeUs * 1000L
                            )

                            codecInputSurface.swapBuffers()
                        }

                        if (eos) {

                            encoder.signalEndOfInputStream()

                            decoderDone = true
                        }
                    }
                }

                // ====================================================
                // ENCODEUR
                // ====================================================

                var encoderOutputIndex =
                    encoder.dequeueOutputBuffer(
                        bufferInfo,
                        timeoutUs
                    )

                while (
                    encoderOutputIndex >= 0 ||
                    encoderOutputIndex ==
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                ) {

                    if (
                        encoderOutputIndex ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                    ) {

                        muxerVideoTrack =
                            muxer.addTrack(
                                encoder.outputFormat
                            )

                        if (audioFormat != null) {

                            muxerAudioTrack =
                                muxer.addTrack(audioFormat)
                        }

                        muxer.start()

                        muxerStarted = true

                    } else {

                        val encodedBuffer =
                            encoder.getOutputBuffer(
                                encoderOutputIndex
                            )

                        if (encodedBuffer != null) {

                            if (
                                (bufferInfo.flags and
                                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            ) {
                                bufferInfo.size = 0
                            }

                            if (
                                bufferInfo.size > 0 &&
                                muxerStarted
                            ) {

                                encodedBuffer.position(
                                    bufferInfo.offset
                                )

                                encodedBuffer.limit(
                                    bufferInfo.offset +
                                            bufferInfo.size
                                )

                                muxer.writeSampleData(
                                    muxerVideoTrack,
                                    encodedBuffer,
                                    bufferInfo
                                )
                            }
                        }

                        val eos =
                            (bufferInfo.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        encoder.releaseOutputBuffer(
                            encoderOutputIndex,
                            false
                        )

                        if (eos) {
                            encoderDone = true
                        }
                    }

                    encoderOutputIndex =
                        encoder.dequeueOutputBuffer(
                            bufferInfo,
                            timeoutUs
                        )
                }
            }

            // ====================================================
            // LIBÉRATION VIDEO
            // ====================================================

            try {
                decoder.stop()
            } catch (_: Exception) {
            }

            try {
                decoder.release()
            } catch (_: Exception) {
            }

            decoder = null

            try {
                encoder.stop()
            } catch (_: Exception) {
            }

            try {
                encoder.release()
            } catch (_: Exception) {
            }

            encoder = null

            extractor.unselectTrack(videoTrack)

            // ====================================================
            // AUDIO
            // ====================================================

            if (
                audioTrack != -1 &&
                muxerAudioTrack != -1
            ) {

                extractor.selectTrack(audioTrack)

                val audioBuffer =
                    ByteBuffer.allocate(
                        1 * 1024 * 1024
                    )

                val audioInfo =
                    MediaCodec.BufferInfo()

                while (true) {

                    audioBuffer.clear()

                    val sampleSize =
                        extractor.readSampleData(
                            audioBuffer,
                            0
                        )

                    if (sampleSize < 0) {
                        break
                    }

                    audioInfo.offset = 0
                    audioInfo.size = sampleSize
                    audioInfo.presentationTimeUs =
                        extractor.sampleTime
                    audioInfo.flags =
                        extractor.sampleFlags

                    muxer.writeSampleData(
                        muxerAudioTrack,
                        audioBuffer,
                        audioInfo
                    )

                    extractor.advance()
                }

                extractor.unselectTrack(audioTrack)
            }

            // ====================================================
            // FIN
            // ====================================================

            extractor.release()
            extractor = null

            if (muxerStarted) {

                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }

            muxer.release()
            muxer = null

            codecInputSurface.release()
            codecInputSurface = null

            decoderSurface?.release()
            decoderSurface = null

            st.release()
            surfaceTexture = null

            return true

        } catch (e: Exception) {

            e.printStackTrace()

            try {
                decoder?.stop()
            } catch (_: Exception) {
            }

            try {
                decoder?.release()
            } catch (_: Exception) {
            }

            try {
                encoder?.stop()
            } catch (_: Exception) {
            }

            try {
                encoder?.release()
            } catch (_: Exception) {
            }

            try {
                codecInputSurface?.release()
            } catch (_: Exception) {
            }

            try {
                decoderSurface?.release()
            } catch (_: Exception) {
            }

            try {
                surfaceTexture?.release()
            } catch (_: Exception) {
            }

            try {
                extractor?.release()
            } catch (_: Exception) {
            }

            try {
                muxer?.release()
            } catch (_: Exception) {
            }

            return false
        }
    }
}