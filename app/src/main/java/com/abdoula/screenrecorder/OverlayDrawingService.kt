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
import android.widget.ImageButton
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
    private var pauseButton: ImageButton? = null

    private var drawingView: DrawingOverlayView? = null
    private var drawingEnabled = false
    private var drawingLayerAttached = true

    private var watermarkView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addDrawingLayer()
        if (!SettingsManager.isBubbleHiddenDuringRecording(this)) addBubble()
        addWatermarkIfEnabled()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

    private fun secureFlag(): Int = 0

    private fun addWatermarkIfEnabled() {
        if (!SettingsManager.isWatermarkEnabled(this)) return

        watermarkView = TextView(this).apply {
            text = SettingsManager.getWatermarkText(this@OverlayDrawingService)
            textSize = 12f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            setBackgroundColor(Color.parseColor("#40000000"))
            setPadding(16, 8, 16, 8)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = 20
        params.y = 20

        windowManager.addView(watermarkView, params)
    }

    private fun buildDrawingLayerParams(touchable: Boolean): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            if (touchable) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or secureFlag()
            } else {
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    secureFlag()
            },
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        return params
    }

    private fun addDrawingLayer() {
        drawingView = DrawingOverlayView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            onShapeFinished = { disableDrawingMode() }
            onTextRequested = { _, _ -> showTextInputDialog() }
        }

        val params = buildDrawingLayerParams(touchable = false)
        windowManager.addView(drawingView, params)
        drawingLayerAttached = true
    }

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or secureFlag(),
            PixelFormat.TRANSLUCENT
        )

        when (SettingsManager.getBubblePosition(this)) {
            "top_left" -> { params.gravity = Gravity.TOP or Gravity.START; params.x = 20; params.y = 300 }
            "top_right" -> { params.gravity = Gravity.TOP or Gravity.END; params.x = 20; params.y = 300 }
            "bottom_left" -> { params.gravity = Gravity.BOTTOM or Gravity.START; params.x = 20; params.y = 300 }
            "bottom_right" -> { params.gravity = Gravity.BOTTOM or Gravity.END; params.x = 20; params.y = 300 }
            else -> { params.gravity = Gravity.TOP or Gravity.START; params.x = 20; params.y = 300 }
        }
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

    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false

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
                    longPressTriggered = false

                    val runnable = Runnable {
                        longPressTriggered = true
                        confirmStopFromBubble()
                    }
                    longPressRunnable = runnable
                    mainHandler.postDelayed(runnable, 600)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        moved = true
                        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, params)
                    repositionPanelIfVisible()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    if (longPressTriggered) {
                        return@setOnTouchListener true
                    }
                    if (!moved) {
                        if (bubbleMinimized) {
                            restoreBubble()
                        } else {
                            togglePanel()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePauseResume() {
        startService(Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_PAUSE_TOGGLE
        })
        mainHandler.postDelayed({ updatePauseButtonAppearance() }, 150)
    }

    private fun updatePauseButtonAppearance() {
        pauseButton?.setImageResource(if (ScreenRecordService.isPaused) R.drawable.ic_play else R.drawable.ic_pause)
    }

    private fun confirmStopFromBubble() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Arrêter l'enregistrement ?")
            .setPositiveButton("Arrêter") { _, _ ->
                stopService(Intent(this, ScreenRecordService::class.java))
                stopSelf()
            }
            .setNegativeButton("Continuer", null)
            .create()
        dialog.window?.setType(overlayType())
        dialog.show()
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
        windowManager.updateViewLayout(bubbleView, params)
    }

    private fun togglePanel() {
        if (panelVisible) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView == null) buildPanel()
        repositionPanelIfVisible(forceShow = true)
        panelVisible = true
        updatePauseButtonAppearance()
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
        pp.gravity = bp.gravity
        pp.x = bp.x + 150
        pp.y = bp.y
        panelView?.visibility = View.VISIBLE
        windowManager.updateViewLayout(panelView, pp)
    }

    private fun buildPanel() {
        panelView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientRoundedRect(intArrayOf(Color.parseColor("#EE1E1E1E"), Color.parseColor("#EE2A1B45")), 22f)
            setPadding(14, 14, 14, 14)
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeIconButton(R.drawable.ic_pen, R.drawable.bg_round_purple) { setTool(ShapeTool.PEN) })
        row1.addView(makeIconButton(R.drawable.ic_arrow, R.drawable.bg_round_blue) { setTool(ShapeTool.ARROW) })
        row1.addView(makeIconButton(R.drawable.ic_circle_tool, R.drawable.bg_round_green) { setTool(ShapeTool.CIRCLE) })
        row1.addView(makeIconButton(R.drawable.ic_rect_tool, R.drawable.bg_round_orange) { setTool(ShapeTool.RECTANGLE) })
        row1.addView(makeIconButton(R.drawable.ic_text_tool, R.drawable.bg_round_purple) { setTool(ShapeTool.TEXT) })
        panelView?.addView(row1)

        val rowPrivacy = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        rowPrivacy.addView(makeIconButton(R.drawable.ic_privacy, R.drawable.bg_round_purple) { setTool(ShapeTool.PRIVACY_BOX) })
        rowPrivacy.addView(makeIconButton(R.drawable.ic_minimize, R.drawable.bg_round_red) { toggleDrawingLayerAttached() })
        panelView?.addView(rowPrivacy)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        listOf(Color.RED, Color.parseColor("#2196F3"), Color.parseColor("#4CAF50"), Color.parseColor("#FFC107"))
            .forEach { color -> row2.addView(makeColorSwatch(color) { drawingView?.currentColor = color }) }
        panelView?.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        row3.addView(makeIconButton(R.drawable.ic_undo, R.drawable.bg_round_blue) { drawingView?.undo() })
        row3.addView(makeIconButton(R.drawable.ic_trash, R.drawable.bg_round_red) { drawingView?.clearAll() })
        row3.addView(makeIconButton(R.drawable.ic_minimize, R.drawable.bg_round_purple) { minimizeBubble() })
        pauseButton = makeIconButton(R.drawable.ic_pause, R.drawable.bg_round_orange) { togglePauseResume() }
        row3.addView(pauseButton)
        panelView?.addView(row3)

        val stopButton = Button(this).apply {
            text = "⏹  Arrêter"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = gradientRoundedRect(intArrayOf(Color.parseColor("#E53935"), Color.parseColor("#B71C1C")), 18f)
            setPadding(16, 14, 16, 14)
        }
        stopButton.setOnClickListener {
            stopService(Intent(this@OverlayDrawingService, ScreenRecordService::class.java))
            stopSelf()
        }
        val stopParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        stopParams.topMargin = 10
        panelView?.addView(stopButton, stopParams)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or secureFlag(),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        panelParams = params

        windowManager.addView(panelView, params)
        panelView?.visibility = View.GONE
    }

    // Bouton d'urgence : détache complètement le calque de dessin de l'écran.
    // Reste disponible en secours si jamais le retour automatique (ci-dessous)
    // ne suffisait pas dans certains cas.
    private fun toggleDrawingLayerAttached() {
        val view = drawingView ?: return
        if (drawingLayerAttached) {
            try { windowManager.removeView(view) } catch (e: Exception) {}
            drawingLayerAttached = false
        } else {
            val params = buildDrawingLayerParams(touchable = drawingEnabled)
            try { windowManager.addView(view, params) } catch (e: Exception) {}
            drawingLayerAttached = true
        }
    }

    private fun makeIconButton(iconRes: Int, bgRes: Int, action: () -> Unit): ImageButton {
        val button = ImageButton(this)
        button.setImageResource(iconRes)
        button.setBackgroundResource(bgRes)
        val size = 68
        button.layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(4, 0, 4, 0) }
        button.setPadding(14, 14, 14, 14)
        button.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        button.setOnClickListener {
            action()
            if (button != pauseButton) hidePanel()
        }
        return button
    }

    private fun makeColorSwatch(color: Int, action: () -> Unit): View {
        return View(this).apply {
            background = gradientOval(intArrayOf(color, color))
            val size = 52
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(4, 0, 4, 0) }
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

    // Retire puis remet le calque avec les nouveaux réglages tactiles, au lieu
    // de simplement modifier ses paramètres en direct : sur certains Android
    // modifiés (comme XOS d'Itel), un simple updateViewLayout() ne suffit pas
    // toujours à faire repasser les touchers vers les autres applis — retirer
    // puis remettre la fenêtre force le système à bien appliquer le changement.
    private fun updateDrawingTouchability() {
        if (!drawingLayerAttached) return
        val view = drawingView ?: return
        val newParams = buildDrawingLayerParams(touchable = drawingEnabled)
        try {
            windowManager.removeView(view)
            windowManager.addView(view, newParams)
        } catch (e: Exception) {
            try { windowManager.updateViewLayout(view, newParams) } catch (e2: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(autoHideRunnable)
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        bubbleView?.let { windowManager.removeView(it) }
        panelView?.let { windowManager.removeView(it) }
        if (drawingLayerAttached) drawingView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        watermarkView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
