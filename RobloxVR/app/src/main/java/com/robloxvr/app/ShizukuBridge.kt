package com.robloxvr.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/** Liga o app ao InputService que roda no processo shell via Shizuku. */
class ShizukuBridge(private val context: Context) {

    var service: IInputService? = null
        private set

    var onReady: ((IInputService) -> Unit)? = null

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, InputService::class.java.name)
    ).daemon(false).processNameSuffix("input").debuggable(false).version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (binder.pingBinder()) {
                val s = IInputService.Stub.asInterface(binder)
                service = s
                onReady?.invoke(s)
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }

    fun isShizukuRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun requestPermission(code: Int) {
        try { Shizuku.requestPermission(code) } catch (_: Throwable) { }
    }

    fun bind() {
        try { Shizuku.bindUserService(args, connection) } catch (_: Throwable) { }
    }

    fun unbind() {
        try { service?.releaseAll() } catch (_: Throwable) { }
        try { Shizuku.unbindUserService(args, connection, true) } catch (_: Throwable) { }
        service = null
    }
}
