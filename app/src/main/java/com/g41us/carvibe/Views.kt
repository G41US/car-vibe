package com.g41us.carvibe

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The speedometer arc: a 270° dial with tick marks, filled up to the current speed. */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var accent: Int = Prefs.DEFAULT_ACCENT
        set(v) { field = v; valuePaint.color = v; invalidate() }

    var maxSpeed = 200f
    private var shown = 0f
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#1A2231")
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = accent
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#2A3446")
    }
    private val arcRect = RectF()

    /** Speed in km/h, or a negative number when there is no GPS fix. */
    fun setSpeed(kmh: Float) {
        val target = kmh.coerceIn(0f, maxSpeed)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val unit = size / 120f
        val cx = width / 2f
        val cy = height / 2f

        tickPaint.strokeWidth = 1.6f * unit
        for (i in 0..10) {
            val a = Math.toRadians(135.0 + i * 27.0)
            val inner = (if (i % 5 == 0) 34f else 36f) * unit
            val outer = 39f * unit
            canvas.drawLine(
                cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat(),
                cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat(), tickPaint
            )
        }

        val r = 48f * unit
        arcRect.set(cx - r, cy - r, cx + r, cy + r)
        trackPaint.strokeWidth = 7f * unit
        valuePaint.strokeWidth = 7f * unit
        canvas.drawArc(arcRect, 135f, 270f, false, trackPaint)
        val sweep = 270f * (shown / maxSpeed)
        if (sweep > 0.5f) canvas.drawArc(arcRect, 135f, sweep, false, valuePaint)
    }
}

/**
 * The glowing phone in the centre card. Connected: accent outline, soft
 * pulsing glow and a check mark. Not connected: a dim grey outline.
 */
class PhoneGlowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var accent: Int = Prefs.DEFAULT_ACCENT
        set(v) { field = v; glowBitmap = null; invalidate() }

    var connected: Boolean = true
        set(v) { field = v; glowBitmap = null; updatePulse(); invalidate() }

    var pulse: Boolean = true
        set(v) { field = v; updatePulse() }

    private val density get() = resources.displayMetrics.density
    private var glowBitmap: Bitmap? = null
    private var glowAlpha = 1f
    private var pulseAnim: ValueAnimator? = null

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowDraw = Paint(Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    private fun phoneRect(out: RectF) {
        val w = 98f * density
        val h = 176f * density
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        out.set(left, top, left + w, top + h)
    }

    private fun buildGlow(): Bitmap? {
        if (width == 0 || height == 0) return null
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = RectF()
        phoneRect(r)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Wide soft halo, then a tighter, brighter glow.
        p.color = Ui.withAlpha(accent, 0x30)
        p.maskFilter = BlurMaskFilter(40f * density, BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(r, 24f * density, 24f * density, p)
        p.color = Ui.withAlpha(accent, 0x70)
        p.maskFilter = BlurMaskFilter(18f * density, BlurMaskFilter.Blur.OUTER)
        c.drawRoundRect(r, 24f * density, 24f * density, p)
        return bmp
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glowBitmap = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePulse()
    }

    override fun onDetachedFromWindow() {
        pulseAnim?.cancel()
        pulseAnim = null
        super.onDetachedFromWindow()
    }

    private fun updatePulse() {
        val want = connected && pulse && isAttachedToWindow
        if (want && pulseAnim == null) {
            pulseAnim = ValueAnimator.ofFloat(1f, 0.55f).apply {
                duration = 2400
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { glowAlpha = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (!want) {
            pulseAnim?.cancel()
            pulseAnim = null
            glowAlpha = 1f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val d = density
        phoneRect(rect)

        if (connected) {
            if (glowBitmap == null) glowBitmap = buildGlow()
            glowBitmap?.let {
                glowDraw.alpha = (glowAlpha * 255).toInt()
                canvas.drawBitmap(it, 0f, 0f, glowDraw)
            }
        }

        // Body
        framePaint.color = if (connected) Color.parseColor("#0A1220") else Color.parseColor("#0C121C")
        canvas.drawRoundRect(rect, 24f * d, 24f * d, framePaint)
        strokePaint.strokeWidth = 2f * d
        strokePaint.color = if (connected) accent else Color.parseColor("#46577A")
        val half = strokePaint.strokeWidth / 2f
        canvas.drawRoundRect(
            rect.left + half, rect.top + half, rect.right - half, rect.bottom - half,
            24f * d, 24f * d, strokePaint
        )

        // Screen
        val sl = rect.left + 8f * d
        val st = rect.top + 8f * d
        val sr = rect.right - 8f * d
        val sb = rect.bottom - 8f * d
        if (connected) {
            screenPaint.shader = LinearGradient(0f, st, 0f, sb,
                Color.parseColor("#12233C"), Color.parseColor("#0B1424"), Shader.TileMode.CLAMP)
        } else {
            screenPaint.shader = null
            screenPaint.color = Color.parseColor("#111A28")
        }
        canvas.drawRoundRect(sl, st, sr, sb, 16f * d, 16f * d, screenPaint)

        // Camera pill
        framePaint.color = Color.parseColor("#04060A")
        val cx = rect.centerX()
        canvas.drawRoundRect(cx - 11f * d, st + 9f * d, cx + 11f * d, st + 15f * d, 3f * d, 3f * d, framePaint)

        if (!connected) {
            // A Bluetooth mark, to hint at how to connect.
            linePaint.color = Color.parseColor("#46577A")
            linePaint.strokeWidth = 2.2f * d
            val s = 1.6f * d
            val ox = cx - 12f * s
            val oy = rect.centerY() - 12f * s
            path.reset()
            path.moveTo(ox + 7f * s, oy + 7f * s)
            path.lineTo(ox + 17f * s, oy + 17f * s)
            path.lineTo(ox + 12f * s, oy + 22f * s)
            path.lineTo(ox + 12f * s, oy + 2f * s)
            path.lineTo(ox + 17f * s, oy + 7f * s)
            path.lineTo(ox + 7f * s, oy + 17f * s)
            canvas.drawPath(path, linePaint)
        }

        if (connected) {
            // Check mark in a circle
            linePaint.color = accent
            linePaint.strokeWidth = 2.6f * d
            val cy = rect.centerY()
            val r = 14f * d
            canvas.drawCircle(cx, cy, r, linePaint)
            path.reset()
            val s = r / 9f
            path.moveTo(cx - 4f * s, cy + 0.5f * s)
            path.lineTo(cx - 1.2f * s, cy + 3.3f * s)
            path.lineTo(cx + 4.5f * s, cy - 2.5f * s)
            canvas.drawPath(path, linePaint)

            // Home bar
            framePaint.color = Ui.withAlpha(accent, 0x40)
            canvas.drawRoundRect(cx - 17f * d, sb - 16f * d, cx + 17f * d, sb - 13f * d, 2f * d, 2f * d, framePaint)
        }
    }
}
