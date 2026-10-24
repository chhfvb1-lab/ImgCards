package com.example.imgcards

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var stats: TextView
    private lateinit var grid: GridView
    private lateinit var adapter: BaseAdapter
    private var cards: List<Card> = emptyList()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
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
        row("شروع مرور" to { startReview() }, "مرور ستاره‌دارها ★" to { startStarred() })
        row("افزودن تصویر" to { pickImages() }, "مدیریت کارت‌ها" to { startActivity(Intent(this, ManageActivity::class.java)) })
        root.addView(header)

        // پایین: گالری ۳ ستونه
        val sp = U.dp(this, 2)
        grid = GridView(this).apply {
            numColumns = 3
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
                val size = (resources.displayMetrics.widthPixels - 2 * sp) / 3
                val cell = (v as? FrameLayout) ?: FrameLayout(this@MainActivity).apply {
                    addView(ImageView(this@MainActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(Color.rgb(224, 224, 224))
                    }, FrameLayout.LayoutParams(-1, -1))
                    addView(TextView(this@MainActivity).apply {
                        text = "★"; textSize = 18f
                        setTextColor(Color.rgb(255, 200, 0))
                        setShadowLayer(4f, 0f, 0f, Color.BLACK)
                    }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(8, 2, 0, 0) })
                }
                cell.layoutParams = AbsListView.LayoutParams(size, size)
                val iv = cell.getChildAt(0) as ImageView
                iv.scaleY = if (c.flip == 1) -1f else 1f
                cell.getChildAt(1).visibility = if (c.star == 1) View.VISIBLE else View.GONE
                Thumbs.load(this@MainActivity, c.front, 480, iv)
                return cell
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, i, _ ->
            startActivity(Intent(this, ViewerActivity::class.java).putExtra("id", cards[i].id))
        }
        grid.setOnItemLongClickListener { _, _, i, _ -> cardDialog(cards[i]); true }

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

    private fun refresh() {
        cards = db.all()
        stats.text = "سررسید: ${db.dueCards().size} • ستاره‌دار: ${cards.count { it.star == 1 }} • کل: ${cards.size}"
        adapter.notifyDataSetChanged()
    }

    private fun startReview() {
        if (db.dueCards().isEmpty()) Toast.makeText(this, "فعلاً کارتی برای مرور نیست", Toast.LENGTH_SHORT).show()
        else startActivity(Intent(this, ReviewActivity::class.java))
    }

    private fun startStarred() {
        if (db.starred().isEmpty()) Toast.makeText(this, "هنوز کارت ستاره‌داری نداری", Toast.LENGTH_SHORT).show()
        else startActivity(Intent(this, ReviewActivity::class.java).putExtra("starred", true))
    }

    private fun pickImages() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, 1)
    }

    private fun cardDialog(c: Card) {
        val starLabel = if (c.star == 1) "برداشتن ستاره" else "ستاره‌دار کردن"
        AlertDialog.Builder(this).setItems(arrayOf(starLabel, "حذف کارت")) { _, which ->
            if (which == 0) {
                db.setStar(c.id, if (c.star == 1) 0 else 1); refresh()
            } else {
                AlertDialog.Builder(this).setMessage("این کارت حذف شود؟")
                    .setPositiveButton("حذف") { _, _ ->
                        db.delete(c.id); U.deleteImage(this, c.front); U.deleteImage(this, c.back); refresh()
                    }.setNegativeButton("لغو", null).show()
            }
        }.show()
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
