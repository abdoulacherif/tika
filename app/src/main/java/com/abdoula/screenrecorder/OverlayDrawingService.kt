package com.abdoula.screenrecorder

import android.app.AlertDialog
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class OverlayDrawingService : Service() {

    private lateinit var windowManager: WindowManager

    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var panelView: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelVisible = false

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

    // ---------- Calque de dessin (plein écran, invisible tant qu'on ne dessine pas) ----------

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

    // ---------- Bulle flottante (ronde) ----------

    private fun addBubble() {
        bubbleView = TextView(this).apply {
            text = "🎬"
            textSize = 24f
            gravity = Gravity.CENTER
            setBackground(resources.getDrawable(R.drawable.bg_bubble, null))
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

        makeBubbleDraggableAndClickable(bubbleView!!, params)

        windowManager.addView(bubbleView, params)
    }

    private fun makeBubbleDraggableAndClickable(view: View, params: WindowManager.LayoutParams) {
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
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Panneau d'outils (s'ouvre/se ferme depuis la bulle) ----------

    private fun togglePanel() {
        if (panelVisible) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView == null) buildPanel()
        repositionPanelIfVisible(forceShow = true)
        panelVisible = true
    }

    private fun hidePanel() {
        panelView?.visibility = View.GONE
        panelVisible = false
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
            setBackgroundColor(Color.parseColor("#EE1E1E1E"))
            setPadding(16, 16, 16, 16)
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
            "🔴" to Color.RED, "🔵" to Color.BLUE, "🟢" to Color.GREEN, "🟡" to Color.YELLOW
        ).forEach { (label, color) ->
            row2.addView(makeButton(label) { drawingView?.currentColor = color })
        }
        panelView?.addView(row2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(makeButton("↩️") { drawingView?.undo() })
        row3.addView(makeButton("🗑️") { drawingView?.clearAll() })
        panelView?.addView(row3)

        val stopButton = Button(this).apply {
            text = "⏹ Arrêter l'enregistrement"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E53935"))
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
        bubbleView?.let { windowManager.removeView(it) }
        panelView?.let { windowManager.removeView(it) }
        drawingView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}