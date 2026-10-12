package com.dordeorz.adimsayar.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.dordeorz.adimsayar.data.BirdItem
import com.dordeorz.adimsayar.data.BirdMood
import com.dordeorz.adimsayar.data.BirdStage
import com.dordeorz.adimsayar.data.BirdState

private const val UNIT = 100f
private const val BODY = 0xFFFFC93C.toInt()
private const val BELLY = 0xFFFFE8A3.toInt()
private const val WING = 0xFFF4A922.toInt()
private const val BEAK = 0xFFFF8A3D.toInt()
private const val EYE = 0xFF3B2A20.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLUSH = 0x66FF6F6F
private const val SHELL = 0xFFFFF4E0.toInt()
private const val SHELL_SPOT = 0xFFF2D7A8.toInt()
private const val SHELL_LINE = 0xFFC9A877.toInt()
private const val SHADOW = 0x22000000
private const val GOLD = 0xFFFFB300.toInt()
private const val SCARF = 0xFFE53935.toInt()
private const val RIBBON = 0xFF1E88E5.toInt()
private const val CAP = 0xFF8E24AA.toInt()
private const val TEAR = 0xFF64B5F6.toInt()
private const val SPARKLE = 0xFFFFD54F.toInt()
private const val ZZZ = 0xFF90A4AE.toInt()

object BirdPainter {
    fun draw(canvas: Canvas, size: Float, bird: BirdState) = BirdDrawer().draw(canvas, size, bird)
}

