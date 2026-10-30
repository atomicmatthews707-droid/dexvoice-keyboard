package com.tektomic.dexvoice

import android.app.Application
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DexVoiceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLog.install(this)
        // Build the controller after startup so a slow keystore never delays launch.
        Handler(Looper.getMainLooper()).post {
            try {
                val c = VoiceController.get(this)
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                    c.state.collect { Feedback.onState(this@DexVoiceApp, it) }
                }
            } catch (e: Throwable) {
                DebugLog.log(this, "APP", "Controller init failed", e)
            }
        }
    }
}
