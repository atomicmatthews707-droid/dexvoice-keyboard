package com.tektomic.dexvoice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

/** Haptic tick, optional start/stop sounds, and error toasts for triggers that have no UI of their own. */
object Feedback {
    private var last: VoiceState = VoiceState.Idle
    private var lastToast: String? = null

    fun onState(app: Context, s: VoiceState) {
        try {
            val settings = Settings(app)
            val prev = last
            last = s
            if (s is VoiceState.Recording && prev !is VoiceState.Recording) {
                tick(app, settings); tone(settings, ToneGenerator.TONE_PROP_BEEP)
            } else if (prev is VoiceState.Recording && s !is VoiceState.Recording) {
                tick(app, settings); tone(settings, ToneGenerator.TONE_PROP_ACK)
            }
            if (s is VoiceState.Error) {
                DebugLog.log(app, "ERROR", s.message)
                // The bubble service shows its own toast; this covers hotkey, tile and notification use.
                if (!BubbleService.isRunning && s.message != lastToast) {
                    lastToast = s.message
                    Toast.makeText(app, s.message, Toast.LENGTH_LONG).show()
                }
            } else lastToast = null
        } catch (_: Throwable) {}
    }

    private fun tick(app: Context, settings: Settings) {
        if (!settings.haptics) return
        val v = if (android.os.Build.VERSION.SDK_INT >= 31)
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        v?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun tone(settings: Settings, type: Int) {
        if (!settings.sounds) return
        try {
            val g = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            g.startTone(type, 120)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ g.release() }, 400)
        } catch (_: Throwable) {}
    }
}
