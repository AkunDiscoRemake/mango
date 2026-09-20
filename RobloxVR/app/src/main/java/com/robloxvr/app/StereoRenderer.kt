package com.robloxvr.app

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Recebe o vídeo do display virtual (onde o Roblox roda) como textura externa e desenha
 * duas cópias lado a lado, uma por olho, com correção de distorção de lente (barril) para
 * óculos Cardboard.
 *
 * Como o Roblox é um jogo 2D (uma imagem só), os dois olhos veem o mesmo quadro: não há
 * profundidade estéreo real. O ganho de "VR" é: tela grande à frente + look por giroscópio
 * (opcional) + mãos. O parâmetro [eyeSeparation] desloca levemente cada cópia para o
 * conforto de convergência.
 */
class StereoRenderer(
    private val onSurfaceReady: (Surface) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    /** Coeficientes de distorção de lente (k1, k2). Ajustáveis em Settings. */
    @Volatile var k1 = 0.22f
    @Volatile var k2 = 0.24f
    /** Deslocamento horizontal de cada olho em fração da largura do olho. */
    @Volatile var eyeSeparation = 0.0f
    /** Zoom da imagem dentro de cada olho (1 = enche o olho). */
    @Volatile var zoom = 0.92f

    private var oesTexture = 0
    private var program = 0
    private var surfaceTexture: SurfaceTexture? = null
    @Volatile private var frameAvailable = false
    private val texMatrix = FloatArray(16)

    private var viewW = 1
    private var viewH = 1

    private val quad: java.nio.FloatBuffer = ByteBuffer
        .allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                -1f,  1f, 0f, 1f,
                 1f,  1f, 1f, 1f
            )); position(0)
        }

    private val vertexShader = """
        attribute vec4 aPos;
        attribute vec2 aTex;
        varying vec2 vUv;          // 0..1 dentro do olho
        void main() {
            vUv = aTex;
            gl_Position = aPos;
        }
    """

    private val fragmentShader = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vUv;
        uniform samplerExternalOES uTex;
        uniform mat4 uTexMatrix;
        uniform float uK1;
        uniform float uK2;
        uniform float uZoom;
        uniform float uShift;
        void main() {
            // centro do olho em (0,0), alcance -1..1
            vec2 p = (vUv - 0.5) * 2.0;
            float r2 = dot(p, p);
            // distorção de barril inversa: puxa a imagem para compensar a lente
            float f = 1.0 + uK1 * r2 + uK2 * r2 * r2;
            vec2 q = p * f / uZoom;
            q.x += uShift;
            if (abs(q.x) > 1.0 || abs(q.y) > 1.0) {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
            }
            vec2 uv = q * 0.5 + 0.5;
            vec4 st = uTexMatrix * vec4(uv, 0.0, 1.0);
            gl_FragColor = texture2D(uTex, st.xy);
        }
    """

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTexture = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        program = buildProgram(vertexShader, fragmentShader)

        surfaceTexture = SurfaceTexture(oesTexture).also {
            it.setOnFrameAvailableListener(this)
        }
        onSurfaceReady(Surface(surfaceTexture))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width; viewH = height
    }

    /** Tamanho que o display virtual deve ter: um olho, em proporção de tela cheia. */
    fun setBufferSize(w: Int, h: Int) { surfaceTexture?.setDefaultBufferSize(w, h) }

    override fun onFrameAvailable(st: SurfaceTexture?) { frameAvailable = true }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        if (frameAvailable) {
            frameAvailable = false
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
        }
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, texMatrix, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uK1"), k1)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uK2"), k2)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uZoom"), zoom)

        val posLoc = GLES20.glGetAttribLocation(program, "aPos")
        val texLoc = GLES20.glGetAttribLocation(program, "aTex")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(texLoc)

        val half = viewW / 2
        for (eye in 0..1) {
            GLES20.glViewport(eye * half, 0, half, viewH)
            val shift = if (eye == 0) eyeSeparation else -eyeSeparation
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uShift"), shift)
            quad.position(0)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, quad)
            quad.position(2)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            return id
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }
}
