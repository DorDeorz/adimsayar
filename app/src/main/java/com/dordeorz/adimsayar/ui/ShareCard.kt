package com.dordeorz.adimsayar.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

class ShareStat(val value: String, val label: String)

class ShareBar(val label: String, val fraction: Float, val reached: Boolean)

class ShareContent(
    val title: String,
    val date: String,
    val steps: String,
    val unit: String,
    val percent: String,
    val fraction: Float,
    val goal: String,
    val stats: List<ShareStat>,
    val week: List<ShareBar>,
    val goalLine: Float,
)

class ShareColors(val background: Int, val surface: Int, val primary: Int, val muted: Int, val text: Int)

object ShareCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val SIDE = 80f
    private const val RING_RADIUS = 250f
    private const val RING_STROKE = 44f
    private const val RING_CENTER_Y = 560f
    private const val CHART_TOP = 1110f
    private const val CHART_BOTTOM = 1250f
    private const val FILE_NAME = "adimsayar.png"

    fun share(context: Context, content: ShareContent, colors: ShareColors, chooserTitle: String): Boolean {
        val file = try {
            val dir = File(context.cacheDir, "shares").apply { mkdirs() }
            File(dir, FILE_NAME).also { out ->
                val bitmap = draw(content, colors)
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } catch (e: IOException) {
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun draw(content: ShareContent, colors: ShareColors): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors.background)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fun text(size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, typeface: Typeface = Typeface.DEFAULT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                textAlign = align
                this.typeface = typeface
            }
        val center = WIDTH / 2f

        canvas.drawText(content.title, SIDE, 140f, text(52f, colors.primary, typeface = bold))
        canvas.drawText(content.date, SIDE, 200f, text(38f, colors.muted))

        val ring = RectF(center - RING_RADIUS, RING_CENTER_Y - RING_RADIUS, center + RING_RADIUS, RING_CENTER_Y + RING_RADIUS)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(ring, 0f, 360f, false, stroke.apply { color = colors.surface })
        val sweep = 360f * content.fraction.coerceIn(0f, 1f)
        if (sweep > 0f) canvas.drawArc(ring, -90f, sweep, false, stroke.apply { color = colors.primary })
        canvas.drawText(content.percent, center, RING_CENTER_Y - 90f, text(48f, colors.primary, Paint.Align.CENTER, bold))
        canvas.drawText(content.steps, center, RING_CENTER_Y + 40f, text(124f, colors.text, Paint.Align.CENTER, bold))
        canvas.drawText(content.unit, center, RING_CENTER_Y + 110f, text(44f, colors.muted, Paint.Align.CENTER))
        canvas.drawText(content.goal, center, RING_CENTER_Y + RING_RADIUS + 100f, text(40f, colors.muted, Paint.Align.CENTER))

        val column = (WIDTH - SIDE * 2) / content.stats.size.coerceAtLeast(1)
        content.stats.forEachIndexed { index, stat ->
            val x = SIDE + column * index + column / 2
            canvas.drawText(stat.value, x, 1000f, text(50f, colors.text, Paint.Align.CENTER, bold))
            canvas.drawText(stat.label, x, 1048f, text(32f, colors.muted, Paint.Align.CENTER))
        }

        val slot = (WIDTH - SIDE * 2) / content.week.size.coerceAtLeast(1)
        val height = CHART_BOTTOM - CHART_TOP
        val goalY = CHART_BOTTOM - height * content.goalLine.coerceIn(0f, 1f)
        canvas.drawLine(SIDE, goalY, WIDTH - SIDE, goalY, Paint().apply {
            color = colors.surface
            strokeWidth = 4f
        })
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        content.week.forEachIndexed { index, day ->
            val x = SIDE + slot * index + slot / 2
            val top = CHART_BOTTOM - (height * day.fraction.coerceIn(0f, 1f)).coerceAtLeast(8f)
            bar.color = if (day.reached) colors.primary else colors.primary and 0x00FFFFFF or (0x66 shl 24)
            canvas.drawRoundRect(RectF(x - 30f, top, x + 30f, CHART_BOTTOM), 14f, 14f, bar)
            canvas.drawText(day.label, x, CHART_BOTTOM + 56f, text(32f, colors.muted, Paint.Align.CENTER))
        }
        return bitmap
    }
}
