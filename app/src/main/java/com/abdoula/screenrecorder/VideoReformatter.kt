package com.abdoula.screenrecorder

import android.graphics.SurfaceTexture
import android.media.*
import android.view.Surface
import java.nio.ByteBuffer

object VideoReformatter {

    // Reformate la vidéo vers un format cible (carré, vertical, horizontal)
    // en recadrant l'image pour la remplir sans la déformer. C'est un vrai
    // retraitement image par image — plus lent que la compression ou la
    // fusion, à réserver aux vidéos courtes sur les téléphones d'entrée de
    // gamme.
    fun reformat(inputPath: String, outputPath: String, targetWidth: Int, targetHeight: Int): Boolean {
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var codecInputSurface: CodecInputSurface? = null
        var decoderSurface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null

        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            var videoTrack = -1
            var audioTrack = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrack == -1) { videoTrack = i; videoFormat = format }
                else if (mime.startsWith("audio/") && audioTrack == -1) { audioTrack = i; audioFormat = format }
            }
            if (videoTrack == -1 || videoFormat == null) return false

            val srcWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val srcHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val srcMime = videoFormat.getString(MediaFormat.KEY_MIME)!!

// Recadrage sur une zone précise (au lieu du centrage automatique de
    // reformat()) : uMin/uMax/vMin/vMax sont les coordonnées normalisées
    // (0 à 1) de la zone à garder, calculées depuis le rectangle choisi
    // par l'utilisateur sur l'aperçu.
    fun cropToRegion(inputPath: String, outputPath: String, uMin: Float, uMax: Float, vMin: Float, vMax: Float, outputWidth: Int, outputHeight: Int): Boolean {
        return reformatWithCustomCrop(inputPath, outputPath, outputWidth, outputHeight, uMin, uMax, vMin, vMax)
    }

    private fun reformatWithCustomCrop(inputPath: String, outputPath: String, targetWidth: Int, targetHeight: Int, uMin: Float, uMax: Float, vMin: Float, vMax: Float): Boolean {
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var codecInputSurface: CodecInputSurface? = null
        var decoderSurface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null

        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            var videoTrack = -1
            var audioTrack = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrack == -1) { videoTrack = i; videoFormat = format }
                else if (mime.startsWith("audio/") && audioTrack == -1) { audioTrack = i; audioFormat = format }
            }
            if (videoTrack == -1 || videoFormat == null) return false
            val srcMime = videoFormat.getString(MediaFormat.KEY_MIME)!!

            val outFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight)
            outFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            outFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            outFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            outFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderSurface = encoder.createInputSurface()
            encoder.start()
            codecInputSurface = CodecInputSurface(encoderSurface)
            codecInputSurface.makeCurrent()

            val textureRender = STextureRender()
            textureRender.surfaceCreated()
            textureRender.setCropUV(uMin, uMax, vMin, vMax)

            surfaceTexture = SurfaceTexture(textureRender.getTextureId())
            val frameLock = Object()
            var frameAvailable = false
            surfaceTexture.setOnFrameAvailableListener {
                synchronized(frameLock) { frameAvailable = true; frameLock.notifyAll() }
            }
            decoderSurface = Surface(surfaceTexture)

            decoder = MediaCodec.createDecoderByType(srcMime)
            decoder.configure(videoFormat, decoderSurface, null, 0)
            decoder.start()
            extractor.selectTrack(videoTrack)

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerVideoTrack = -1
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

                if (!decoderDone) {
                    val decOutIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (decOutIndex >= 0) {
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val doRender = bufferInfo.size > 0
                        decoder.releaseOutputBuffer(decOutIndex, doRender)

                        if (doRender) {
                            synchronized(frameLock) {
                                while (!frameAvailable) frameLock.wait(1000)
                                frameAvailable = false
                            }
                            surfaceTexture.updateTexImage()
                            textureRender.drawFrame(surfaceTexture, targetWidth, targetHeight)
                            codecInputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                            codecInputSurface.swapBuffers()
                        }

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
                        if (audioFormat != null) muxerAudioTrack = muxer.addTrack(audioFormat)
                        muxer.start()
                        muxerStarted = true
                    } else {
                        val encodedBuffer = encoder.getOutputBuffer(encOutIndex)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bufferInfo.size = 0
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
            extractor.unselectTrack(videoTrack)

            if (audioTrack != -1 && muxerAudioTrack != -1) {
                extractor.selectTrack(audioTrack)
                val audioBuffer = ByteBuffer.allocate(1 * 1024 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                while (true) {
                    val sampleSize = extractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break
                    audioInfo.offset = 0
                    audioInfo.size = sampleSize
                    audioInfo.presentationTimeUs = extractor.sampleTime
                    audioInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioInfo)
                    extractor.advance()
                }
            }

            extractor.release()
            muxer.stop()
            muxer.release()
            codecInputSurface.release()
            return true
        } catch (e: Exception) {
            try { decoder?.stop(); decoder?.release() } catch (ignored: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (ignored: Exception) {}
            try { codecInputSurface?.release() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
            return false
        }
    }

            // ---------- Encodeur (vers la surface OpenGL) ----------
            val outFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight)
            outFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            outFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            outFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            outFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderSurface = encoder.createInputSurface()
            encoder.start()
            codecInputSurface = CodecInputSurface(encoderSurface)
            codecInputSurface.makeCurrent()

            val textureRender = STextureRender()
            textureRender.surfaceCreated()

            // Calcule la portion à recadrer pour remplir le format cible sans déformer l'image
            val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()
            val dstAspect = targetWidth.toFloat() / targetHeight.toFloat()
            if (srcAspect > dstAspect) {
                val cropWidthUV = dstAspect / srcAspect
                val uMin = (1f - cropWidthUV) / 2f
                textureRender.setCropUV(uMin, 1f - uMin, 0f, 1f)
            } else {
                val cropHeightUV = srcAspect / dstAspect
                val vMin = (1f - cropHeightUV) / 2f
                textureRender.setCropUV(0f, 1f, vMin, 1f - vMin)
            }

            surfaceTexture = SurfaceTexture(textureRender.getTextureId())
            val frameLock = Object()
            var frameAvailable = false
            surfaceTexture.setOnFrameAvailableListener {
                synchronized(frameLock) {
                    frameAvailable = true
                    frameLock.notifyAll()
                }
            }
            decoderSurface = Surface(surfaceTexture)

            // ---------- Décodeur (vers la texture OpenGL) ----------
            decoder = MediaCodec.createDecoderByType(srcMime)
            decoder.configure(videoFormat, decoderSurface, null, 0)
            decoder.start()
            extractor.selectTrack(videoTrack)

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerVideoTrack = -1
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

                if (!decoderDone) {
                    val decOutIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (decOutIndex >= 0) {
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val doRender = bufferInfo.size > 0
                        decoder.releaseOutputBuffer(decOutIndex, doRender)

                        if (doRender) {
                            synchronized(frameLock) {
                                while (!frameAvailable) frameLock.wait(1000)
                                frameAvailable = false
                            }
                            surfaceTexture.updateTexImage()
                            textureRender.drawFrame(surfaceTexture, targetWidth, targetHeight)
                            codecInputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                            codecInputSurface.swapBuffers()
                        }

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
                        if (audioFormat != null) muxerAudioTrack = muxer.addTrack(audioFormat)
                        muxer.start()
                        muxerStarted = true
                    } else {
                        val encodedBuffer = encoder.getOutputBuffer(encOutIndex)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bufferInfo.size = 0
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
            extractor.unselectTrack(videoTrack)

            if (audioTrack != -1 && muxerAudioTrack != -1) {
                extractor.selectTrack(audioTrack)
                val audioBuffer = ByteBuffer.allocate(1 * 1024 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                while (true) {
                    val sampleSize = extractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break
                    audioInfo.offset = 0
                    audioInfo.size = sampleSize
                    audioInfo.presentationTimeUs = extractor.sampleTime
                    audioInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioInfo)
                    extractor.advance()
                }
            }

            extractor.release()
            muxer.stop()
            muxer.release()
            codecInputSurface.release()
            return true
        } catch (e: Exception) {
            try { decoder?.stop(); decoder?.release() } catch (ignored: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (ignored: Exception) {}
            try { codecInputSurface?.release() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
            return false
        }
    }
}