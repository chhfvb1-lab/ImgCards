package com.example.imgcards

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** نمایش تمام‌صفحه‌ی یک تصویر از گالری: زوم، کشیدن برای قبلی/بعدی، وارونه و ستاره */
class ViewerActivity : Activity() {
    private lateinit var db: Db
    private val cards = ArrayList<Card>()
    private var pos = 0
    private var animating = false
    private var overlayShown = true

    private lateinit var img: ZoomImageView
    private lateinit var topBar: LinearLayout
    private lateinit var info: TextView
    private lateinit var flipBtn: Button
    private lateinit var starBtn: Button
    private lateinit var star2Btn: Button

    private fun small(text: String, f: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 12f; minWidth = 0; minimumWidth = 0
        val p = U.dp(this@ViewerActivity, 2); setPadding(p, 0, p, 0)
        setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        val ids = intent.getLongArrayExtra("ids")
        if (ids != null) {
            val map = db.all().associateBy { it.id }
            for (id in ids) map[id]?.let { cards.add(it) }
        } else cards.addAll(db.all())
        if (cards.isEmpty()) { finish(); return }
        val startId = intent.getLongExtra("id", -1L)
        pos = cards.indexOfFirst { it.id == startId }.coerceAtLeast(0)

        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        img = ZoomImageView(this).apply {
            onTap = { toggleOverlay() }
            onSwipe = { d -> swipeTo(d) }
            onHold = { deleteCurrent() }
        }
        frame.addView(img, FrameLayout.LayoutParams(-1, -1))

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            val p = U.dp(this@ViewerActivity, 8)
            setPadding(p, U.dp(this@ViewerActivity, 14), p, p)
        }
        info = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        flipBtn = small("↕\nوارونه") { toggleFlip() }
        starBtn = small("☆\nستاره") { toggleStar() }
        star2Btn = small("★★\nدو ستاره") { toggleStar2() }
        val delBtn = small("🗑\nحذف") { deleteCurrent() }
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (bt in listOf(flipBtn, starBtn, star2Btn, delBtn)) {
            btnRow.addView(bt, LinearLayout.LayoutParams(0, -2, 1f))
        }
        topBar.addView(info, LinearLayout.LayoutParams(-1, -2))
        topBar.addView(btnRow, LinearLayout.LayoutParams(-1, -2))
        frame.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        setContentView(frame)
        applyFull()
        showCard()
        Toast.makeText(this, "دابل‌تپ: زوم • کشیدن به چپ/راست: بعدی/قبلی", Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFull()
    }

    @Suppress("DEPRECATION")
    private fun applyFull() {
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { c ->
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsets.Type.systemBars())
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun toggleOverlay() {
        overlayShown = !overlayShown
        topBar.visibility = if (overlayShown) View.VISIBLE else View.GONE
    }

    private fun showCard() {
        val c = cards[pos]
        info.text = "${pos + 1} از ${cards.size}"
        img.setImageBitmap(U.load(this, c.front, 2000))
        img.flipV = c.flip == 1
        updateButtons()
    }

    private fun updateButtons() {
        val c = cards[pos]
        flipBtn.text = if (c.flip == 1) "↕\nوارونه ✓" else "↕\nوارونه"
        starBtn.text = if (c.star >= 1) "★\nستاره" else "☆\nستاره"
        star2Btn.text = if (c.star == 2) "★★\nدو ستاره ✓" else "★★\nدو ستاره"
    }

    private fun toggleFlip() {
        val c = cards[pos]
        val v = if (c.flip == 1) 0 else 1
        db.setFlip(c.id, v)
        cards[pos] = c.copy(flip = v)
        img.flipV = v == 1
        updateButtons()
    }

    /** حذف کامل تصویر فعلی (با دکمه‌ی حذف یا نگه داشتن انگشت روی تصویر) */
    private fun deleteCurrent() {
        val c = cards[pos]
        deleteDialog(this, db, listOf(c)) {
            cards.removeAt(pos)
            if (cards.isEmpty()) {
                finish()
            } else {
                if (pos >= cards.size) pos = cards.size - 1
                showCard()
            }
        }
    }

    private fun setLevel(v: Int, msg: String) {
        val c = cards[pos]
        db.setStar(c.id, v)
        cards[pos] = c.copy(star = v)
        updateButtons()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun toggleStar() {
        if (cards[pos].star == 0) setLevel(1, "به پوشه ستاره‌دارها رفت ★")
        else setLevel(0, "از ستاره‌دارها خارج شد")
    }

    private fun toggleStar2() {
        if (cards[pos].star == 2) setLevel(1, "به پوشه ستاره‌دارها برگشت ★")
        else setLevel(2, "به پوشه دو ستاره رفت ★★")
    }

    /** dir: +1 بعدی، -1 قبلی */
    private fun swipeTo(dir: Int) {
        if (animating) return
        val np = pos + dir
        if (np < 0 || np >= cards.size) {
            Toast.makeText(this, if (dir > 0) "آخرین تصویر" else "اولین تصویر", Toast.LENGTH_SHORT).show()
            return
        }
        animating = true
        val d = img.width * 0.25f
        img.animate().translationX(-dir * d).alpha(0f).setDuration(110).withEndAction {
            pos = np
            showCard()
            img.translationX = dir * d
            img.animate().translationX(0f).alpha(1f).setDuration(110).withEndAction { animating = false }.start()
        }.start()
    }
}
