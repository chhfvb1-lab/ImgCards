package com.example.imgcards

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ReviewActivity : Activity() {
    private lateinit var db: Db
    private val queue = ArrayDeque<Card>()
    private var cur: Card? = null
    private var starredMode = false
    private lateinit var info: TextView
    private lateinit var img: ZoomImageView
    private lateinit var note: TextView
    private lateinit var showBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var starBtn: Button
    private lateinit var rateRow: LinearLayout
    private val rateBtns = ArrayList<Button>()
    private val names = listOf("دوباره", "سخت", "خوب", "آسان")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        starredMode = intent.getBooleanExtra("starred", false)
        if (starredMode) queue.addAll(db.starred().shuffled()) else queue.addAll(db.dueCards())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val p = U.dp(this@ReviewActivity, 12); setPadding(p, p, p, p)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        info = TextView(this).apply { textSize = 16f }
        starBtn = Button(this).apply { textSize = 15f; setOnClickListener { toggleStar() } }
        val flipBtn = Button(this).apply { text = "↕ وارونه"; textSize = 15f; setOnClickListener { img.flipV = !img.flipV } }
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(flipBtn)
        top.addView(starBtn)

        img = ZoomImageView(this)
        note = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER; setPadding(8, 8, 8, 8) }
        showBtn = btn("نمایش پاسخ") { showAnswer() }
        nextBtn = btn("بعدی") { next() }.apply { visibility = View.GONE }
        rateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        for (q in 0..3) {
            val bt = Button(this).apply { textSize = 13f; setOnClickListener { rate(q) } }
            rateBtns.add(bt)
            rateRow.addView(bt, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(top)
        root.addView(img, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(note)
        root.addView(showBtn)
        root.addView(nextBtn)
        root.addView(rateRow)
        setContentView(root)
        next()
    }

    private fun updateStarBtn() {
        starBtn.text = if ((cur?.star ?: 0) == 1) "★ ستاره" else "☆ ستاره"
    }

    private fun toggleStar() {
        val c = cur ?: return
        val v = if (c.star == 1) 0 else 1
        db.setStar(c.id, v)
        cur = c.copy(star = v)
        updateStarBtn()
        Toast.makeText(this, if (v == 1) "به پوشه ستاره‌دارها رفت" else "از پوشه ستاره‌دارها برگشت", Toast.LENGTH_SHORT).show()
    }

    private fun next() {
        val c = queue.removeFirstOrNull()
        if (c == null) {
            Toast.makeText(this, "مرور تمام شد 🎉", Toast.LENGTH_LONG).show(); finish(); return
        }
        cur = c
        info.text = "باقی‌مانده: ${queue.size + 1}"
        img.setImageBitmap(U.load(this, c.front, 1800))
        note.text = ""
        updateStarBtn()
        showBtn.visibility = View.VISIBLE
        nextBtn.visibility = View.GONE
        rateRow.visibility = View.GONE
    }

    private fun showAnswer() {
        val c = cur ?: return
        c.back?.let { img.setImageBitmap(U.load(this, it, 1800)) }
        note.text = c.note ?: ""
        showBtn.visibility = View.GONE
        if (starredMode) {
            nextBtn.visibility = View.VISIBLE
        } else {
            for (q in 0..3) rateBtns[q].text = "${names[q]}\n${Sched.label(c, q)}"
            rateRow.visibility = View.VISIBLE
        }
    }

    private fun rate(q: Int) {
        val c = cur ?: return
        val n = Sched.apply(c, q)
        db.update(n)
        if (q == 0 && c.star == 0) queue.addLast(n)
        next()
    }
}
