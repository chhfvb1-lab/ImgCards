package com.example.imgcards

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class ManageActivity : Activity() {
    private lateinit var db: Db
    private var cards: MutableList<Card> = mutableListOf()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        cards = db.all().toMutableList()
        val lv = ListView(this).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
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
                    text = "فاصله: ${c.ivl} روز" + if (c.back != null || c.note != null) "  • دارای پشت" else ""
                    setPadding(24, 0, 0, 0)
                })
                return row
            }
        }
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, i, _ ->
            AlertDialog.Builder(this).setMessage("این کارت حذف شود؟")
                .setPositiveButton("حذف") { _, _ ->
                    val c = cards.removeAt(i)
                    db.delete(c.id); U.deleteImage(this, c.front); U.deleteImage(this, c.back)
                    adapter.notifyDataSetChanged()
                }.setNegativeButton("لغو", null).show()
        }
        setContentView(lv)
    }
}
