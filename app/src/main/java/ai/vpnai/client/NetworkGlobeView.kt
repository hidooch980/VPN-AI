package ai.vpnai.client

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight animated pseudo-3D network globe.
 * This is a dashboard visualization only; orbit points are decorative and
 * must never be interpreted as real servers, routes, latency, or traffic.
 */
internal class NetworkGlobeView(context: Context) : View(context) {
    private val globePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 75, 224, 218)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(125, 94, 164, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 245, 220)
        style = Paint.Style.FILL
        setShadowLayer(dp(7f), 0f, 0f, Color.rgb(57, 225, 210))
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 100, 234, 225)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private var rotation = 0f
    private var animator: ValueAnimator? = null

    init {
        contentDescription = "Animated decorative 3D network globe. No live server or traffic data is represented."
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 28000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    rotation = it.animatedValue as Float
                    invalidate()
                }
            }
        }
        animator?.start()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * 0.5f
        val cy = height * 0.49f
        val radius = min(width * 0.235f, height * 0.405f)
        if (radius <= 0f) return

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius * 1.85f,
                intArrayOf(Color.argb(48, 32, 190, 205), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius * 1.85f, glow)

        val sphere = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - radius * 0.32f, cy - radius * 0.36f, radius * 1.55f,
                intArrayOf(Color.rgb(37, 103, 129), Color.rgb(15, 43, 72), Color.rgb(6, 17, 35)),
                floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius, sphere)

        val save = canvas.save()
        val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.rotate(rotation, cx, cy)

        // Longitudes: progressively compressed ellipses create a spherical projection.
        for (i in -3..3) {
            val factor = cos(i * Math.PI / 7.0).toFloat()
            val halfWidth = radius * factor.coerceAtLeast(0.12f)
            canvas.drawOval(
                RectF(cx - halfWidth, cy - radius, cx + halfWidth, cy + radius),
                gridPaint
            )
        }
        // Latitudes are elliptical to reinforce depth rather than flat circular rings.
        for (i in -2..2) {
            val y = i * radius * 0.34f
            val halfHeight = radius * 0.22f * (1f - kotlin.math.abs(i) * 0.16f)
            canvas.drawOval(
                RectF(cx - radius, cy + y - halfHeight, cx + radius, cy + y + halfHeight),
                gridPaint
            )
        }
        canvas.restoreToCount(save)

        canvas.drawCircle(cx, cy, radius, rimPaint)

        val orbitSave = canvas.save()
        canvas.rotate(-18f, cx, cy)
        val orbit = RectF(cx - radius * 1.62f, cy - radius * 0.48f, cx + radius * 1.62f, cy + radius * 0.48f)
        canvas.drawOval(orbit, orbitPaint)
        val nodeAngle = Math.toRadians((rotation * 1.8f).toDouble())
        val nx = cx + (radius * 1.62f * cos(nodeAngle)).toFloat()
        val ny = cy + (radius * 0.48f * sin(nodeAngle)).toFloat()
        canvas.drawCircle(nx, ny, dp(3.5f), nodePaint)
        canvas.restoreToCount(orbitSave)

        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 185, 255, 246)
            style = Paint.Style.STROKE
            strokeWidth = dp(5f)
        }
        canvas.drawArc(
            RectF(cx - radius + dp(3f), cy - radius + dp(3f), cx + radius - dp(3f), cy + radius - dp(3f)),
            205f, 105f, false, highlight
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
