package com.example.imgcards

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var stats: TextView
    private lateinit var grid: GridView
    private lateinit var adapter: BaseAdapter
    private lateinit var bar: SelectionBar
    private var cards: List<Card> = emptyList()
    private val selected = LinkedHashSet<Long>()
    private var columns = 3
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        columns = prefs.getInt("gridCols", 3).coerceIn(1, 6)
        val pad = U.dp(this, 10)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // بالا: عنوان، آمار و دکمه‌ها
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        header.addView(TextView(this).apply { text = "مرور تصویری"; textSize = 20f; gravity = Gravity.CENTER })
        stats = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setPadding(0, pad / 2, 0, pad / 2) }
        header.addView(stats)
        fun row(vararg items: Pair<String, () -> Unit>) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((t, f) in items) r.addView(btn(t, f).apply { textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(r)
        }
        row("شروع مرور" to { startReview() }, "پوشه ستاره‌دارها ★" to { openStarredFolder() })
        row("افزودن تصویر" to { pickImages() }, "مدیریت کارت‌ها" to { startActivity(Intent(this, ManageActivity::class.java)) })
        root.addView(header)

        bar = SelectionBar(this, "★ ستاره", { moveSelected() }, { deleteSelected() }, { clearSelection() })
        root.addView(bar.view)

        // پایین: گالری
        val sp = U.dp(this, 2)
        grid = PinchGridView(this).apply {
            numColumns = columns
            onStep = { s -> changeColumns(s) }
            horizontalSpacing = sp
            verticalSpacing = sp
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        adapter = object : BaseAdapter() {
            override fun getCount() = cards.size
            override fun getItem(i: Int) = cards[i]
            override fun getItemId(i: Int) = cards[i].id
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val c = cards[i]
                val size = (resources.displayMetrics.widthPixels - (columns - 1) * sp) / columns
                val cell = (v as? FrameLayout) ?: makeImageCell(this@MainActivity)
                bindImageCell(this@MainActivity, cell, c, size, thumbPx(columns), true, selected.contains(c.id))
                return cell
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, i, _ ->
            val c = cards[i]
            if (selected.isNotEmpty()) toggleSel(c.id)
            else startActivity(Intent(this, ViewerActivity::class.java).putExtra("id", c.id))
        }
        grid.setOnItemLongClickListener { _, _, i, _ -> toggleSel(cards[i].id); true }

        val empty = TextView(this).apply {
            text = "هنوز تصویری اضافه نکرده‌ای\nاز «افزودن تصویر» شروع کن"
            gravity = Gravity.CENTER; textSize = 16f
        }
        val frame = FrameLayout(this)
        frame.addView(grid, FrameLayout.LayoutParams(-1, -1))
        frame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        grid.emptyView = empty
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onBackPressed() {
        if (selected.isNotEmpty()) clearSelection() else super.onBackPressed()
    }

    // ---------- انتخاب چندتایی ----------
    private fun toggleSel(id: Long) {
        if (!selected.add(id)) selected.remove(id)
        updateSel()
    }

    private fun clearSelection() { selected.clear(); updateSel() }

    private fun updateSel() {
        bar.view.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        bar.countText.text = "${selected.size} انتخاب شده"
        adapter.notifyDataSetChanged()
    }

    private fun moveSelected() {
        val n = selected.size
        db.setStars(selected.toList(), 1)
        Toast.makeText(this, "$n تصویر به پوشه ستاره‌دارها رفت ★", Toast.LENGTH_SHORT).show()
        selected.clear()
        refresh()
    }

    private fun deleteSelected() {
        deleteDialog(this, db, cards.filter { selected.contains(it.id) }) { selected.clear(); refresh() }
    }

    /** زوم دو انگشتی روی گالری: step=+1 بزرگ‌تر (ستون کمتر)، -1 کوچک‌تر */
    private fun changeColumns(step: Int) {
        val n = (columns - step).coerceIn(1, 6)
        if (n == columns) return
        val first = grid.firstVisiblePosition
        columns = n
        grid.numColumns = n
        prefs.edit().putInt("gridCols", n).apply()
        adapter.notifyDataSetChanged()
        grid.post { grid.setSelection(first) }
    }

    private fun refresh() {
        cards = db.all()
        stats.text = "سررسید: ${db.dueCards().size} • ★: ${cards.count { it.star == 1 }} • ★★: ${cards.count { it.star == 2 }} • کل: ${cards.size}"
        bar.view.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        bar.countText.text = "${selected.size} انتخاب شده"
        adapter.notifyDataSetChanged()
    }

    private fun startReview() {
        if (db.dueCards().isEmpty()) Toast.makeText(this, "فعلاً کارتی برای مرور نیست", Toast.LENGTH_SHORT).show()
        else startActivity(Intent(this, ReviewActivity::class.java))
    }

    private fun openStarredFolder() {
        startActivity(Intent(this, FolderActivity::class.java).putExtra("level", 1))
    }

    private fun pickImages() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, 1)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 1 || res != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        data.clipData?.let { cd -> for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri) }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        Toast.makeText(this, "در حال افزودن ${uris.size} تصویر...", Toast.LENGTH_SHORT).show()
        Thread {
            var n = 0
            for (u in uris) {
                val name = U.saveImage(this, u)
                if (name != null) { db.add(name, null, null); n++ }
            }
            runOnUiThread {
                Toast.makeText(this, "$n کارت اضافه شد", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }.start()
    }
}
