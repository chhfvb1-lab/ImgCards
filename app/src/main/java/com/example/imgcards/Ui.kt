package com.example.imgcards

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.AbsListView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** اندازه‌ی تصویر کوچک بر اساس تعداد ستون‌ها */
fun thumbPx(cols: Int) = when (cols) { 1 -> 1000; 2 -> 700; 3 -> 480; else -> 320 }

fun makeImageCell(ctx: Context): FrameLayout = FrameLayout(ctx).apply {
    addView(ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.rgb(224, 224, 224))
    }, FrameLayout.LayoutParams(-1, -1))
    addView(TextView(ctx).apply {
        text = "★"; textSize = 18f
        setTextColor(Color.rgb(255, 200, 0))
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(8, 2, 0, 0) })
    addView(TextView(ctx).apply {
        text = "✓"; textSize = 34f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(130, 0, 110, 255))
    }, FrameLayout.LayoutParams(-1, -1))
}

fun bindImageCell(ctx: Context, cell: FrameLayout, c: Card, size: Int, px: Int, showStar: Boolean, selected: Boolean) {
    cell.layoutParams = AbsListView.LayoutParams(size, size)
    val iv = cell.getChildAt(0) as ImageView
    iv.scaleY = if (c.flip == 1) -1f else 1f
    (cell.getChildAt(1) as TextView).text = if (c.star == 2) "★★" else "★"
    cell.getChildAt(1).visibility = if (showStar && c.star >= 1) View.VISIBLE else View.GONE
    cell.getChildAt(2).visibility = if (selected) View.VISIBLE else View.GONE
    Thumbs.load(ctx, c.front, px, iv)
}

fun makeFolderCell(ctx: Context, size: Int, name: String, count: Int): TextView = TextView(ctx).apply {
    text = "📁\n$name\n($count)"
    gravity = Gravity.CENTER; textSize = 15f
    setTextColor(Color.BLACK)
    setBackgroundColor(Color.rgb(255, 236, 179))
    layoutParams = AbsListView.LayoutParams(size, size)
}

/** نوار بالای صفحه هنگام انتخاب چند تصویر */
class SelectionBar(ctx: Context, moveLabel: String, onMove: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val countText = TextView(ctx).apply { textSize = 15f; setTextColor(Color.WHITE) }
    val view = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.rgb(30, 90, 200))
        visibility = View.GONE
        val p = U.dp(ctx, 6); setPadding(p, p, p, p)
        addView(countText, LinearLayout.LayoutParams(0, -2, 1f))
        addView(Button(ctx).apply {
            text = moveLabel; textSize = 13f; minWidth = 0; minimumWidth = 0
            setOnClickListener { onMove() }
        })
        addView(Button(ctx).apply {
            text = "🗑 حذف"; textSize = 13f; minWidth = 0; minimumWidth = 0
            setOnClickListener { onDelete() }
        })
        addView(Button(ctx).apply {
            text = "✖"; textSize = 13f; minWidth = 0; minimumWidth = 0
            setOnClickListener { onCancel() }
        })
    }
}

/** حذف کامل تصاویر (از برنامه و حافظه) با تأیید */
fun deleteDialog(act: Activity, db: Db, cards: List<Card>, onDone: () -> Unit) {
    if (cards.isEmpty()) return
    AlertDialog.Builder(act)
        .setTitle("حذف")
        .setMessage("${cards.size} تصویر برای همیشه حذف شود؟ این کار قابل بازگشت نیست.")
        .setPositiveButton("حذف") { _, _ ->
            db.deleteCards(cards.map { it.id })
            for (c in cards) { U.deleteImage(act, c.front); U.deleteImage(act, c.back) }
            onDone()
        }
        .setNegativeButton("لغو", null)
        .show()
}
