package com.abdoula.screenrecorder

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

// Cadre de recadrage déplaçable et redimensionnable par les coins, dessiné
// par-dessus l'aperçu de la vidéo.
class CropOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var cropRect = RectF(100f, 100f, 500f, 500f)

    private val dimPaint = Paint().apply { color = Color.parseColor("#AA000000") }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#7C4DFF")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#7C4DFF")
        style = Paint.Style.FILL
    }

    private val handleRadius = 28f
    private var activeHandle = -1 // 0=haut-gauche 1=haut-droite 2=bas-gauche 3=bas-droite 4=déplacer tout
    private var lastX = 0f
    private var lastY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val path = Path()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRect(cropRect, Path.Direction.CCW)
        canvas.drawPath(path, dimPaint)
        canvas.drawRect(cropRect, borderPaint)

        canvas.drawCircle(cropRect.left, cropRect.top, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.top, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadius, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = when {
                    isNear(x, y, cropRect.left, cropRect.top) -> 0
                    isNear(x, y, cropRect.right, cropRect.top) -> 1
                    isNear(x, y, cropRect.left, cropRect.bottom) -> 2
                    isNear(x, y, cropRect.right, cropRect.bottom) -> 3
                    cropRect.contains(x, y) -> 4
                    else -> -1
                }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                when (activeHandle) {
                    0 -> { cropRect.left += dx; cropRect.top += dy }
                    1 -> { cropRect.right += dx; cropRect.top += dy }
                    2 -> { cropRect.left += dx; cropRect.bottom += dy }
                    3 -> { cropRect.right += dx; cropRect.bottom += dy }
                    4 -> { cropRect.offset(dx, dy) }
                }
                lastX = x; lastY = y
                invalidate()
            }
        }
        return true
    }

    private fun isNear(x: Float, y: Float, hx: Float, hy: Float): Boolean {
        val dx = x - hx; val dy = y - hy
        return dx * dx + dy * dy < (handleRadius * 2.5f) * (handleRadius * 2.5f)
    }
}