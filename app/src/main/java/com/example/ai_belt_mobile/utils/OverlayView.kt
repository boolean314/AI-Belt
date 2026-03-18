package com.example.ai_belt_mobile.utils

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scannerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boxRect = RectF()

    private var scannerLineTop: Float = 0f
    private var animator: ValueAnimator? = null

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 计算扫描框的尺寸和位置
        val boxWidth = width * 0.8f
        val boxHeight = boxWidth // 保持为正方形
        val left = (width - boxWidth) / 2
        val top = (height - boxHeight) / 2
        val right = left + boxWidth
        val bottom = top + boxHeight
        boxRect.set(left, top, right, bottom)

        // 绘制扫描框外部的半透明背景
        boxPaint.color = Color.parseColor("#88000000")
        boxPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), top, boxPaint)
        canvas.drawRect(0f, top, left, bottom, boxPaint)
        canvas.drawRect(right, top, width.toFloat(), bottom, boxPaint)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), boxPaint)

        // 绘制扫描框的白色边框
        boxPaint.color = Color.WHITE
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 5f
        canvas.drawRect(boxRect, boxPaint)

        // 绘制移动的扫描线
        if (scannerLineTop > 0) {
            // 创建一个从透明到绿色再到透明的线性渐变
            val gradient = LinearGradient(
                boxRect.left, scannerLineTop,
                boxRect.right, scannerLineTop,
                intArrayOf(Color.TRANSPARENT, Color.GREEN, Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            scannerPaint.shader = gradient
            canvas.drawLine(boxRect.left, scannerLineTop, boxRect.right, scannerLineTop, scannerPaint)
        }
    }

    private fun startScannerAnimation() {
        if (boxRect.isEmpty) {
            // 视图尚未测量，稍后重试
            post { startScannerAnimation() }
            return
        }

        animator = ValueAnimator.ofFloat(boxRect.top, boxRect.bottom).apply {
            interpolator = LinearInterpolator()
            duration = 2000 // 动画持续时间，单位毫秒
            repeatCount = ValueAnimator.INFINITE // 无限循环
            repeatMode = ValueAnimator.REVERSE // 设置重复模式为“反向”
            addUpdateListener { animation ->
                scannerLineTop = animation.animatedValue as Float
                invalidate() // 重绘视图
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scannerPaint.strokeWidth = 4f
        startScannerAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}