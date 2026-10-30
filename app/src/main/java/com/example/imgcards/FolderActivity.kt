package com.example.imgcards

import android.app.Activity
import android.content.Intent
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

private class Cell(val isTile: Boolean, val card: Card?)

/**
 * level = 1 : پوشه ستاره‌دارها (بالایش یک کاشی «پوشه دو ستاره» دارد)
 * level = 2 : پوشه دو ستاره (داخل ستاره‌دارها)
 */
class FolderActivity : Activity() {
    private lateinit var db: Db
    private var level = 1
    private lateinit var grid: GridView
    private lateinit var adapter: BaseAdapter
    private lateinit var bar: SelectionBar
    private var items: List<Cell> = emptyList()
    private var tileCount = 0
    private val selected = LinkedHashSet<Long>()
    private var columns = 3
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        level = intent.getIntExtra("level", 1)
        columns = prefs.getInt("gridCols", 3).coerceIn(1, 6)
        val pad = U.dp(this, 10)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        header.addView(TextView(this).apply {
            text = if (level == 1) "★ پوشه ستاره‌دارها" else "★★ پوشه دو ستاره"
            textSize = 20f; gravity = Gravity.CENTER
        })
        header.addView(btn("▶ مرور این پوشه") { startReview() }.apply { textSize = 14f })
        root.addView(header)

        val moveLabel = if (level == 1) "★★ دو ستاره" else "★ بازگشت"
        bar = SelectionBar(this, moveLabel, { moveSelected() }, { deleteSelected() }, { clearSelection() })
        root.addView(bar.view)

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
            override fun getCount() = items.size
            override fun getItem(i: Int): Any = items[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getViewTypeCount() = 2
            override fun getItemViewType(i: Int) = if (items[i].isTile) 0 else 1
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val size = (resources.displayMetrics.widthPixels - (columns - 1) * sp) / columns
                val c = items[i].card
                if (c == null) {
                    return makeFolderCell(this@FolderActivity, size, "★★ پوشه دو ستاره", tileCount)
                }
                val cell = (v as? FrameLayout) ?: makeImageCell(this@FolderActivity)
                bindImageCell(this@FolderActivity, cell, c, size, thumbPx(columns), false, selected.contains(c.id))
                return cell
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, i, _ -> onCellClick(items[i]) }
        grid.setOnItemLongClickListener { _, _, i, _ ->
            val c = items[i].card
            if (c != null) toggleSel(c.id)
            true
        }

        val empty = TextView(this).apply {
            text = if (level == 1) "این پوشه خالی است" else "پوشه دو ستاره خالی است\nدر پوشه ستاره‌دارها تصویر را باز کن و «★★» را بزن"
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

    private fun onCellClick(cell: Cell) {
        val c = cell.card
        if (c == null) {
            if (selected.isEmpty()) {
                startActivity(Intent(this, FolderActivity::class.java).putExtra("level", 2))
            }
        } else if (selected.isNotEmpty()) {
            toggleSel(c.id)
        } else {
            val ids = items.mapNotNull { x -> x.card?.id }.toLongArray()
            startActivity(Intent(this, ViewerActivity::class.java).putExtra("id", c.id).putExtra("ids", ids).putExtra("level", level))
        }
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

    /** پوشه ستاره‌دارها: انتخاب‌شده‌ها به پوشه دو ستاره می‌روند؛ پوشه دو ستاره: برمی‌گردند به ستاره‌دارها */
    private fun moveSelected() {
        val target = if (level == 1) 2 else 1
        val n = selected.size
        db.setStars(selected.toList(), target)
        Toast.makeText(
            this,
            if (target == 2) "$n تصویر به پوشه دو ستاره رفت ★★" else "$n تصویر به پوشه ستاره‌دارها برگشت ★",
            Toast.LENGTH_SHORT
        ).show()
        selected.clear()
        refresh()
    }

    private fun deleteSelected() {
        val list = items.mapNotNull { x -> x.card }.filter { selected.contains(it.id) }
        deleteDialog(this, db, list) { selected.clear(); refresh() }
    }

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

    private fun startReview() {
        if (db.byStar(level).isEmpty()) {
            Toast.makeText(this, "تصویری برای مرور نیست", Toast.LENGTH_SHORT).show(); return
        }
        startActivity(Intent(this, ReviewActivity::class.java).putExtra("level", level))
    }

    private fun refresh() {
        val all = db.all()
        val list = ArrayList<Cell>()
        if (level == 1) {
            tileCount = all.count { it.star == 2 }
            list.add(Cell(true, null))
        }
        for (c in all) if (c.star == level) list.add(Cell(false, c))
        items = list
        updateSel()
    }
}
