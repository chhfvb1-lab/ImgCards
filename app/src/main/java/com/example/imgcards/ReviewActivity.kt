package com.example.imgcards

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ReviewActivity : Activity() {
    private lateinit var db: Db
    private val queue = ArrayDeque<Card>()
    private var cur: Card? = null
    private var starredMode = false
    private var locked = false
    private var overlayShown = true

    private lateinit var column: LinearLayout
    private lateinit var imgHolder: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var info: TextView
    private lateinit var img: ZoomImageView
    private lateinit var note: TextView
    private lateinit var showBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var starBtn: Button
    private lateinit var lockBtn: Button
    private lateinit var rateRow: LinearLayout
    private val rateBtns = ArrayList<Button>()
    private val names = listOf("دوباره", "سخت", "خوب", "آسان")

    private fun small(text: String, f: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 13f; minWidth = 0; minimumWidth = 0
        val p = U.dp(this@ReviewActivity, 8); setPadding(p, 0, p, 0)
        setOnClickListener { f() }
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        starredMode = intent.getBooleanExtra("starred", false)
        if (starredMode) queue.addAll(db.starred().shuffled()) else queue.addAll(db.dueCards())

        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        img = ZoomImageView(this).apply { onTap = { if (isLandscape()) toggleOverlay() } }
        imgHolder = FrameLayout(this)
        imgHolder.addView(img, FrameLayout.LayoutParams(-1, -1))
        column.addView(imgHolder, LinearLayout.LayoutParams(-1, 0, 1f))

        // نوار بالا
        topBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        info = TextView(this).apply { textSize = 15f }
        val flipBtn = small("↕ وارونه") { img.flipV = !img.flipV }
        lockBtn = small("🔒 قفل") { toggleLock() }
        starBtn = small("☆ ستاره") { toggleStar() }
        topBar.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        topBar.addView(flipBtn)
        topBar.addView(lockBtn)
        topBar.addView(starBtn)

        // پنل پایین
        bottomPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        note = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER; setPadding(8, 8, 8, 8) }
        showBtn = btn("نمایش پاسخ") { showAnswer() }
        nextBtn = btn("بعدی") { next() }.apply { visibility = View.GONE }
        rateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        for (q in 0..3) {
            val bt = Button(this).apply { textSize = 13f; setOnClickListener { rate(q) } }
            rateBtns.add(bt)
            rateRow.addView(bt, LinearLayout.LayoutParams(0, -2, 1f))
        }
        bottomPanel.addView(note)
        bottomPanel.addView(showBtn)
        bottomPanel.addView(nextBtn)
        bottomPanel.addView(rateRow)

        setContentView(column)
        applyLayout()
        next()
    }

    // ---------- چرخش و تمام‌صفحه ----------
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        applyLayout()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isLandscape()) setImmersive(true)
    }

    private fun detach(v: View) { (v.parent as? ViewGroup)?.removeView(v) }

    private fun applyLayout() {
        val land = isLandscape()
        detach(topBar); detach(bottomPanel)
        if (land) {
            imgHolder.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            imgHolder.addView(bottomPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            val shade = Color.argb(150, 0, 0, 0)
            topBar.setBackgroundColor(shade); bottomPanel.setBackgroundColor(shade)
            info.setTextColor(Color.WHITE); note.setTextColor(Color.WHITE)
            column.setBackgroundColor(Color.BLACK)
            column.setPadding(0, 0, 0, 0)
            val vis = if (overlayShown) View.VISIBLE else View.GONE
            topBar.visibility = vis; bottomPanel.visibility = vis
            setImmersive(true)
        } else {
            column.addView(topBar, 0)
            column.addView(bottomPanel)
            topBar.setBackgroundColor(Color.TRANSPARENT); bottomPanel.setBackgroundColor(Color.TRANSPARENT)
            info.setTextColor(Color.BLACK); note.setTextColor(Color.BLACK)
            column.setBackgroundColor(Color.WHITE)
            val p = U.dp(this, 12); column.setPadding(p, p, p, p)
            topBar.visibility = View.VISIBLE; bottomPanel.visibility = View.VISIBLE
            setImmersive(false)
        }
    }

    private fun toggleOverlay() {
        overlayShown = !overlayShown
        val vis = if (overlayShown) View.VISIBLE else View.GONE
        topBar.visibility = vis; bottomPanel.visibility = vis
    }

    @Suppress("DEPRECATION")
    private fun setImmersive(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = if (on) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.attributes = lp
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(!on)
            window.insetsController?.let { c ->
                if (on) {
                    c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(WindowInsets.Type.systemBars())
                } else c.show(WindowInsets.Type.systemBars())
            }
        } else {
            window.decorView.systemUiVisibility = if (on) (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) else View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun toggleLock() {
        locked = !locked
        requestedOrientation = if (locked) ActivityInfo.SCREEN_ORIENTATION_LOCKED
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        lockBtn.text = if (locked) "🔓 آزاد" else "🔒 قفل"
        Toast.makeText(this, if (locked) "چرخش قفل شد" else "چرخش آزاد شد", Toast.LENGTH_SHORT).show()
    }

    // ---------- ستاره و مرور ----------
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
