package com.example.imgcards

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var stats: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val p = U.dp(this@MainActivity, 24); setPadding(p, p, p, p)
        }
        root.addView(TextView(this).apply { text = "مرور تصویری"; textSize = 28f; gravity = Gravity.CENTER })
        stats = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 32, 0, 32) }
        root.addView(stats)
        fun add(t: String, f: () -> Unit) {
            root.addView(btn(t, f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        }
        add("شروع مرور") {
            if (db.dueCards().isEmpty()) Toast.makeText(this, "فعلاً کارتی برای مرور نیست", Toast.LENGTH_SHORT).show()
            else startActivity(Intent(this, ReviewActivity::class.java))
        }
        add("مرور پوشه ستاره‌دارها ★") {
            if (db.starred().isEmpty()) Toast.makeText(this, "هنوز کارت ستاره‌داری نداری", Toast.LENGTH_SHORT).show()
            else startActivity(Intent(this, ReviewActivity::class.java).putExtra("starred", true))
        }
        add("افزودن چند تصویر یکجا") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }, 1)
        }
        add("افزودن کارت (روی + پشت)") { startActivity(Intent(this, AddActivity::class.java)) }
        add("مدیریت کارت‌ها") { startActivity(Intent(this, ManageActivity::class.java)) }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val all = db.all()
        stats.text = "برای مرور امروز: ${db.dueCards().size}\nستاره‌دارها: ${all.count { it.star == 1 }}\nکل کارت‌ها: ${all.size}"
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
                onResume()
            }
        }.start()
    }
}
