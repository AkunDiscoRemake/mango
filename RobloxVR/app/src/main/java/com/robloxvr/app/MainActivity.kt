package com.robloxvr.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val bridge by lazy { ShizukuBridge(this) }
    private lateinit var status: TextView
    private lateinit var start: Button

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val deadListener = Shizuku.OnBinderDeadListener { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#0f1115"))
        }

        root.addView(TextView(this).apply {
            text = "Roblox VR"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Roblox em Cardboard com as duas mãos (PhoneXR) e Shizuku."
            setTextColor(Color.parseColor("#9aa4b2"))
            setPadding(0, pad / 2, 0, pad)
        })

        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f }
        root.addView(status)

        root.addView(TextView(this).apply {
            setTextColor(Color.parseColor("#9aa4b2"))
            textSize = 13f
            setPadding(0, pad, 0, pad)
            text = """Gestos
• Mão ESQUERDA, punho fechado: andar (mova o punho para os lados)
• Mão DIREITA aberta: olhar (mova a mão)
• Pinça (polegar + indicador): clicar / interagir
• Punho direito: segurar o clique
• Os dois punhos juntos: pular
• Palma virada para o rosto: menu

Antes de jogar: abra o PhoneXR e ligue o rastreamento de mãos."""
        })

        start = Button(this).apply {
            text = "Iniciar VR"
            setOnClickListener { startActivity(Intent(this@MainActivity, VrActivity::class.java)) }
        }
        root.addView(start, LinearLayout.LayoutParams(-1, -2).apply { gravity = Gravity.CENTER })

        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(Color.parseColor("#0f1115")) })

        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val running = bridge.isShizukuRunning()
        val granted = running && bridge.hasPermission()
        val roblox = try {
            packageManager.getPackageInfo("com.roblox.client", 0); true
        } catch (_: PackageManager.NameNotFoundException) { false }

        fun line(ok: Boolean, text: String) = (if (ok) "✅ " else "❌ ") + text
        status.text = listOf(
            line(running, "Shizuku em execução"),
            line(granted, "Permissão do Shizuku concedida"),
            line(roblox, "Roblox instalado")
        ).joinToString("\n")

        start.isEnabled = running && granted && roblox
        if (running && !granted) bridge.requestPermission(1)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        super.onDestroy()
    }
}
