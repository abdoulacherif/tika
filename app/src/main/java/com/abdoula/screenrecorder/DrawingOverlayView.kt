package com.abdoula.screenrecorder

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class ShapeTool { PEN, ARROW, CIRCLE, RECTANGLE, TEXT }

data class DrawnShape(
    val tool: ShapeTool,
    val path: Path? = null,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f,
    val color: Int = Color.RED
)

class DrawingOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var currentTool: ShapeTool = ShapeTool.ARROW
    var currentColor: Int = Color.RED

    // Appelé automatiquement quand une forme vient d'être terminée,
    // pour que le service puisse redonner la main au téléphone
    var onShapeFinished: (() -> Unit)? = null

    private val shapes = mutableListOf<DrawnShape>()
    private var currentPath: Path? = null
    private var startX = 0f
    private var startY = 0f

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                if (currentTool == ShapeTool.PEN) {
                    currentPath = Path().apply { moveTo(x, y) }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTool == ShapeTool.PEN) {
                    currentPath?.lineTo(x, y)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                when (currentTool) {
                    ShapeTool.PEN -> {
                        currentPath?.let {
                            shapes.add(DrawnShape(ShapeTool.PEN, path = it, color = currentColor))
                        }
                        currentPath = null
                    }
                    else -> {
                        shapes.add(
                            DrawnShape(
                                currentTool,
                                startX = startX, startY = startY,
                                endX = x, endY = y,
                                color = currentColor
                            )
                        )
                    }
                }
                invalidate()
                // La forme est terminée : on redonne automatiquement la main au téléphone
                onShapeFinished?.invoke()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (shape in shapes) {
            paint.color = shape.color
            when (shape.tool) {
                ShapeTool.PEN -> shape.path?.let { canvas.drawPath(it, paint) }
                ShapeTool.CIRCLE -> {
                    val radius = distance(shape.startX, shape.startY, shape.endX, shape.endY)
                    canvas.drawCircle(shape.startX, shape.startY, radius, paint)
                }
                ShapeTool.RECTANGLE -> {
                    canvas.drawRect(shape.startX, shape.startY, shape.endX, shape.endY, paint)
                }
                ShapeTool.ARROW -> drawArrow(canvas, shape.startX, shape.startY, shape.endX, shape.endY, paint)
                ShapeTool.TEXT -> {}
            }
        }

        currentPath?.let {
            paint.color = currentColor
            canvas.drawPath(it, paint)
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun drawArrow(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, paint: Paint) {
        canvas.drawLine(startX, startY, endX, endY, paint)

        val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
        val arrowLength = 30f

        val x1 = endX - arrowLength * cos(angle - Math.PI / 6).toFloat()
        val y1 = endY - arrowLength * sin(angle - Math.PI / 6).toFloat()
        val x2 = endX - arrowLength * cos(angle + Math.PI / 6).toFloat()
        val y2 = endY - arrowLength * sin(angle + Math.PI / 6).toFloat()

        canvas.drawLine(endX, endY, x1, y1, paint)
        canvas.drawLine(endX, endY, x2, y2, paint)
    }

    fun clearAll() {
        shapes.clear()
        invalidate()
    }

    fun undo() {
        if (shapes.isNotEmpty()) {
            shapes.removeAt(shapes.size - 1)
            invalidate()
        }
    }
}