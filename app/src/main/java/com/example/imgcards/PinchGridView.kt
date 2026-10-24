package com.example.imgcards

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.GridView

/** گالری ۳ستونه که با دو انگشت زوم می‌شود: بازکردن انگشت‌ها = تصاویر بزرگ‌تر، جمع‌کردن = کوچک‌تر */
class PinchGridView(ctx: Context) : GridView(ctx) {
    /** +1 = زوم‌این (ستون کمتر)، -1 = زوم‌اوت (ستون بیشتر) */
    var onStep: ((Int) -> Unit)? = null

    private var acc = 1f
    private var pinching = false

    private val det = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean { acc = 1f; return true }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            acc *= d.scaleFactor
            if (acc > 1.3f) { onStep?.invoke(1); acc = 1f }
            else if (acc < 0.77f) { onStep?.invoke(-1); acc = 1f }
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        det.onTouchEvent(ev)
        if (det.isInProgress && !pinching) {
            // اسکرول یا کلیکِ نیمه‌کاره را لغو کن
            pinching = true
            val cancel = MotionEvent.obtain(ev)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        if (pinching) {
            val a = ev.actionMasked
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) pinching = false
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}
