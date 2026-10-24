package com.example.imgcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import android.widget.Button
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

object U {
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun saveImage(ctx: Context, uri: Uri): String? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
        if (bmp == null) null else {
            val rot = ctx.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rot != 0f) {
                val m = Matrix().apply { postRotate(rot) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val f = File(ctx.filesDir, "img_${System.nanoTime()}.jpg")
            FileOutputStream(f).use { bmp!!.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            f.name
        }
    } catch (e: Exception) { null }

    fun load(ctx: Context, name: String, maxPx: Int): Bitmap? {
        val path = File(ctx.filesDir, name).absolutePath
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / s > maxPx || o.outHeight / s > maxPx) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    fun deleteAllImages(ctx: Context) {
        ctx.filesDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("img_") && f.name.endsWith(".jpg")) f.delete()
        }
    }

    fun deleteImage(ctx: Context, name: String?) { if (name != null) File(ctx.filesDir, name).delete() }
}

fun Context.btn(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text; textSize = 16f; setOnClickListener { onClick() }
}

/** بارگذاری پس‌زمینه‌ی تصاویر کوچک با کش، تا گالری روی گوشی ضعیف هم روان باشد */
object Thumbs {
    private val cache = object : android.util.LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    fun load(ctx: Context, name: String, size: Int, iv: ImageView) {
        iv.tag = name
        val hit = cache.get(name)
        if (hit != null) { iv.setImageBitmap(hit); return }
        iv.setImageBitmap(null)
        val app = ctx.applicationContext
        pool.execute {
            val bmp = U.load(app, name, size)
            if (bmp != null) cache.put(name, bmp)
            ui.post { if (iv.tag == name) iv.setImageBitmap(bmp) }
        }
    }
}
