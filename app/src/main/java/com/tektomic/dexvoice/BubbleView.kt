package com.tektomic.dexvoice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/** Round mic bubble that shows idle / recording (pulse) / transcribing (spinner) / error. */
class BubbleView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.WHITE
    }
    private val arc = RectF()
    private var state: VoiceState = VoiceState.Idle
    private var level = 0f
    private var spin = 0f
    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { spin = it.animatedValue as Float; invalidate() }
    }
    private val d = resources.displayMetrics.density

    fun setVoiceState(s: VoiceState) {
        state = s
        if (s is VoiceState.Transcribing) { if (!spinner.isStarted) spinner.start() } else spinner.cancel()
        if (s is VoiceState.Error) shake()
        invalidate()
    }

    fun setLevel(l: Float) { level = l; if (state is VoiceState.Recording) invalidate() }

    private fun shake() {
        animate().cancel()
        ObjectShake.run(this)
    }

    override fun onDetachedFromWindow() { spinner.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 4 * d
        val (color, alpha) = when (state) {
            is VoiceState.Recording -> Color.parseColor("#E53935") to 255
            is VoiceState.Transcribing -> Color.parseColor("#FFB300") to 255
            is VoiceState.Error -> Color.parseColor("#FB8C00") to 255
            else -> Color.parseColor("#37474F") to 170
        }
        if (state is VoiceState.Recording) {
            paint.color = Color.parseColor("#E53935"); paint.alpha = 90
            c.drawCircle(cx, cy, r + 4 * d * (0.5f + level * 3f).coerceAtMost(3f), paint)
        }
        paint.color = color; paint.alpha = alpha
        c.drawCircle(cx, cy, r, paint)
        if (state is VoiceState.Transcribing) {
            ring.strokeWidth = 3 * d
            arc.set(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f)
            c.drawArc(arc, spin, 270f, false, ring)
        } else {
            // simple mic glyph
            paint.color = Color.WHITE; paint.alpha = 255
            val w = r * 0.28f
            c.drawRoundRect(cx - w, cy - r * 0.5f, cx + w, cy + r * 0.1f, w, w, paint)
            ring.strokeWidth = 2.2f * d
            arc.set(cx - r * 0.45f, cy - r * 0.3f, cx + r * 0.45f, cy + r * 0.3f)
            c.drawArc(arc, 0f, 180f, false, ring)
            c.drawLine(cx, cy + r * 0.3f, cx, cy + r * 0.52f, ring)
        }
    }

    private object ObjectShake {
        fun run(v: View) {
            val dx = 8 * v.resources.displayMetrics.density
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 350
                addUpdateListener {
                    val t = it.animatedValue as Float
                    v.translationX = (kotlin.math.sin(t * 6 * Math.PI) * dx * (1 - t)).toFloat()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) { v.translationX = 0f }
                })
                start()
            }
        }
    }
}
