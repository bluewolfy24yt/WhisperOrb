package com.whisperorb.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GeminiOrbCanvasView(context: Context) : View(context) {

    var bgHex: String = "#08080C"
        set(value) {
            field = value
            invalidate()
        }

    var fgHex: String = "#0088FF"
        set(value) {
            field = value
            invalidate()
        }

    var audioLevel: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var orbState: WhisperOrbState = WhisperOrbState.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private val clipPath = Path()
    private val wavePath = Path()
    private val crestPath = Path()
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private fun parseHexColorSafe(hex: String, fallback: Int): Int {
        return try {
            val formatted = if (hex.startsWith("#")) hex else "#$hex"
            Color.parseColor(formatted)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun withAlpha(color: Int, alphaFraction: Float): Int {
        val a = (alphaFraction.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        if (size <= 4f) return

        val cx = w * 0.5f
        val cy = h * 0.5f
        val radius = size * 0.46f

        val phase = (System.currentTimeMillis() % 3600000L).toDouble() / 1000.0
        val effectiveLevel: Float = when (orbState) {
            WhisperOrbState.IDLE -> (0.08 + 0.05 * sin(phase * 2.2)).toFloat()
            WhisperOrbState.LISTENING -> min(1.0f, max(0.14f, audioLevel) + (0.08 * sin(phase * 8.0)).toFloat())
            WhisperOrbState.TRANSCRIBING -> (0.45 + 0.25 * sin(phase * 12.0)).toFloat()
        }

        val isEclipse = bgHex.equals("#050507", true) && fgHex.equals("#050507", true)
        val isPearl = bgHex.equals("#F8F9FA", true) && fgHex.equals("#FFFFFF", true)

        val baseColor = when {
            isEclipse -> Color.parseColor("#050507")
            isPearl -> Color.parseColor("#F8F9FA")
            else -> parseHexColorSafe(bgHex, Color.parseColor("#08080C"))
        }
        val waveColor = when {
            isEclipse -> Color.parseColor("#6E7385")
            isPearl -> Color.parseColor("#94A3B8")
            else -> parseHexColorSafe(fgHex, Color.parseColor("#0088FF"))
        }

        canvas.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 1. Sphère de fond profonde (Trou Noir Cosmique)
        basePaint.shader = null
        basePaint.color = baseColor
        canvas.drawCircle(cx, cy, radius, basePaint)

        // 2. Halo radial volumétrique ancré au bas de l'orbe
        val glowCenterX = cx + radius * 0.14f * sin(phase * 3.4).toFloat()
        val glowCenterY = cy + radius * (0.82f - effectiveLevel * 0.55f)
        val glowRadius = max(radius * 0.4f, radius * (1.15f + effectiveLevel * 0.45f))

        glowPaint.shader = RadialGradient(
            glowCenterX,
            glowCenterY,
            glowRadius,
            intArrayOf(
                withAlpha(waveColor, if (isEclipse) 0.48f else 0.92f),
                withAlpha(waveColor, if (isEclipse) 0.22f else 0.52f),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, glowPaint)

        // 3. Onde sinusoïdale interne 120 Hz + Crête lumineuse
        val left = cx - radius
        val right = cx + radius
        val bottom = cy + radius
        val baseHorizonY = (cy - radius) + (radius * 2f) * (0.76f - effectiveLevel * 0.34f)
        val waveAmp = (radius * 2f) * (0.038f + effectiveLevel * 0.058f)

        wavePath.reset()
        crestPath.reset()
        wavePath.moveTo(left, bottom)
        wavePath.lineTo(left, baseHorizonY)

        val steps = 36
        for (i in 0..steps) {
            val progress = i.toDouble() / steps.toDouble()
            val x = left + (right - left) * progress.toFloat()
            val s1 = sin(progress * Math.PI * 2.0 + phase * 3.8)
            val c2 = cos(progress * Math.PI * 1.5 - phase * 4.2) * 0.45
            val y = baseHorizonY + ((s1 + c2).toFloat() * waveAmp)
            wavePath.lineTo(x, y)
            if (i == 0) {
                crestPath.moveTo(x, y)
            } else {
                crestPath.lineTo(x, y)
            }
        }
        wavePath.lineTo(right, bottom)
        wavePath.close()

        wavePaint.shader = LinearGradient(
            cx,
            baseHorizonY - waveAmp,
            cx,
            bottom,
            intArrayOf(
                withAlpha(waveColor, if (isEclipse) 0.55f else 0.72f),
                withAlpha(waveColor, if (isEclipse) 0.85f else 0.96f),
                withAlpha(Color.WHITE, if (isPearl) 0.90f else 0.28f)
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(wavePath, wavePaint)

        crestPaint.strokeWidth = max(2f, size * 0.028f)
        crestPaint.color = withAlpha(Color.WHITE, if (isPearl) 0.95f else 0.52f)
        canvas.drawPath(crestPath, crestPaint)

        canvas.restore()

        // 4. Anneau périphérique 3D (Rotation Orbitale pendant la transcription)
        borderPaint.strokeWidth = max(2.2f, size * 0.036f)
        if (orbState == WhisperOrbState.TRANSCRIBING) {
            val deg = ((phase * 260.0) % 360.0).toFloat()
            canvas.save()
            canvas.rotate(deg, cx, cy)
            borderPaint.shader = SweepGradient(
                cx,
                cy,
                intArrayOf(
                    withAlpha(waveColor, 0.12f),
                    withAlpha(waveColor, 0.88f),
                    Color.WHITE,
                    withAlpha(waveColor, 0.12f)
                ),
                floatArrayOf(0f, 0.45f, 0.75f, 1f)
            )
            canvas.drawCircle(cx, cy, radius, borderPaint)
            canvas.restore()
        } else {
            borderPaint.shader = LinearGradient(
                cx,
                cy - radius,
                cx,
                cy + radius,
                intArrayOf(
                    withAlpha(Color.WHITE, 0.18f),
                    withAlpha(Color.WHITE, 0.04f),
                    withAlpha(waveColor, 0.65f)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, borderPaint)
        }

        if (isAttachedToWindow && visibility == VISIBLE) {
            val delayMs = if (orbState == WhisperOrbState.IDLE) 36L else 14L
            postInvalidateDelayed(delayMs)
        }
    }
}
