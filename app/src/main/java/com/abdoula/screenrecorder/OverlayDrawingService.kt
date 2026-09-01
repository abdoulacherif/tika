val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeIconButton(R.drawable.ic_pen, R.drawable.bg_round_purple) { setTool(ShapeTool.PEN) })
        row1.addView(makeIconButton(R.drawable.ic_arrow, R.drawable.bg_round_blue) { setTool(ShapeTool.ARROW) })
        row1.addView(makeIconButton(R.drawable.ic_circle_tool, R.drawable.bg_round_green) { setTool(ShapeTool.CIRCLE) })
        row1.addView(makeIconButton(R.drawable.ic_rect_tool, R.drawable.bg_round_orange) { setTool(ShapeTool.RECTANGLE) })
        row1.addView(makeIconButton(R.drawable.ic_text_tool, R.drawable.bg_round_purple) { setTool(ShapeTool.TEXT) })
        panelView?.addView(row1)