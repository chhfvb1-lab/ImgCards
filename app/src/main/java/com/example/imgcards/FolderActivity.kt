package com.example.imgcards

import android.app.Activity
import android.app.AlertDialog
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

private class Cell(val folder: Folder?, val card: Card?)

/** پوشه‌ی ستاره‌دارها (folder=0) یا یکی از پوشه‌های داخل آن */
class FolderActivity : Activity() {
    private lateinit var db: Db
    private var folderId = 0L
    private lateinit var grid: GridView
    private lateinit var adapter: BaseAdapter
    private lateinit var bar: SelectionBar
    private var items: List<Cell> = emptyList()
    private var counts: Map<Long, Int> = emptyMap()
    private val selected = LinkedHashSet<Long>()
    private var columns = 3
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        folderId = intent.getLongExtra("folder", 0L)
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
        val heading = if (folderId == 0L) "★ پوشه ستاره‌دارها"
        else "📁 " + (db.folders().firstOrNull { it.id == folderId }?.name ?: "پوشه")
        header.addView(TextView(this).apply { text = heading; textSize = 20f; gravity = Gravity.CENTER })
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(btn("▶ مرور این پوشه") { startReview() }.apply { textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
        if (folderId == 0L) {
            r.addView(btn("➕ پوشه جدید") {
                promptName(this, "پوشه جدید") { n -> db.addFolder(n); refresh() }
            }.apply { textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        header.addView(r)
        root.addView(header)

        bar = SelectionBar(this, { moveSelected() }, { deleteSelected() }, { clearSelection() })
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
            override fun getItemViewType(i: Int) = if (items[i].folder != null) 0 else 1
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val size = (resources.displayMetrics.widthPixels - (columns - 1) * sp) / columns
                val f = items[i].folder
                val c = items[i].card
                if (f != null) {
                    return makeFolderCell(this@FolderActivity, size, f.name, counts[f.id] ?: 0)
                }
                val cell = (v as? FrameLayout) ?: makeImageCell(this@FolderActivity)
                bindImageCell(this@FolderActivity, cell, c!!, size, thumbPx(columns), false, selected.contains(c.id))
                return cell
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, i, _ -> onCellClick(items[i]) }
        grid.setOnItemLongClickListener { _, _, i, _ -> onCellLongClick(items[i]); true }

        val empty = TextView(this).apply {
            text = "این پوشه خالی است\nاز صفحه‌ی اول تصویری را نگه دار و «انتقال» را بزن"
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
        val f = cell.folder
        val c = cell.card
        if (f != null) {
            if (selected.isEmpty()) {
                startActivity(Intent(this, FolderActivity::class.java).putExtra("folder", f.id))
            }
        } else if (c != null) {
            if (selected.isNotEmpty()) toggleSel(c.id)
            else {
                val ids = items.mapNotNull { x -> x.card?.id }.toLongArray()
                startActivity(Intent(this, ViewerActivity::class.java).putExtra("id", c.id).putExtra("ids", ids))
            }
        }
    }

    private fun onCellLongClick(cell: Cell) {
        val f = cell.folder
        val c = cell.card
        if (c != null) {
            toggleSel(c.id)
        } else if (f != null && selected.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("پوشه «${f.name}» حذف شود؟ تصاویر داخلش حذف نمی‌شوند و به پوشه ستاره‌دارها می‌روند.")
                .setPositiveButton("حذف پوشه") { _, _ -> db.deleteFolder(f.id); refresh() }
                .setNegativeButton("لغو", null)
                .show()
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

    private fun moveSelected() {
        moveDialog(this, db, selected.toList()) { selected.clear(); refresh() }
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
        val all = db.starred()
        val n = if (folderId == 0L) all.size else all.count { it.folder == folderId }
        if (n == 0) {
            Toast.makeText(this, "تصویری برای مرور نیست", Toast.LENGTH_SHORT).show(); return
        }
        startActivity(
            Intent(this, ReviewActivity::class.java)
                .putExtra("starred", true)
                .putExtra("folder", if (folderId == 0L) -1L else folderId)
        )
    }

    private fun refresh() {
        val starred = db.starred()
        val list = ArrayList<Cell>()
        if (folderId == 0L) {
            for (f in db.folders()) list.add(Cell(f, null))
            for (c in starred) if (c.folder == 0L) list.add(Cell(null, c))
        } else {
            for (c in starred) if (c.folder == folderId) list.add(Cell(null, c))
        }
        counts = starred.groupingBy { it.folder }.eachCount()
        items = list
        updateSel()
    }
}
