package com.jiotv.androidtv.server

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class JioTvServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverManager = EmbeddedServerManager.getInstance(applicationContext)
        serverManager.startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val serverManager = EmbeddedServerManager.getInstance(applicationContext)
        serverManager.stopServer()
    }

    companion object {
        fun start(context: Context) {
            try {
                val serverManager = EmbeddedServerManager.getInstance(context)
                serverManager.startServer()
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val serverManager = EmbeddedServerManager.getInstance(context)
                serverManager.stopServer()
            } catch (_: Exception) {}
        }
    }
}
