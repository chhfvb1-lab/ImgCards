package com.example.imgcards

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ReviewActivity : Activity() {
    private lateinit var db: Db
    private val queue = ArrayDeque<Card>()
    private var cur: Card? = null
    private lateinit var info: TextView
    private lateinit var img: ImageView
    private lateinit var note: TextView
    private lateinit var showBtn: Button
    private lateinit var rateRow: LinearLayout
    private val rateBtns = ArrayList<Button>()
    private val names = listOf("دوباره", "سخت", "خوب", "آسان")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        queue.addAll(db.dueCards())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val p = U.dp(this@ReviewActivity, 12); setPadding(p, p, p, p)
        }
        info = TextView(this).apply { gravity = Gravity.CENTER; textSize = 16f }
        img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        note = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER; setPadding(8, 8, 8, 8) }
        showBtn = btn("نمایش پاسخ") { showAnswer() }
        rateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        for (q in 0..3) {
            val bt = Button(this).apply { textSize = 13f; setOnClickListener { rate(q) } }
            rateBtns.add(bt)
            rateRow.addView(bt, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(info)
        root.addView(img, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(note)
        root.addView(showBtn)
        root.addView(rateRow)
        setContentView(root)
        next()
    }

    private fun next() {
        val c = queue.removeFirstOrNull()
        if (c == null) {
            Toast.makeText(this, "مرور تمام شد 🎉", Toast.LENGTH_LONG).show(); finish(); return
        }
        cur = c
        info.text = "باقی‌مانده: ${queue.size + 1}"
        img.setImageBitmap(U.load(this, c.front, 1400))
        note.text = ""
        showBtn.visibility = View.VISIBLE
        rateRow.visibility = View.GONE
    }

    private fun showAnswer() {
        val c = cur ?: return
        c.back?.let { img.setImageBitmap(U.load(this, it, 1400)) }
        note.text = c.note ?: ""
        for (q in 0..3) rateBtns[q].text = "${names[q]}\n${Sched.label(c, q)}"
        showBtn.visibility = View.GONE
        rateRow.visibility = View.VISIBLE
    }

    private fun rate(q: Int) {
        val c = cur ?: return
        val n = Sched.apply(c, q)
        db.update(n)
        if (q == 0) queue.addLast(n)
        next()
    }
}
