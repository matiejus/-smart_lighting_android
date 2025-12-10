package com.example.myapplication.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.myapplication.models.Reading
import java.text.SimpleDateFormat
import java.util.*

class EnergyChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var readings: List<Reading> = emptyList()
    
    private val linePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val circlePaint = Paint().apply {
        color = Color.BLUE
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        isAntiAlias = true
    }
    
    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
    }
    
    private val padding = 80f
    private val circleRadius = 8f

    fun setData(newReadings: List<Reading>) {
        readings = newReadings
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (readings.isEmpty()) {
            canvas.drawText("No data available", 100f, 100f, textPaint)
            return
        }

        val width = width.toFloat() - padding * 2
        val height = height.toFloat() - padding * 2

        // Find min and max power values
        val maxPower = readings.maxOf { it.powerW }
        val minPower = 0f

        // Draw grid
        drawGrid(canvas, width, height)

        // Draw axes
        canvas.drawLine(padding, height + padding, width + padding, height + padding, linePaint)
        canvas.drawLine(padding, padding, padding, height + padding, linePaint)

        // Draw data points and lines
        var prevX = 0f
        var prevY = 0f

        readings.forEachIndexed { index, reading ->
            val x = padding + (index.toFloat() / (readings.size - 1).coerceAtLeast(1)) * width
            val normalizedPower = if (maxPower > 0) reading.powerW / maxPower else 0f
            val y = height + padding - normalizedPower * height

            // Draw circle at data point
            canvas.drawCircle(x, y, circleRadius, circlePaint)

            // Draw line between points
            if (index > 0) {
                canvas.drawLine(prevX, prevY, x, y, linePaint)
            }

            // Draw time label
            if (index % 2 == 0) { // Show every other label to avoid crowding
                val date = Date(reading.timestamp * 1000)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeLabel = sdf.format(date)
                canvas.drawText(timeLabel, x - 25f, height + padding + 40f, textPaint.apply { textSize = 20f })
            }

            prevX = x
            prevY = y
        }

        // Draw Y-axis labels
        drawYAxisLabels(canvas, maxPower, height)
    }

    private fun drawGrid(canvas: Canvas, width: Float, height: Float) {
        for (i in 0..10) {
            val y = padding + (i.toFloat() / 10) * height
            canvas.drawLine(padding, y, width + padding, y, gridPaint)
        }
    }

    private fun drawYAxisLabels(canvas: Canvas, maxPower: Float, height: Float) {
        textPaint.textSize = 20f
        for (i in 0..5) {
            val power = (maxPower / 5) * (5 - i)
            val y = padding + (i.toFloat() / 5) * height
            canvas.drawText(String.format("%.0f W", power), 10f, y + 10f, textPaint)
        }
    }
}
