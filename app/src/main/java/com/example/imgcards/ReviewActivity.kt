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
    private val cards = ArrayList<Card>()
    private var pos = 0
    private var cur: Card? = null
    private var level = 0   // 0 = مرور عادی، 1 = پوشه ستاره‌دارها، 2 = پوشه دو ستاره
    private var locked = false
    private var overlayShown = true
    private var animating = false

    private lateinit var column: LinearLayout
    private lateinit var imgHolder: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var info: TextView
    private lateinit var img: ZoomImageView
    private lateinit var starBtn: Button
    private lateinit var star2Btn: Button
    private lateinit var lockBtn: Button
    private lateinit var flipBtn: Button
    private lateinit var rateRow: LinearLayout
    private val rateBtns = ArrayList<Button>()
    private val names = listOf("دوباره", "سخت", "خوب", "آسان")

    private fun small(text: String, f: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 12f; minWidth = 0; minimumWidth = 0
        val p = U.dp(this@ReviewActivity, 2); setPadding(p, 0, p, 0)
        setOnClickListener { f() }
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        level = intent.getIntExtra("level", if (intent.getBooleanExtra("starred", false)) 1 else 0)
        if (level > 0) cards.addAll(db.byStar(level).shuffled()) else cards.addAll(db.dueCards())

        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        img = ZoomImageView(this).apply {
            onTap = { if (isLandscape()) toggleOverlay() }
            onSwipe = { d -> swipeTo(d) }
            onHold = { deleteCurrent() }
        }
        imgHolder = FrameLayout(this)
        imgHolder.addView(img, FrameLayout.LayoutParams(-1, -1))
        column.addView(imgHolder, LinearLayout.LayoutParams(-1, 0, 1f))

        // نوار بالا
        topBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER }
        flipBtn = small("↕\nوارونه") { toggleFlip() }
        updateFlipBtn()
        lockBtn = small("🔒\nقفل") { toggleLock() }
        starBtn = small("☆\nستاره") { toggleStar() }
        star2Btn = small("★★\nدو ستاره") { toggleStar2() }
        val delBtn = small("🗑\nحذف") { deleteCurrent() }
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (bt in listOf(flipBtn, lockBtn, starBtn, star2Btn, delBtn)) {
            btnRow.addView(bt, LinearLayout.LayoutParams(0, -2, 1f))
        }
        topBar.addView(info, LinearLayout.LayoutParams(-1, -2))
        topBar.addView(btnRow, LinearLayout.LayoutParams(-1, -2))

        // پنل پایین
        bottomPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (q in 0..3) {
            val bt = Button(this).apply { textSize = 13f; setOnClickListener { rate(q) } }
            rateBtns.add(bt)
            rateRow.addView(bt, LinearLayout.LayoutParams(0, -2, 1f))
        }
        bottomPanel.addView(rateRow)

        setContentView(column)
        applyLayout()
        if (cards.isEmpty()) { finish(); return }
        showCurrent()
        Toast.makeText(this, "تصویر را به چپ بکش: بعدی • به راست: قبلی", Toast.LENGTH_LONG).show()
    }

    // ---------- چرخش و تمام‌صفحه ----------
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        applyLayout()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isLandscape()) applyImmersive(true)
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
            info.setTextColor(Color.WHITE)
            column.setBackgroundColor(Color.BLACK)
            column.setPadding(0, 0, 0, 0)
            val vis = if (overlayShown) View.VISIBLE else View.GONE
            topBar.visibility = vis; bottomPanel.visibility = vis
            applyImmersive(true)
        } else {
            column.addView(topBar, 0)
            column.addView(bottomPanel)
            topBar.setBackgroundColor(Color.TRANSPARENT); bottomPanel.setBackgroundColor(Color.TRANSPARENT)
            info.setTextColor(Color.BLACK)
            column.setBackgroundColor(Color.WHITE)
            val p = U.dp(this, 12); column.setPadding(p, p, p, p)
            topBar.visibility = View.VISIBLE; bottomPanel.visibility = View.VISIBLE
            applyImmersive(false)
        }
    }

    private fun toggleOverlay() {
        overlayShown = !overlayShown
        val vis = if (overlayShown) View.VISIBLE else View.GONE
        topBar.visibility = vis; bottomPanel.visibility = vis
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive(on: Boolean) {
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

    private fun updateFlipBtn() {
        flipBtn.text = if (cur?.flip == 1) "↕\nوارونه ✓" else "↕\nوارونه"
    }

    /** وارونگی برای هر تصویر جداگانه ذخیره می‌شود و با تعویض تصویر یا بستن برنامه حفظ می‌شود */
    private fun toggleFlip() {
        val c = cur ?: return
        val v = if (c.flip == 1) 0 else 1
        db.setFlip(c.id, v)
        val n = c.copy(flip = v)
        cur = n
        cards[pos] = n
        img.flipV = v == 1
        updateFlipBtn()
    }

    private fun toggleLock() {
        locked = !locked
        requestedOrientation = if (locked) ActivityInfo.SCREEN_ORIENTATION_LOCKED
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        lockBtn.text = if (locked) "🔓\nباز" else "🔒\nقفل"
        Toast.makeText(this, if (locked) "چرخش قفل شد" else "چرخش باز شد", Toast.LENGTH_SHORT).show()
    }

    // ---------- ناوبری بین کارت‌ها ----------
    private fun showCurrent() {
        val c = cards[pos]
        cur = c
        info.text = "کارت ${pos + 1} از ${cards.size}"
        img.setImageBitmap(U.load(this, c.front, 1800))
        img.flipV = c.flip == 1
        updateFlipBtn()
        updateStarBtn()
        for (q in 0..3) rateBtns[q].text = "${names[q]}\n${Sched.label(c, q)}"
    }

    /** dir: +1 بعدی، -1 قبلی. بدون نیاز به نمایش پاسخ؛ کارت امتیاز نمی‌گیرد و همچنان در صف می‌ماند. */
    private fun swipeTo(dir: Int) {
        if (animating) return
        if (cards.size <= 1) {
            Toast.makeText(this, "کارت دیگری نیست", Toast.LENGTH_SHORT).show(); return
        }
        animating = true
        val d = img.width * 0.25f
        img.animate().translationX(-dir * d).alpha(0f).setDuration(110).withEndAction {
            pos = (pos + dir + cards.size) % cards.size
            showCurrent()
            img.translationX = dir * d
            img.animate().translationX(0f).alpha(1f).setDuration(110).withEndAction { animating = false }.start()
        }.start()
    }

    private fun finishDone() {
        Toast.makeText(this, "مرور تمام شد 🎉", Toast.LENGTH_LONG).show(); finish()
    }

    // ---------- ستاره و مرور ----------
    private fun updateStarBtn() {
        starBtn.text = if (level == 0) "☆\nستاره" else "★\nستاره"
        star2Btn.text = if (level == 2) "★★\nدو ستاره ✓" else "★★\nدو ستاره"
    }

    /** حذف کامل تصویر فعلی (با دکمه‌ی حذف یا نگه داشتن انگشت روی تصویر) */
    private fun deleteCurrent() {
        val c = cur ?: return
        deleteDialog(this, db, listOf(c)) {
            cards.removeAt(pos)
            if (cards.isEmpty()) {
                finish()
            } else {
                if (pos >= cards.size) pos = 0
                showCurrent()
            }
        }
    }

    /** تغییر سطح ستاره؛ کارت همان لحظه از صفِ همین مرور بیرون می‌رود */
    private fun changeLevel(v: Int, msg: String) {
        val c = cur ?: return
        db.setStar(c.id, v)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        cards.removeAt(pos)
        if (cards.isEmpty()) { finish(); return }
        if (pos >= cards.size) pos = 0
        showCurrent()
    }

    /** ★ : عادی ← پوشه ستاره‌دارها ؛ در هر پوشه‌ی ستاره‌دار ← خروج از ستاره‌ها */
    private fun toggleStar() {
        if (level == 0) changeLevel(1, "به پوشه ستاره‌دارها رفت ★")
        else changeLevel(0, "از ستاره‌دارها خارج شد")
    }

    /** ★★ : رفتن به پوشه دو ستاره؛ اگر همین‌جا هستی برگشت به پوشه ستاره‌دارها */
    private fun toggleStar2() {
        if (level == 2) changeLevel(1, "به پوشه ستاره‌دارها برگشت ★")
        else changeLevel(2, "به پوشه دو ستاره رفت ★★")
    }

    private fun rate(q: Int) {
        val c = cur ?: return
        val n = Sched.apply(c, q)
        db.update(n)
        cards.removeAt(pos)
        val again = q == 0
        if (again) cards.add(n)
        if (cards.isEmpty()) { finishDone(); return }
        if (pos >= cards.size || (again && pos == cards.size - 1 && cards.size > 1)) pos = 0
        showCurrent()
    }
}
