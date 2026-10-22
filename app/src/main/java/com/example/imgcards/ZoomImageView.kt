package com.example.imgcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** ImageView با زوم (دو انگشت)، جابجایی، دابل‌تپ و فلیپ عمودی */
class ZoomImageView(ctx: Context) : ImageView(ctx) {
    private val m = Matrix()
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private val maxScale = 8f

    var onTap: (() -> Unit)? = null
    /** +1 = کشیدن به چپ (بعدی)، -1 = کشیدن به راست (قبلی) */
    var onSwipe: ((Int) -> Unit)? = null

    var flipV = false
        set(v) { field = v; applyMatrix() }

    init { scaleType = ScaleType.MATRIX }

    private val scaleDet = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomTo(scale * d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    })

    private val gestDet = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 == null || scale > 1.05f || scaleDet.isInProgress) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            val minDist = 80 * resources.displayMetrics.density
            if (abs(dx) > minDist && abs(dx) > abs(dy) * 1.5f && abs(vx) > 300f) {
                onSwipe?.invoke(if (dx < 0) 1 else -1)
                return true
            }
            return false
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap?.invoke(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scale > 1f && !scaleDet.isInProgress) { tx -= dx; ty -= dy; applyMatrix() }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > 1.05f) { scale = 1f; tx = 0f; ty = 0f; applyMatrix() }
            else zoomTo(3f, e.x, e.y)
            return true
        }
    })

    private fun zoomTo(target: Float, fx: Float, fy: Float) {
        val ns = min(maxScale, max(1f, target))
        val r = ns / scale
        val cx = width / 2f
        val cy = height / 2f
        tx = (fx - cx) - r * (fx - cx - tx)
        ty = (fy - cy) - r * (fy - cy - ty)
        scale = ns
        applyMatrix()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        scale = 1f; tx = 0f; ty = 0f; flipV = false
        applyMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh); applyMatrix()
    }

    private fun applyMatrix() {
        val d = drawable ?: return
        val vw = width.toFloat(); val vh = height.toFloat()
        if (vw == 0f || vh == 0f) return
        val dw = d.intrinsicWidth.toFloat(); val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return
        val fit = min(vw / dw, vh / dh)
        val maxX = max(0f, (dw * fit * scale - vw) / 2f)
        val maxY = max(0f, (dh * fit * scale - vh) / 2f)
        tx = min(maxX, max(-maxX, tx))
        ty = min(maxY, max(-maxY, ty))
        m.reset()
        m.postScale(fit, fit)
        m.postTranslate((vw - dw * fit) / 2f, (vh - dh * fit) / 2f)
        if (flipV) m.postScale(1f, -1f, vw / 2f, vh / 2f)
        m.postScale(scale, scale, vw / 2f, vh / 2f)
        m.postTranslate(tx, ty)
        imageMatrix = m
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDet.onTouchEvent(e)
        gestDet.onTouchEvent(e)
        return true
    }
}
