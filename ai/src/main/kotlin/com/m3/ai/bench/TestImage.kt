package com.m3.ai.bench

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/** A white image with a red circle (left) and a blue square (right), for the image prompt. */
fun shapesTestImagePng(sizePx: Int = 512): ByteArray {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val unit = sizePx / 8f

    paint.color = Color.rgb(220, 20, 30)
    canvas.drawCircle(unit * 2.2f, unit * 4f, unit * 1.5f, paint)

    paint.color = Color.rgb(30, 60, 220)
    canvas.drawRect(unit * 4.6f, unit * 2.6f, unit * 7.4f, unit * 5.4f, paint)

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return out.toByteArray()
}
