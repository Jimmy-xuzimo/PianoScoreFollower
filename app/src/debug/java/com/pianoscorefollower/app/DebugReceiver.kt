package com.pianoscorefollower.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.pianoscorefollower.DEBUG") return
        val action = intent.getStringExtra("command") ?: return
        val argument = intent.getStringExtra("argument") ?: ""
        Handler(Looper.getMainLooper()).post {
            DebugHooks.command?.invoke(action, argument)
            DebugHooks.scoreCommand?.invoke(action, argument)
        }
    }
}
