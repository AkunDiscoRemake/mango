package com.robloxvr.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.view.Surface
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties

/**
 * Roda no processo do Shizuku (uid shell), que tem a permissão INJECT_EVENTS.
 * Injeta MotionEvents multitoque reais: ~1 ms por evento, ao contrário de `input tap`
 * (que sobe um processo por comando e leva 100-300 ms).
 *
 * Mantém o estado de até 10 dedos e reenvia um MotionEvent coerente a cada mudança,
 * então "andar com um dedo e olhar com outro" funciona ao mesmo tempo.
 */
class InputService : IInputService.Stub() {

    private val inputManager: Any = Class.forName("android.hardware.input.InputManager")
        .getMethod("getInstance").invoke(null)!!

    private val injectMethod = InputManager::class.java.getMethod(
        "injectInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType
    )

    private class Finger(var x: Float, var y: Float)

    private val fingers = LinkedHashMap<Int, Finger>()
    private var downTime = 0L
    private val lock = Any()

    override fun destroy() {
        releaseAll()
        releaseDisplay()
        Process.killProcess(Process.myPid())
    }

    override fun touchDown(pointerId: Int, x: Float, y: Float) = synchronized(lock) {
        if (fingers.containsKey(pointerId)) {
            touchMoveLocked(pointerId, x, y); return
        }
        val first = fingers.isEmpty()
        if (first) downTime = SystemClock.uptimeMillis()
        fingers[pointerId] = Finger(x, y)
        val index = fingers.keys.indexOf(pointerId)
        val action = if (first) MotionEvent.ACTION_DOWN
        else MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        inject(action)
    }

    override fun touchMove(pointerId: Int, x: Float, y: Float) = synchronized(lock) {
        touchMoveLocked(pointerId, x, y)
    }

    private fun touchMoveLocked(pointerId: Int, x: Float, y: Float) {
        val finger = fingers[pointerId] ?: return
        finger.x = x; finger.y = y
        inject(MotionEvent.ACTION_MOVE)
    }

    override fun touchUp(pointerId: Int) {
        synchronized(lock) {
            if (!fingers.containsKey(pointerId)) return
            val index = fingers.keys.indexOf(pointerId)
            val action = if (fingers.size == 1) MotionEvent.ACTION_UP
            else MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            inject(action)
            fingers.remove(pointerId)
        }
    }

    override fun releaseAll() = synchronized(lock) {
        // Solta do último para o primeiro; o último dispara ACTION_UP.
        for (id in fingers.keys.toList().asReversed()) {
            val index = fingers.keys.indexOf(id)
            val action = if (fingers.size == 1) MotionEvent.ACTION_UP
            else MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            inject(action)
            fingers.remove(id)
        }
    }


    // ------------------------------------------------------------ display virtual
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile private var targetDisplayId = -1

    /**
     * Flags ocultas do framework (não existem no SDK público, então vão por valor):
     *   1 shl 1  PUBLIC                     - visível a outros apps
     *   1 shl 3  OWN_CONTENT_ONLY           - não espelha a tela principal
     *   1 shl 6  SUPPORTS_TOUCH             - aceita toque
     *   1 shl 7  ROTATES_WITH_CONTENT
     *   1 shl 10 TRUSTED                    - pode hospedar activities de outros apps
     *   1 shl 11 OWN_DISPLAY_GROUP          - grupo próprio, não apaga com a tela principal
     * Só um processo com privilégio de sistema/shell consegue setar TRUSTED e
     * OWN_DISPLAY_GROUP; por isso isto vive aqui e não no app.
     */
    override fun createDisplay(surface: Surface, width: Int, height: Int, dpi: Int): Int =
        synchronized(lock) {
            try {
                releaseDisplay()
                val ctx = currentContext() ?: return -1
                val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val flags = (1 shl 1) or (1 shl 3) or (1 shl 6) or (1 shl 7) or (1 shl 10) or (1 shl 11)
                virtualDisplay = dm.createVirtualDisplay(
                    "roblox-vr", width, height, dpi, surface, flags
                )
                val id = virtualDisplay?.display?.displayId ?: -1
                targetDisplayId = id
                id
            } catch (t: Throwable) {
                -1
            }
        }

    override fun launchOnDisplay(packageName: String, displayId: Int) {
        // `am start --display` é o caminho suportado para colocar uma activity num display.
        // Usa o launcher intent do pacote e resolve o componente via `cmd package`.
        Thread {
            try {
                val resolve = Runtime.getRuntime().exec(
                    arrayOf("sh", "-c", "cmd package resolve-activity --brief $packageName | tail -n 1")
                )
                val component = resolve.inputStream.bufferedReader().readText().trim()
                resolve.waitFor()
                if (component.contains("/")) {
                    Runtime.getRuntime().exec(
                        arrayOf("sh", "-c", "am start --display $displayId -n $component")
                    ).waitFor()
                }
            } catch (_: Throwable) { }
        }.start()
    }

    override fun setTargetDisplay(displayId: Int) { targetDisplayId = displayId }

    override fun releaseDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        targetDisplayId = -1
    }

    /** Contexto de sistema disponível no processo shell do Shizuku. */
    private fun currentContext(): Context? = try {
        val at = Class.forName("android.app.ActivityThread")
        val thread = at.getMethod("systemMain").invoke(null)
        at.getMethod("getSystemContext").invoke(thread) as Context
    } catch (_: Throwable) { null }

    private fun inject(action: Int) {
        val count = fingers.size
        if (count == 0) return
        val props = arrayOfNulls<PointerProperties>(count)
        val coords = arrayOfNulls<PointerCoords>(count)
        var i = 0
        for ((id, f) in fingers) {
            props[i] = PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
            coords[i] = PointerCoords().apply { x = f.x; y = f.y; pressure = 1f; size = 1f }
            i++
        }
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, count,
            props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            val display = targetDisplayId
            if (display >= 0) {
                MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
                    .invoke(event, display)
            }
            // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
            injectMethod.invoke(inputManager, event, 0)
        } catch (_: Throwable) {
        } finally {
            event.recycle()
        }
    }
}
