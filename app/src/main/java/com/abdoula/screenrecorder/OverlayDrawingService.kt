package com.abdoula.screenrecorder

import android.app.AlertDialog
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class OverlayDrawingService : Service() {

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleMinimized = false

    private var panelView: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelVisible = false
    private val autoHideRunnable = Runnable { hidePanel() }

    private var drawingView: DrawingOverlayView? = null
    private var drawingEnabled = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addDrawingLayer()
        addBubble()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

    // ---------- Calque de dessin ----------

    private fun addDrawingLayer() {
        drawingView = DrawingOverlayView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            onShapeFinished = { disableDrawingMode() }
            onTextRequested = { _, _ -> showTextInputDialog() }
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

    // ---------- Bulle flottante ----------

    private fun addBubble() {
        bubbleView = TextView(this).apply {
            text = "🎬"
            textSize = 24f
            gravity = Gravity.CENTER
            background = gradientOval(intArrayOf(Color.parseColor("#7C4DFF"), Color.parseColor("#5E35B1")))
        }

        val params = WindowManager.LayoutParams(
            140, 140,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 300
        bubbleParams = params

        makeBubbleInteractive(bubbleView!!, params)

        windowManager.addView(bubbleView, params)
    }

    private fun gradientOval(colors: IntArray): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.OVAL
        }
    }

    private fun gradientRoundedRect(colors: IntArray, radius: Float): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
        }
    }

    private var lastTapTime = 0L

    private fun makeBubbleInteractive(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, params)
                    repositionPanelIfVisible()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300) {
                            // Double-tap détecté : on minimise/restaure la bulle
                            toggleMinimized()
                            lastTapTime = 0
                        } else {
                            lastTapTime = now
                            if (bubbleMinimized) {
                                restoreBubble()
                            } else {
                                togglePanel()
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Minimiser la bulle (double-tap) : elle devient un petit onglet discret ----------

    private fun toggleMinimized() {
        if (bubbleMinimized) restoreBubble() else minimizeBubble()
    }

    private fun minimizeBubble() {
        hidePanel()
        bubbleMinimized = true
        bubbleView?.apply {
            text = "▸"
            textSize = 16f
            background = gradientRoundedRect(intArrayOf(Color.parseColor("#997C4DFF"), Color.parseColor("#995E35B1")), 24f)
        }
        val params = bubbleParams ?: return
        params.width = 50
        params.x = 0
        windowManager.updateViewLayout(bubbleView, params)
    }

    private fun restoreBubble() {
        bubbleMinimized = false
        bubbleView?.apply {
            text = "🎬"
            textSize = 24f
            background = gradientOval(intArrayOf(Color.parseColor("#7C4DFF"), Color.parseColor("#5E35B1")))
        }
        val params = bubbleParams ?: return
        params.width = 140
        params.x = 20
        windowManager.updateViewLayout(bubbleView, params)
    }

    // ---------- Panneau d'outils ----------

    private fun togglePanel() {
        if (panelVisible) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView == null) buildPanel()
        repositionPanelIfVisible(forceShow = true)
        panelVisible = true
        scheduleAutoHide()
    }

    private fun hidePanel() {
        panelView?.visibility = View.GONE
        panelVisible = false
        mainHandler.removeCallbacks(autoHideRunnable)
    }

    private fun scheduleAutoHide() {
        mainHandler.removeCallbacks(autoHideRunnable)
        mainHandler.postDelayed(autoHideRunnable, 4000)
    }

    private fun repositionPanelIfVisible(forceShow: Boolean = false) {
        if (!panelVisible && !forceShow) return
        val bp = bubbleParams ?: return
        val pp = panelParams ?: return
        pp.x = bp.x + 160
        pp.y = bp.y
        panelView?.visibility = View.VISIBLE
        windowManager.updateViewLayout(panelView, pp)
    }

    private fun buildPanel() {
        panelView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientRoundedRect(intArrayOf(Color.parseColor("#EE1E1E1E"), Color.parseColor("#EE2A1B45")), 28f)
            setPadding(20, 20, 20, 20)
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "✏️" to { setTool(ShapeTool.PEN) },
            "➡️" to { setTool(ShapeTool.ARROW) },
            "⭕" to { setTool(ShapeTool.CIRCLE) },
            "▭" to { setTool(ShapeTool.RECTANGLE) },
            "🔤" to { setTool(ShapeTool.TEXT) }
        ).forEach { (label, action) -> row1.addView(makeButton(label, action)) }
        panelView?.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "🔴" to Color.RED, "🔵" to Color.parseColor("#2196F3"),
            "🟢" to Color.parseColor("#4CAF50"), "🟡" to Color.parseColor("#FFC107")
        ).forEach { (label, color) ->
            row2.addView(makeButton(label) { drawingView?.currentColor = color })
        }
        panelView?.addView(row2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(makeButton("↩️") { drawingView?.undo() })
        row3.addView(makeButton("🗑️") { drawingView?.clearAll() })
        row3.addView(makeButton("➖") { minimizeBubble() })
        panelView?.addView(row3)

        val stopButton = Button(this).apply {
            text = "⏹ Arrêter l'enregistrement"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = gradientRoundedRect(intArrayOf(Color.parseColor("#E53935"), Color.parseColor("#B71C1C")), 16f)
            setOnClickListener {
                stopService(Intent(this@OverlayDrawingService, ScreenRecordService::class.java))
                stopSelf()
            }
        }
        panelView?.addView(stopButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        panelParams = params

        windowManager.addView(panelView, params)
        panelView?.visibility = View.GONE
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            minWidth = 0
            minimumWidth = 0
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                action()
                hidePanel()
            }
        }
    }

    private fun showTextInputDialog() {
        val input = EditText(this).apply {
            hint = "Ton texte…"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Ajouter du texte")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                drawingView?.addTextShape(input.text.toString())
                disableDrawingMode()
            }
            .setNegativeButton("Annuler") { _, _ -> disableDrawingMode() }
            .create()

        dialog.window?.setType(overlayType())
        dialog.show()
    }

    private fun setTool(tool: ShapeTool) {
        drawingView?.currentTool = tool
        enableDrawingMode()
    }

    private fun enableDrawingMode() {
        drawingEnabled = true
        updateDrawingTouchability()
    }

    private fun disableDrawingMode() {
        drawingEnabled = false
        updateDrawingTouchability()
    }

    private fun updateDrawingTouchability() {
        val params = drawingView?.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = if (drawingEnabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(drawingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(autoHideRunnable)
        bubbleView?.let { windowManager.removeView(it) }
        panelView?.let { windowManager.removeView(it) }
        drawingView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}