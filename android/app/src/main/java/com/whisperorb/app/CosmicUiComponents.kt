package com.whisperorb.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Composant Switch iOS / Pixel Pro personnalisé, fluide et moderne (remplace le Switch Android par défaut).
 */
class CosmicPillSwitch(
    context: Context,
    initialChecked: Boolean,
    private val activeColorHex: String = "#0A84FF",
    private val onToggle: (Boolean) -> Unit
) : View(context) {

    var isChecked: Boolean = initialChecked
        private set

    private var thumbProgress: Float = if (initialChecked) 1f else 0f
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1f)
        color = Color.parseColor("#24FFFFFF")
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(dpF(3f), 0f, dpF(1.5f), Color.parseColor("#66000000"))
    }
    private val trackRect = RectF()

    init {
        val w = dp(48)
        val h = dp(28)
        layoutParams = LinearLayout.LayoutParams(w, h)
        isClickable = true
        setOnClickListener {
            setCheckedAnimated(!isChecked, notify = true)
        }
    }

    fun setCheckedAnimated(checked: Boolean, notify: Boolean = false) {
        if (this.isChecked == checked) return
        this.isChecked = checked
        animator?.cancel()
        animator = ValueAnimator.ofFloat(thumbProgress, if (checked) 1f else 0f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                thumbProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        if (notify) {
            onToggle(checked)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(48), dp(28))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f

        val offColor = Color.parseColor("#222530")
        val onColor = Color.parseColor(activeColorHex)
        trackPaint.color = blendColors(offColor, onColor, thumbProgress)

        trackRect.set(dpF(1f), dpF(1f), w - dpF(1f), h - dpF(1f))
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        canvas.drawRoundRect(trackRect, radius, radius, borderPaint)

        val thumbRadius = (h - dpF(6f)) / 2f
        val minCx = dpF(3f) + thumbRadius
        val maxCx = w - dpF(3f) - thumbRadius
        val cx = minCx + (maxCx - minCx) * thumbProgress
        val cy = h / 2f

        canvas.drawCircle(cx, cy, thumbRadius, thumbPaint)
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt()
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt()
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun dpF(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }
}

/**
 * Barre de progression dégradée Cosmic Dark Pro pour le téléchargement des modèles GGUF.
 */
class CosmicProgressBar(context: Context) : View(context) {

    var progressFraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D202B")
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, bgPaint)

        val fillW = (w * progressFraction).coerceAtLeast(if (progressFraction > 0.01f) h else 0f)
        if (fillW > 0f) {
            fgPaint.shader = LinearGradient(
                0f, 0f, w, 0f,
                Color.parseColor("#0A84FF"),
                Color.parseColor("#00E5FF"),
                Shader.TileMode.CLAMP
            )
            rect.set(0f, 0f, fillW, h)
            canvas.drawRoundRect(rect, r, r, fgPaint)
        }
    }
}

object CosmicUiFactory {

    fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    fun roundedCardDrawable(
        context: Context,
        fillHex: String = "#11131A",
        radiusDp: Int = 20,
        strokeHex: String = "#1AFFFFFF",
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(context, strokeWidthDp), Color.parseColor(strokeHex))
        }
    }

    fun createSettingSwitchRow(
        context: Context,
        title: String,
        subtitle: String? = null,
        initialChecked: Boolean,
        activeColorHex: String = "#0A84FF",
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            background = roundedCardDrawable(context, "#0C0E14", 14, "#14FFFFFF")
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 12)
            }
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        textCol.addView(titleView)

        if (!subtitle.isNullOrBlank()) {
            val subView = TextView(context).apply {
                text = subtitle
                setTextColor(Color.parseColor("#99FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setPadding(0, dp(context, 2), 0, 0)
            }
            textCol.addView(subView)
        }

        val pillSwitch = CosmicPillSwitch(context, initialChecked, activeColorHex, onToggle)

        row.addView(textCol)
        row.addView(pillSwitch)
        row.setOnClickListener {
            pillSwitch.setCheckedAnimated(!pillSwitch.isChecked, notify = true)
        }
        return row
    }

    fun createSpecBadge(context: Context, label: String, textHex: String = "#CCFFFFFF", bgHex: String = "#16FFFFFF"): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
            background = roundedCardDrawable(context, bgHex, 8, "#00000000", 0)
        }
    }
}