private class BirdDrawer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }

    fun draw(canvas: Canvas, size: Float, bird: BirdState) {
        canvas.save()
        canvas.scale(size / UNIT, size / UNIT)
        fill.color = SHADOW
        canvas.drawOval(RectF(26f, 90f, 74f, 98f), fill)
        if (BirdItem.Sun in bird.items) sun(canvas)
        if (bird.stage == BirdStage.Egg) {
            egg(canvas, bird)
        } else {
            canvas.save()
            canvas.translate(50f, 93f)
            val scale = scaleOf(bird.stage)
            canvas.scale(scale, scale)
            body(canvas, bird)
            canvas.restore()
        }
        moodDecor(canvas, bird)
        canvas.restore()
    }

    private fun scaleOf(stage: BirdStage): Float = when (stage) {
        BirdStage.Egg, BirdStage.Hatchling -> 0.62f
        BirdStage.Chick -> 0.74f
        BirdStage.Fledgling -> 0.84f
        BirdStage.Bird -> 0.94f
        BirdStage.Elder -> 1f
    }

    private fun egg(canvas: Canvas, bird: BirdState) {
        fill.color = SHELL
        canvas.drawOval(RectF(28f, 30f, 72f, 94f), fill)
        fill.color = SHELL_SPOT
        canvas.drawCircle(40f, 52f, 4f, fill)
        canvas.drawCircle(58f, 44f, 3f, fill)
        canvas.drawCircle(60f, 72f, 5f, fill)
        canvas.drawCircle(42f, 80f, 3f, fill)
        if (bird.stageFraction >= 0.5f) {
            stroke.color = SHELL_LINE
            stroke.strokeWidth = 1.6f
            val crack = Path().apply {
                moveTo(36f, 58f)
                lineTo(42f, 54f)
                lineTo(46f, 60f)
                lineTo(52f, 53f)
                lineTo(56f, 59f)
                lineTo(62f, 55f)
            }
            canvas.drawPath(crack, stroke)
        }
    }

    private fun body(canvas: Canvas, bird: BirdState) {
        val stage = bird.stage
        val excited = bird.mood == BirdMood.Excited
        if (stage >= BirdStage.Fledgling) tail(canvas, stage)
        stroke.color = BEAK
        stroke.strokeWidth = 3f
        if (stage != BirdStage.Hatchling) {
            canvas.drawLine(-8f, -12f, -8f, 0f, stroke)
            canvas.drawLine(8f, -12f, 8f, 0f, stroke)
            canvas.drawLine(-12f, 0f, -4f, 0f, stroke)
            canvas.drawLine(4f, 0f, 12f, 0f, stroke)
        }
        fill.color = BODY
        canvas.drawCircle(0f, -38f, 28f, fill)
        fill.color = BELLY
        canvas.drawOval(RectF(-16f, -46f, 18f, -14f), fill)
        wing(canvas, -24f, if (excited) -60f else 15f)
        wing(canvas, 24f, if (excited) 60f else -15f)
        if (stage >= BirdStage.Bird) crest(canvas, stage == BirdStage.Elder)
        eyes(canvas, bird.mood)
        beak(canvas, bird.mood)
        if (bird.mood == BirdMood.Happy || excited) {
            fill.color = BLUSH
            canvas.drawCircle(-17f, -38f, 4.5f, fill)
            canvas.drawCircle(17f, -38f, 4.5f, fill)
        }
        if (bird.mood == BirdMood.Sad) {
            fill.color = TEAR
            canvas.drawCircle(-12f, -36f, 2.4f, fill)
        }
        if (stage == BirdStage.Hatchling) shell(canvas)
        if (stage != BirdStage.Hatchling) items(canvas, bird.items)
    }

    private fun tail(canvas: Canvas, stage: BirdStage) {
        val length = if (stage >= BirdStage.Bird) 24f else 16f
        fill.color = WING
        val path = Path().apply {
            moveTo(20f, -30f)
            lineTo(20f + length, -46f)
            lineTo(22f + length * 0.8f, -26f)
            close()
        }
        canvas.drawPath(path, fill)
    }

    private fun wing(canvas: Canvas, x: Float, angle: Float) {
        canvas.save()
        canvas.rotate(angle, x, -38f)
        fill.color = WING
        canvas.drawOval(RectF(x - 7f, -52f, x + 7f, -26f), fill)
        canvas.restore()
    }

    private fun crest(canvas: Canvas, elder: Boolean) {
        fill.color = if (elder) GOLD else WING
        val lift = if (elder) 6f else 0f
        canvas.drawOval(RectF(-4f, -76f - lift, 4f, -62f), fill)
        canvas.drawOval(RectF(-11f, -71f - lift / 2, -3f, -61f), fill)
        canvas.drawOval(RectF(3f, -71f - lift / 2, 11f, -61f), fill)
    }

    private fun eyes(canvas: Canvas, mood: BirdMood) {
        stroke.color = EYE
        stroke.strokeWidth = 2.4f
        fill.color = EYE
        for (x in floatArrayOf(-9f, 9f)) {
            when (mood) {
                BirdMood.Sleepy -> canvas.drawArc(RectF(x - 4f, -50f, x + 4f, -44f), 0f, 180f, false, stroke)
                BirdMood.Excited -> canvas.drawArc(RectF(x - 4f, -50f, x + 4f, -42f), 180f, 180f, false, stroke)
                else -> {
                    canvas.drawCircle(x, -46f, if (mood == BirdMood.Happy) 3.6f else 3.2f, fill)
                    if (mood == BirdMood.Happy) {
                        fill.color = WHITE
                        canvas.drawCircle(x + 1.2f, -47.2f, 1.2f, fill)
                        fill.color = EYE
                    }
                }
            }
        }
        if (mood == BirdMood.Sad) {
            canvas.drawLine(-13f, -51f, -6f, -54f, stroke)
            canvas.drawLine(13f, -51f, 6f, -54f, stroke)
        }
    }

    private fun beak(canvas: Canvas, mood: BirdMood) {
        fill.color = BEAK
        val open = mood == BirdMood.Happy || mood == BirdMood.Excited
        val top = Path().apply {
            moveTo(-5f, -41f)
            lineTo(5f, -41f)
            lineTo(0f, if (open) -36f else -33f)
            close()
        }
        canvas.drawPath(top, fill)
        if (open) {
            val bottom = Path().apply {
                moveTo(-4f, -35f)
                lineTo(4f, -35f)
                lineTo(0f, -30f)
                close()
            }
            canvas.drawPath(bottom, fill)
        }
    }

    private fun shell(canvas: Canvas) {
        fill.color = SHELL
        val path = Path().apply {
            moveTo(-32f, -26f)
            lineTo(-22f, -32f)
            lineTo(-12f, -24f)
            lineTo(-2f, -32f)
            lineTo(8f, -24f)
            lineTo(18f, -32f)
            lineTo(28f, -24f)
            lineTo(32f, -28f)
            lineTo(32f, -8f)
            quadTo(0f, 8f, -32f, -8f)
            close()
        }
        canvas.drawPath(path, fill)
        fill.color = SHELL_SPOT
        canvas.drawCircle(-14f, -12f, 3.5f, fill)
        canvas.drawCircle(14f, -16f, 3f, fill)
    }

    private fun items(canvas: Canvas, items: Set<BirdItem>) {
        if (BirdItem.Scarf in items) {
            fill.color = SCARF
            canvas.drawRoundRect(RectF(-21f, -31f, 21f, -25f), 3f, 3f, fill)
            val end = Path().apply {
                moveTo(8f, -28f)
                lineTo(16f, -28f)
                lineTo(20f, -14f)
                lineTo(12f, -16f)
                close()
            }
            canvas.drawPath(end, fill)
        }
        if (BirdItem.Ribbon in items) {
            fill.color = RIBBON
            val ribbon = Path().apply {
                moveTo(-5f, -26f)
                lineTo(5f, -26f)
                lineTo(2f, -18f)
                lineTo(-2f, -18f)
                close()
            }
            canvas.drawPath(ribbon, fill)
            fill.color = GOLD
            canvas.drawCircle(0f, -15f, 4.5f, fill)
        }
        if (BirdItem.Crown in items) {
            fill.color = GOLD
            val crown = Path().apply {
                moveTo(-12f, -62f)
                lineTo(-12f, -76f)
                lineTo(-6f, -69f)
                lineTo(0f, -79f)
                lineTo(6f, -69f)
                lineTo(12f, -76f)
                lineTo(12f, -62f)
                close()
            }
            canvas.drawPath(crown, fill)
        } else if (BirdItem.Cap in items) {
            fill.color = CAP
            val cap = Path().apply {
                moveTo(-11f, -62f)
                lineTo(11f, -62f)
                lineTo(3f, -84f)
                close()
            }
            canvas.drawPath(cap, fill)
            fill.color = GOLD
            canvas.drawCircle(3f, -85f, 3f, fill)
        }
    }

    private fun sun(canvas: Canvas) {
        fill.color = GOLD
        canvas.drawCircle(16f, 16f, 6.5f, fill)
        stroke.color = GOLD
        stroke.strokeWidth = 2f
        for (i in 0 until 8) {
            canvas.save()
            canvas.rotate(i * 45f, 16f, 16f)
            canvas.drawLine(16f, 5f, 16f, 2f, stroke)
            canvas.restore()
        }
    }

    private fun moodDecor(canvas: Canvas, bird: BirdState) {
        when (bird.mood) {
            BirdMood.Sleepy -> {
                text.color = ZZZ
                text.textSize = 10f
                canvas.drawText("z", 72f, 34f, text)
                text.textSize = 14f
                canvas.drawText("z", 80f, 22f, text)
            }
            BirdMood.Excited -> {
                sparkle(canvas, 18f, 44f, 5f)
                sparkle(canvas, 84f, 36f, 6f)
                sparkle(canvas, 78f, 14f, 4f)
            }
            else -> Unit
        }
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float, r: Float) {
        fill.color = SPARKLE
        val path = Path().apply {
            moveTo(x, y - r)
            lineTo(x + r * 0.3f, y - r * 0.3f)
            lineTo(x + r, y)
            lineTo(x + r * 0.3f, y + r * 0.3f)
            lineTo(x, y + r)
            lineTo(x - r * 0.3f, y + r * 0.3f)
            lineTo(x - r, y)
            lineTo(x - r * 0.3f, y - r * 0.3f)
            close()
        }
        canvas.drawPath(path, fill)
    }
}
