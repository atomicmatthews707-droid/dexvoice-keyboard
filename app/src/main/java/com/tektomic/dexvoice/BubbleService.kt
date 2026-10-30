package com.tektomic.dexvoice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.provider.Settings as AndroidSettings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foreground service that draws a draggable mic bubble on the display where the user is typing
 * (including the Samsung DeX monitor). The window is not focusable, so tapping it never steals
 * focus from the text field. Also keeps the control notification up to date.
 */
class BubbleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: Settings
    private lateinit var controller: VoiceController
    private lateinit var displayManager: DisplayManager

    private var bubble: BubbleView? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var displayId = Display.DEFAULT_DISPLAY
    private var lastError: String? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = retarget()
        override fun onDisplayRemoved(id: Int) = retarget()
        override fun onDisplayChanged(id: Int) { if (id == displayId) retarget() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settings = Settings(this)
        controller = VoiceController.get(this)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        startForegroundCompat(controller.state.value)
        if (!canDraw()) {
            // Onboarding explains the overlay permission; just keep the notification.
        }
        displayManager.registerDisplayListener(displayListener, null)
        DexVoiceIme.inputStartListener = { retarget(it) }
        scope.launch { controller.state.collect { onState(it) } }
        scope.launch { controller.amplitude.collect { bubble?.setLevel(it) } }
        retarget()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        retarget()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        DexVoiceIme.inputStartListener = null
        try { displayManager.unregisterDisplayListener(displayListener) } catch (_: Exception) {}
        removeBubble()
        scope.cancel()
        super.onDestroy()
    }

    private fun canDraw() = AndroidSettings.canDrawOverlays(this)

    private fun startForegroundCompat(state: VoiceState) {
        val n = ControlNotification.build(this, state)
        try {
            startForeground(
                ControlNotification.NOTIFICATION_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun onState(s: VoiceState) {
        try {
            getSystemService(android.app.NotificationManager::class.java)
                ?.notify(ControlNotification.NOTIFICATION_ID, ControlNotification.build(this, s))
        } catch (_: Exception) {}
        bubble?.setVoiceState(s)
        if (s is VoiceState.Error && s.message != lastError) {
            lastError = s.message
            Toast.makeText(this, s.message, Toast.LENGTH_SHORT).show()
        } else if (s !is VoiceState.Error) lastError = null
    }

    // ---- display handling -------------------------------------------------------------

    /** Display the active keyboard / focused field is on, else the last known, else default. */
    private fun targetDisplayId(ime: DexVoiceIme? = DexVoiceIme.active()): Int {
        val fromIme = ime?.let {
            try { it.display?.displayId } catch (_: Exception) { null }
                ?: try { it.window?.window?.decorView?.display?.displayId } catch (_: Exception) { null }
        }
        val id = fromIme ?: displayId
        return if (displayManager.getDisplay(id) != null) id else Display.DEFAULT_DISPLAY
    }

    private fun retarget(ime: DexVoiceIme? = DexVoiceIme.active()) {
        if (!settings.bubbleEnabled || !canDraw()) { removeBubble(); return }
        val id = targetDisplayId(ime)
        if (bubble != null && id == displayId && bubble?.isAttachedToWindow == true) return
        removeBubble()
        displayId = id
        addBubble(id)
    }

    private fun addBubble(id: Int) {
        val display = displayManager.getDisplay(id) ?: return
        try {
            val windowContext = createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            val wm = windowContext.getSystemService(WindowManager::class.java)
            val size = (56 * resources.displayMetrics.density).toInt()
            val bounds = wm.currentWindowMetrics.bounds
            val saved = settings.bubblePosition(id)
            val lp = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (saved?.first ?: (bounds.width() - size * 2)).coerceIn(0, maxOf(0, bounds.width() - size))
                y = (saved?.second ?: (bounds.height() / 2)).coerceIn(0, maxOf(0, bounds.height() - size))
                title = "DeX Voice bubble"
            }
            val view = BubbleView(windowContext)
            view.contentDescription = "DeX Voice microphone"
            view.setVoiceState(controller.state.value)
            attachTouch(view, lp, wm, id)
            wm.addView(view, lp)
            bubble = view; windowManager = wm; params = lp
        } catch (e: Exception) {
            // e.g. overlay permission revoked or display vanished; stay silent, notification still works
            bubble = null; windowManager = null; params = null
        }
    }

    private fun removeBubble() {
        val v = bubble; val wm = windowManager
        if (v != null && wm != null) try { wm.removeViewImmediate(v) } catch (_: Exception) {}
        bubble = null; windowManager = null; params = null
    }

    private fun attachTouch(view: BubbleView, lp: WindowManager.LayoutParams, wm: WindowManager, id: Int) {
        val slop = 8 * resources.displayMetrics.density
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        view.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = lp.x; startY = lp.y; dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX; val dy = ev.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        val b = wm.currentWindowMetrics.bounds
                        lp.x = (startX + dx).toInt().coerceIn(0, maxOf(0, b.width() - view.width))
                        lp.y = (startY + dy).toInt().coerceIn(0, maxOf(0, b.height() - view.height))
                        try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) settings.setBubblePosition(id, lp.x, lp.y)
                    else controller.toggle()
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        @Volatile var isRunning = false
        fun start(context: Context) {
            context.startForegroundService(Intent(context, BubbleService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
