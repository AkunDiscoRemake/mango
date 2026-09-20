package com.robloxvr.app

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.phonexr.sdk.PhoneXRInput

class VrActivity : android.app.Activity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: StereoRenderer
    private val bridge by lazy { ShizukuBridge(this) }

    private var mapper: GestureMapper? = null
    @Volatile private var running = false
    private var handThread: Thread? = null

    // Resolução do display virtual = um olho. Roblox renderiza aqui.
    private var vdW = 1280
    private var vdH = 720
    private var vdDpi = 320

    private var pendingSurface: Surface? = null
    private var displayId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(dm)
        // Cada olho é metade da largura física; o Roblox renderiza na proporção do olho.
        vdW = dm.widthPixels / 2
        vdH = dm.heightPixels
        vdDpi = dm.densityDpi

        renderer = StereoRenderer { surface ->
            pendingSurface = surface
            runOnUiThread { tryStartDisplay() }
        }
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        setContentView(glView)

        bridge.onReady = { runOnUiThread { tryStartDisplay() } }
        bridge.bind()
    }

    private fun tryStartDisplay() {
        val svc = bridge.service ?: return
        val surface = pendingSurface ?: return
        if (displayId >= 0) return

        renderer.setBufferSize(vdW, vdH)
        displayId = svc.createDisplay(surface, vdW, vdH, vdDpi)
        if (displayId < 0) {
            android.widget.Toast.makeText(
                this, "Falha ao criar o display virtual (veja o README).",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        svc.launchOnDisplay("com.roblox.client", displayId)

        val layout = GestureMapper.Layout(
            screenW = vdW, screenH = vdH,
            joyCenterX = vdW * 0.16f, joyCenterY = vdH * 0.72f, joyRadius = vdH * 0.16f,
            jumpX = vdW * 0.90f, jumpY = vdH * 0.78f,
            menuX = vdW * 0.04f, menuY = vdH * 0.06f,
            aimX = vdW * 0.50f, aimY = vdH * 0.50f
        )
        mapper = GestureMapper(svc, layout)
        startHandLoop()
    }

    private fun startHandLoop() {
        running = true
        handThread = Thread {
            var input: PhoneXRInput? = null
            try {
                input = PhoneXRInput()          // 127.0.0.1:42425
                var lost = 0
                while (running) {
                    val state = input.read()
                    if (state == null) {
                        // 500 ms sem pacote: tracking caiu. Solta tudo para não travar andando.
                        if (++lost >= 1) mapper?.releaseAll()
                    } else {
                        lost = 0
                        mapper?.update(state)
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this,
                        "Não abri a porta 42425 — outro app já lê o PhoneXR.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                try { input?.close() } catch (_: Throwable) { }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    override fun onPause() {
        super.onPause()
        mapper?.releaseAll()
        glView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onDestroy() {
        running = false
        mapper?.releaseAll()
        try { bridge.service?.releaseDisplay() } catch (_: Throwable) { }
        bridge.unbind()
        renderer.release()
        super.onDestroy()
    }
}
