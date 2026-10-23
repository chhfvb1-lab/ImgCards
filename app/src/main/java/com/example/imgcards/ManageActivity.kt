package com.example.imgcards

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class ManageActivity : Activity() {
    private lateinit var db: Db
    private var cards: MutableList<Card> = mutableListOf()
    private lateinit var adapter: BaseAdapter
    private lateinit var tabAll: Button
    private lateinit var tabStar: Button
    private var onlyStar = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        onlyStar = intent.getBooleanExtra("starred", false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabAll = Button(this).apply { setOnClickListener { onlyStar = false; reload() } }
        tabStar = Button(this).apply { setOnClickListener { onlyStar = true; reload() } }
        tabs.addView(tabAll, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(tabStar, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tabs)

        val lv = ListView(this)
        adapter = object : BaseAdapter() {
            override fun getCount() = cards.size
            override fun getItem(i: Int) = cards[i]
            override fun getItemId(i: Int) = cards[i].id
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val c = cards[i]
                val row = LinearLayout(this@ManageActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 12, 16, 12)
                }
                val s = U.dp(this@ManageActivity, 72)
                row.addView(ImageView(this@ManageActivity).apply {
                    setImageBitmap(U.load(this@ManageActivity, c.front, 150))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }, LinearLayout.LayoutParams(s, s))
                row.addView(TextView(this@ManageActivity).apply {
                    text = (if (c.star == 1) "★ " else "") + "فاصله: ${c.ivl} روز"
                    setPadding(24, 0, 0, 0)
                })
                return row
            }
        }
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, i, _ ->
            val c = cards[i]
            val starLabel = if (c.star == 1) "برداشتن ستاره" else "ستاره‌دار کردن"
            AlertDialog.Builder(this).setItems(arrayOf(starLabel, "حذف کارت")) { _, which ->
                if (which == 0) {
                    db.setStar(c.id, if (c.star == 1) 0 else 1); reload()
                } else {
                    AlertDialog.Builder(this).setMessage("این کارت حذف شود؟")
                        .setPositiveButton("حذف") { _, _ ->
                            db.delete(c.id); U.deleteImage(this, c.front); U.deleteImage(this, c.back); reload()
                        }.setNegativeButton("لغو", null).show()
                }
            }.show()
        }
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        reload()
    }

    private fun reload() {
        val all = db.all()
        val st = all.count { it.star == 1 }
        tabAll.text = (if (!onlyStar) "● " else "") + "همه (${all.size})"
        tabStar.text = (if (onlyStar) "● " else "") + "★ ستاره‌دارها ($st)"
        cards = (if (onlyStar) all.filter { it.star == 1 } else all).toMutableList()
        adapter.notifyDataSetChanged()
    }
}
