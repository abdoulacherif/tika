package com.abdoula.screenrecorder

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayDrawingService : Service() {

    private lateinit var windowManager: WindowManager
    private var toolbarView: LinearLayout? = null
    private var drawingView: DrawingOverlayView? = null
    private var drawingEnabled = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addDrawingLayer()
        addToolbar()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

    private fun addDrawingLayer() {
        drawingView = DrawingOverlayView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(drawingView, params)
    }

    private fun addToolbar() {
        toolbarView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#E6222222"))
            setPadding(8, 8, 8, 8)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // Poignée de déplacement séparée des boutons : c'est ELLE qu'on fait glisser,
        // les boutons gardent uniquement leur clic (plus de conflit clic/glisser)
        val dragHandle = TextView(this).apply {
            text = "⠿⠿"
            textSize = 20f
            setTextColor(Color.LTGRAY)
            setPadding(20, 20, 24, 20)
        }
        makeDraggable(dragHandle, params)
        toolbarView?.addView(dragHandle)

        val buttons = listOf(
            "✏️" to { setTool(ShapeTool.PEN) },
            "➡️" to { setTool(ShapeTool.ARROW) },
            "⭕" to { setTool(ShapeTool.CIRCLE) },
            "▭" to { setTool(ShapeTool.RECTANGLE) },
            "↩️" to { drawingView?.undo() },
            "🗑️" to { drawingView?.clearAll() },
            "✋" to { toggleDrawingMode() }
        )

        for ((label, action) in buttons) {
            val button = Button(this).apply {
                text = label
                textSize = 18f
                minWidth = 0
                minimumWidth = 0
                setPadding(20, 16, 20, 16)
                setOnClickListener { action() }
            }
            toolbarView?.addView(button)
        }

        // Bouton STOP bien visible et distinct, en rouge
        val stopButton = Button(this).apply {
            text = "⏹ STOP"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E53935"))
            setPadding(28, 16, 28, 16)
            setOnClickListener {
                stopService(Intent(this@OverlayDrawingService, ScreenRecordService::class.java))
                stopSelf()
            }
        }
        toolbarView?.addView(stopButton)

        windowManager.addView(toolbarView, params)
    }

    private fun setTool(tool: ShapeTool) {
        drawingView?.currentTool = tool
        if (!drawingEnabled) toggleDrawingMode()
    }

    private fun toggleDrawingMode() {
        drawingEnabled = !drawingEnabled
        val params = drawingView?.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = if (drawingEnabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(drawingView, params)
    }

    private fun makeDraggable(handle: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(toolbarView, params)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toolbarView?.let { windowManager.removeView(it) }
        drawingView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}