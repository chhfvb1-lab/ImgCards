package com.example.imgcards

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class AddActivity : Activity() {
    private var front: String? = null
    private var back: String? = null
    private lateinit var fv: ImageView
    private lateinit var bv: ImageView
    private lateinit var note: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val p = U.dp(this@AddActivity, 16); setPadding(p, p, p, p)
        }
        val h = U.dp(this, 160)
        fv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        bv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        root.addView(btn("انتخاب تصویر روی کارت (اجباری)") { pick(1) })
        root.addView(fv, LinearLayout.LayoutParams(-1, h))
        root.addView(btn("انتخاب تصویر پشت کارت (اختیاری)") { pick(2) })
        root.addView(bv, LinearLayout.LayoutParams(-1, h))
        note = EditText(this).apply { hint = "متن پاسخ / یادداشت (اختیاری)"; gravity = Gravity.START }
        root.addView(note)
        root.addView(btn("ذخیره") { save() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pick(code: Int) = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
    }, code)

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        val name = U.saveImage(this, uri) ?: return
        val bmp: Bitmap? = U.load(this, name, 600)
        if (req == 1) { U.deleteImage(this, front); front = name; fv.setImageBitmap(bmp) }
        else { U.deleteImage(this, back); back = name; bv.setImageBitmap(bmp) }
    }

    private fun save() {
        val f = front ?: run { Toast.makeText(this, "تصویر روی کارت را انتخاب کن", Toast.LENGTH_SHORT).show(); return }
        Db(this).add(f, back, note.text.toString().trim().ifEmpty { null })
        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
        front = null; back = null; fv.setImageBitmap(null); bv.setImageBitmap(null); note.setText("")
    }
}
