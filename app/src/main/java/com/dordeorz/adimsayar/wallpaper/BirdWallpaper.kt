package com.dordeorz.adimsayar.wallpaper

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.dordeorz.adimsayar.data.Avatar
import com.dordeorz.adimsayar.data.BirdState
import com.dordeorz.adimsayar.data.ExtrasStore
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.StreakFreezes
import com.dordeorz.adimsayar.ui.BirdPainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** A live wallpaper: the bird flies from its nest to the branch as the day's steps reach the goal. */
class BirdWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = BirdEngine()

    private inner class BirdEngine : Engine() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var loop: Job? = null
        private var bird: BirdState? = null
        private var progress = 0f
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        override fun onVisibilityChanged(visible: Boolean) {
            loop?.cancel()
            if (!visible) return
            loop = scope.launch {
                while (isActive) {
                    load()
                    draw()
                    delay(REFRESH_MS)
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            scope.launch { draw() }
        }

        override fun onDestroy() {
            scope.cancel()
            super.onDestroy()
        }

        private suspend fun load() {
            val context = applicationContext
            val today = LocalDate.now()
            val settings = SettingsStore.get(context).settings.value
            val history = StepRepository.get(context).loadHistory()
            val freezes = StreakFreezes.load(context)
            val goal = settings.goalOn(today)
            val achievements = settings.achievements(history, today, freezes = freezes)
            bird = Avatar.state(achievements, history, freezes, today, LocalTime.now().hour, goal)
                .styled(ExtrasStore.get(context).extras.value)
            progress = ((history[today] ?: 0L).toFloat() / goal).coerceIn(0f, 1f)
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                paint(canvas)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun paint(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val night = LocalTime.now().hour !in DAY_HOURS
            fill.shader = LinearGradient(0f, 0f, 0f, h, if (night) NIGHT_TOP else DAY_TOP, if (night) NIGHT_BOTTOM else DAY_BOTTOM, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, fill)
            fill.shader = null
            fill.color = HILL
            canvas.drawOval(RectF(-w * 0.3f, h * 0.82f, w * 1.3f, h * 1.3f), fill)

            val nestX = w * 0.2f
            val nestY = h * 0.72f
            val branchX = w * 0.8f
            val branchY = h * 0.38f
            // The tree trunk on the right and its branch.
            line.color = BARK
            line.strokeWidth = w * 0.06f
            canvas.drawLine(w * 0.95f, h * 0.9f, w * 0.95f, h * 0.2f, line)
            line.strokeWidth = w * 0.025f
            canvas.drawLine(w * 0.95f, branchY + w * 0.02f, branchX - w * 0.1f, branchY + w * 0.03f, line)
            fill.color = LEAF
            canvas.drawCircle(w * 0.93f, h * 0.2f, w * 0.14f, fill)
            canvas.drawCircle(branchX - w * 0.08f, branchY, w * 0.05f, fill)
            // The nest on its stump on the left.
            line.color = BARK
            line.strokeWidth = w * 0.05f
            canvas.drawLine(nestX, h * 0.9f, nestX, nestY + w * 0.04f, line)
            fill.color = NEST
            canvas.drawArc(RectF(nestX - w * 0.1f, nestY - w * 0.03f, nestX + w * 0.1f, nestY + w * 0.08f), 0f, 180f, true, fill)

            val current = bird ?: return
            val size = w * 0.24f
            val t = progress
            val x = nestX + (branchX - nestX) * t
            val arc = (1f - (2f * t - 1f) * (2f * t - 1f)) * h * 0.12f
            val y = nestY + (branchY - nestY) * t - arc
            canvas.save()
            canvas.translate(x - size / 2f, y - size * 0.95f)
            BirdPainter.draw(canvas, size, current)
            canvas.restore()

            fill.color = TRACK
            canvas.drawRoundRect(RectF(w * 0.2f, h * 0.94f, w * 0.8f, h * 0.94f + w * 0.012f), w, w, fill)
            fill.color = PROGRESS
            canvas.drawRoundRect(RectF(w * 0.2f, h * 0.94f, w * 0.2f + w * 0.6f * t, h * 0.94f + w * 0.012f), w, w, fill)
        }
    }

    private companion object {
        const val REFRESH_MS = 60_000L
        val DAY_HOURS = 7..19
        const val DAY_TOP = 0xFF81D4FA.toInt()
        const val DAY_BOTTOM = 0xFFE1F5FE.toInt()
        const val NIGHT_TOP = 0xFF0D1B2A.toInt()
        const val NIGHT_BOTTOM = 0xFF1B263B.toInt()
        const val HILL = 0xFF66BB6A.toInt()
        const val BARK = 0xFF6D4C41.toInt()
        const val LEAF = 0xFF388E3C.toInt()
        const val NEST = 0xFF8D6E63.toInt()
        const val TRACK = 0x55FFFFFF
        const val PROGRESS = 0xFFFFC93C.toInt()
    }
}
