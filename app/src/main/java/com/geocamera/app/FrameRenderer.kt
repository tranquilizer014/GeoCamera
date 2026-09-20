package com.geocamera.app.video

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws each decoded video frame (delivered via a SurfaceTexture as a GL_TEXTURE_EXTERNAL_OES
 * texture) full-screen, then blends a static overlay bitmap on top, into whichever EGL surface
 * is currently current (the encoder's input surface during processing).
 */
class FrameRenderer {

    private val vertexCoords = floatArrayOf(
        -1f, -1f, 0f,
        1f, -1f, 0f,
        -1f, 1f, 0f,
        1f, 1f, 0f
    )
    private val texCoords = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )
    // The overlay Bitmap is uploaded top-down by GLUtils.texImage2D, so its texture needs a
    // vertical flip relative to the plain texCoords above to display right-side up.
    private val overlayTexCoords = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private val vertexBuffer: FloatBuffer = directFloatBuffer(vertexCoords)
    private val texBuffer: FloatBuffer = directFloatBuffer(texCoords)
    private val overlayTexBuffer: FloatBuffer = directFloatBuffer(overlayTexCoords)

    private var oesProgram = 0
    private var oesPositionHandle = 0
    private var oesTexCoordHandle = 0
    private var oesTextureHandle = 0
    private var oesMatrixHandle = 0

    private var flatProgram = 0
    private var flatPositionHandle = 0
    private var flatTexCoordHandle = 0
    private var flatTextureHandle = 0

    private var videoTextureId = 0
    private var overlayTextureId = 0

    lateinit var decoderSurface: Surface
        private set
    private lateinit var surfaceTexture: SurfaceTexture

    private val stMatrix = FloatArray(16)
    private val frameSyncObject = java.lang.Object()
    private var frameAvailable = false

    fun setup(overlayBitmap: Bitmap) {
        oesProgram = GlUtil.createProgram(VERTEX_SHADER, OES_FRAGMENT_SHADER)
        oesPositionHandle = GLES20.glGetAttribLocation(oesProgram, "aPosition")
        oesTexCoordHandle = GLES20.glGetAttribLocation(oesProgram, "aTexCoord")
        oesTextureHandle = GLES20.glGetUniformLocation(oesProgram, "sTexture")
        oesMatrixHandle = GLES20.glGetUniformLocation(oesProgram, "uSTMatrix")

        flatProgram = GlUtil.createProgram(VERTEX_SHADER, FLAT_FRAGMENT_SHADER)
        flatPositionHandle = GLES20.glGetAttribLocation(flatProgram, "aPosition")
        flatTexCoordHandle = GLES20.glGetAttribLocation(flatProgram, "aTexCoord")
        flatTextureHandle = GLES20.glGetUniformLocation(flatProgram, "sTexture")

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        videoTextureId = textures[0]
        overlayTextureId = textures[1]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(videoTextureId)
        surfaceTexture.setOnFrameAvailableListener {
            synchronized(frameSyncObject) {
                frameAvailable = true
                frameSyncObject.notifyAll()
            }
        }
        decoderSurface = Surface(surfaceTexture)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0)
    }

    /** Blocks (with a timeout fallback) until the decoder has delivered a new frame, then binds it. */
    fun awaitNewImage() {
        synchronized(frameSyncObject) {
            var waits = 0
            while (!frameAvailable && waits < 5) {
                frameSyncObject.wait(500)
                waits++
            }
            frameAvailable = false
        }
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(stMatrix)
    }

    fun drawFrame(viewportWidth: Int, viewportHeight: Int) {
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_BLEND)

        // Pass 1: decoded video frame
        GLES20.glUseProgram(oesProgram)
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(oesPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(oesPositionHandle)
        texBuffer.position(0)
        GLES20.glVertexAttribPointer(oesTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glEnableVertexAttribArray(oesTexCoordHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glUniform1i(oesTextureHandle, 0)
        GLES20.glUniformMatrix4fv(oesMatrixHandle, 1, false, stMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(oesPositionHandle)
        GLES20.glDisableVertexAttribArray(oesTexCoordHandle)

        // Pass 2: overlay bitmap, alpha-blended on top
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(flatProgram)
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(flatPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(flatPositionHandle)
        overlayTexBuffer.position(0)
        GLES20.glVertexAttribPointer(flatTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, overlayTexBuffer)
        GLES20.glEnableVertexAttribArray(flatTexCoordHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glUniform1i(flatTextureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(flatPositionHandle)
        GLES20.glDisableVertexAttribArray(flatTexCoordHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        decoderSurface.release()
        surfaceTexture.release()
        GLES20.glDeleteTextures(2, intArrayOf(videoTextureId, overlayTextureId), 0)
    }

    private fun directFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val OES_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            uniform mat4 uSTMatrix;
            void main() {
                gl_FragColor = texture2D(sTexture, (uSTMatrix * vec4(vTexCoord, 0.0, 1.0)).xy);
            }
        """

        private const val FLAT_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
