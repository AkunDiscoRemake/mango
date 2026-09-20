package com.robloxvr.app

import com.phonexr.sdk.PhoneXRInput
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Converte as duas mãos do PhoneXR em dedos virtuais no Roblox.
 *
 * Esquema padrão (todos configuráveis em Settings):
 *
 *   MÃO ESQUERDA  ->  ANDAR
 *     - punho fechado  = "segura o joystick virtual" (dedo 0 desce no centro do joystick)
 *     - mover o punho  = empurra o joystick para o lado correspondente
 *     - abrir a mão    = solta (para de andar)
 *
 *   MÃO DIREITA   ->  OLHAR + CLICAR
 *     - mão aberta movendo = arrasta a câmera (dedo 1, só enquanto a mão está "ativa")
 *     - pinça (polegar+indicador) = clique/interagir (dedo 2, toque curto no ponto da mira)
 *     - punho = segurar clique (arrasto/pegar)
 *
 *   OS DOIS PUNHOS JUNTOS  ->  PULAR (toque no botão de pulo)
 *   PALMA NO ROSTO         ->  abrir/fechar o menu do Roblox
 *
 * Cada dedo virtual usa um pointerId fixo, então andar + olhar + clicar coexistem.
 */
class GestureMapper(
    private val input: IInputService,
    private val cfg: Layout
) {
    /** Posições (em pixels) dos controles na tela do Roblox. Calibradas em Settings. */
    data class Layout(
        val screenW: Int,
        val screenH: Int,
        val joyCenterX: Float,
        val joyCenterY: Float,
        val joyRadius: Float,
        val jumpX: Float,
        val jumpY: Float,
        val menuX: Float,
        val menuY: Float,
        val aimX: Float,
        val aimY: Float,
        val lookSensitivity: Float = 1.6f,
        val deadZone: Float = 0.08f
    )

    private companion object {
        const val F_WALK = 0
        const val F_LOOK = 1
        const val F_CLICK = 2
        const val F_MISC = 3
    }

    // --- estado do andar ---
    private var lastWalkX = Float.NaN
    private var lastWalkY = Float.NaN
    private var walking = false
    private var walkOriginX = 0f
    private var walkOriginY = 0f

    // --- estado do olhar ---
    private var looking = false
    private var lastLookX = 0f
    private var lastLookY = 0f
    private var lookCursorX = cfg.aimX
    private var lookCursorY = cfg.aimY

    // --- estado do clique ---
    private var clicking = false

    // --- combos ---
    private var jumpLatch = false
    private var menuLatch = false

    fun update(s: PhoneXRInput.State) {
        // Dois punhos = combo de pular. Nesse caso os punhos NÃO podem também andar/clicar,
        // senão o pulo sairia junto com um "segurar clique" involuntário.
        val jumpCombo = s.left.present && s.right.present && s.left.fist && s.right.fist
        handleWalk(if (jumpCombo) s.left.copy(fist = false, stickX = 0f, stickY = 0f) else s.left)
        handleLookAndClick(if (jumpCombo) s.right.copy(fist = false, pinch = false) else s.right)
        handleCombos(s)
    }

    /** Solta tudo. Chamar ao pausar, perder tracking, ou sair. */
    fun releaseAll() {
        walking = false; looking = false; clicking = false
        jumpLatch = false; menuLatch = false
        input.releaseAll()
    }

    // ---------------------------------------------------------------- andar
    private fun handleWalk(h: PhoneXRInput.Hand) {
        val active = h.present && (h.fist || h.stickMagnitude() > 0.1f)

        if (!active) {
            if (walking) { input.touchUp(F_WALK); walking = false }
            return
        }

        if (!walking) {
            // Ancora o "joystick" na posição atual da mão; movimento relativo a esse ponto.
            walkOriginX = h.x
            walkOriginY = h.y
            walking = true
            lastWalkX = cfg.joyCenterX
            lastWalkY = cfg.joyCenterY
            input.touchDown(F_WALK, cfg.joyCenterX, cfg.joyCenterY)
        }

        var dx: Float
        var dy: Float
        if (h.stickMagnitude() > 0.1f) {
            // Joy-Con: usa o stick direto. stickY positivo = para frente = sobe na tela (y menor).
            dx = h.stickX
            dy = -h.stickY
        } else {
            // Mão: deslocamento em relação ao ponto onde fechou o punho. 0.18 do quadro = curso total.
            dx = (h.x - walkOriginX) / 0.18f
            dy = (h.y - walkOriginY) / 0.18f
        }
        dx = dx.coerceIn(-1f, 1f)
        dy = dy.coerceIn(-1f, 1f)
        if (abs(dx) < cfg.deadZone) dx = 0f
        if (abs(dy) < cfg.deadZone) dy = 0f

        val tx = cfg.joyCenterX + dx * cfg.joyRadius
        val ty = cfg.joyCenterY + dy * cfg.joyRadius
        if (tx != lastWalkX || ty != lastWalkY) {
            lastWalkX = tx; lastWalkY = ty
            input.touchMove(F_WALK, tx, ty)
        }
    }

    // -------------------------------------------------------- olhar e clicar
    private fun handleLookAndClick(h: PhoneXRInput.Hand) {
        if (!h.present) {
            if (looking) { input.touchUp(F_LOOK); looking = false }
            if (clicking) { input.touchUp(F_CLICK); clicking = false }
            return
        }

        // Olhar: enquanto a mão estiver visível e não fechada em punho, a câmera segue a mão.
        // A pinça NÃO solta o dedo da câmera: clicar e olhar coexistem (senão a câmera
        // dá um salto a cada clique, porque o dedo era solto e re-tocado no centro).
        val wantsLook = !h.fist
        if (wantsLook) {
            if (!looking) {
                looking = true
                lastLookX = h.x; lastLookY = h.y
                lookCursorX = cfg.screenW * 0.72f
                lookCursorY = cfg.screenH * 0.50f
                input.touchDown(F_LOOK, lookCursorX, lookCursorY)
            } else {
                val dx = (h.x - lastLookX) * cfg.screenW * cfg.lookSensitivity
                val dy = (h.y - lastLookY) * cfg.screenH * cfg.lookSensitivity
                lastLookX = h.x; lastLookY = h.y
                lookCursorX += dx
                lookCursorY += dy
                // Se o cursor chegar perto da borda, recentra para nunca "acabar o curso".
                if (lookCursorX < cfg.screenW * 0.5f || lookCursorX > cfg.screenW * 0.95f ||
                    lookCursorY < cfg.screenH * 0.1f || lookCursorY > cfg.screenH * 0.9f
                ) {
                    input.touchUp(F_LOOK)
                    lookCursorX = cfg.screenW * 0.72f
                    lookCursorY = cfg.screenH * 0.50f
                    input.touchDown(F_LOOK, lookCursorX, lookCursorY)
                } else {
                    // Mão parada = cursor parado: não gasta um evento a 60 Hz à toa.
                    if (dx != 0f || dy != 0f) input.touchMove(F_LOOK, lookCursorX, lookCursorY)
                }
            }
        } else if (looking) {
            input.touchUp(F_LOOK); looking = false
        }

        // Clicar: pinça (ou punho da direita = segurar).
        val wantsClick = h.pinch || h.fist
        if (wantsClick && !clicking) {
            clicking = true
            input.touchDown(F_CLICK, cfg.aimX, cfg.aimY)
        } else if (!wantsClick && clicking) {
            input.touchUp(F_CLICK); clicking = false
        }
    }

    // ---------------------------------------------------------------- combos
    private fun handleCombos(s: PhoneXRInput.State) {
        // Pular: dois punhos fechados juntos.
        val bothFists = s.left.present && s.right.present && s.left.fist && s.right.fist
        if (bothFists && !jumpLatch) {
            jumpLatch = true
            tapMisc(cfg.jumpX, cfg.jumpY)
        } else if (!bothFists) {
            jumpLatch = false
        }

        // Menu: palma no rosto em qualquer mão.
        val palm = (s.left.present && s.left.palmToFace) || (s.right.present && s.right.palmToFace)
        if (palm && !menuLatch) {
            menuLatch = true
            tapMisc(cfg.menuX, cfg.menuY)
        } else if (!palm) {
            menuLatch = false
        }
    }

    private fun tapMisc(x: Float, y: Float) {
        input.touchDown(F_MISC, x, y)
        input.touchUp(F_MISC)
    }

    private fun PhoneXRInput.Hand.stickMagnitude(): Float =
        max(abs(stickX), abs(stickY)).let { min(it, 1f) }
}
