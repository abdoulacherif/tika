package com.abdoula.screenrecorder

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// Dessine l'image décodée (texture OES) sur la surface de sortie, en ne
// gardant que la portion centrale nécessaire pour remplir le format cible
// sans déformer l'image (comportement "cover" — recadre plutôt qu'étirer).
class STextureRender {

    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)
    private var program = 0
    private var textureId = -1
    private var muMVPMatrixHandle = 0
    private var muSTMatrixHandle = 0
    private var maPositionHandle = 0
    private var maTextureHandle = 0

    private var cropUMin = 0f
    private var cropUMax = 1f
    private var cropVMin = 0f
    private var cropVMax = 1f

    private val vertexData = floatArrayOf(
        -1f, -1f, 0f,
        1f, -1f, 0f,
        -1f, 1f, 0f,
        1f, 1f, 0f
    )
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uSTMatrix;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = (uSTMatrix * aTextureCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        void main() {
            gl_FragColor = texture2D(sTexture, vTextureCoord);
        }
    """.trimIndent()

    init {
        Matrix.setIdentityM(mvpMatrix, 0)
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertexData); position(0) }
        updateTexCoordBuffer()
    }

    private fun updateTexCoordBuffer() {
        val texData = floatArrayOf(
            cropUMin, cropVMin,
            cropUMax, cropVMin,
            cropUMin, cropVMax,
            cropUMax, cropVMax
        )
        texCoordBuffer = ByteBuffer.allocateDirect(texData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(texData); position(0) }
    }

    fun setCropUV(uMin: Float, uMax: Float, vMin: Float, vMax: Float) {
        cropUMin = uMin; cropUMax = uMax; cropVMin = vMin; cropVMax = vMax
        updateTexCoordBuffer()
    }

    fun getTextureId(): Int = textureId

    fun surfaceCreated() {
        program = createProgram(vertexShaderCode, fragmentShaderCode)
        maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        maTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        muMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        muSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun drawFrame(surfaceTexture: SurfaceTexture, viewportWidth: Int, viewportHeight: Int) {
        surfaceTexture.getTransformMatrix(stMatrix)

        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
        GLES20.glEnableVertexAttribArray(maPositionHandle)

        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)
        GLES20.glEnableVertexAttribArray(maTextureHandle)

        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, stMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glFinish()
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vertexShader)
        GLES20.glAttachShader(prog, fragmentShader)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
}