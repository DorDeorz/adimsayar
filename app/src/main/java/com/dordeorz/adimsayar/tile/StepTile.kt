package com.dordeorz.adimsayar.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.ui.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/** Quick settings tile: today's steps and the goal percent; a tap opens the Today tab. */
@RequiresApi(Build.VERSION_CODES.N)
class StepTile : TileService() {

    private var job: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = AppScope.launch(Dispatchers.IO) {
            val steps = StepRepository.get(applicationContext).today()
            val goal = SettingsStore.get(applicationContext).settings.value.goalOn(java.time.LocalDate.now())
            withContext(Dispatchers.Main) { show(steps, goal) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        super.onStopListening()
    }

    private fun show(steps: Long, goal: Long) {
        val tile = qsTile ?: return
        val percent = if (goal > 0L) (steps * 100 / goal).toInt() else 0
        val count = NumberFormat.getIntegerInstance(Locales.current).format(steps)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_walk)
        tile.state = if (steps >= goal) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.steps_value, count)
            tile.subtitle = getString(R.string.tile_goal_percent, percent)
        } else {
            tile.label = getString(R.string.tile_label_short, count, percent)
        }
        tile.contentDescription = getString(R.string.tile_description, count, percent)
        tile.updateTile()
    }

    // The Intent overload is only used below Android 14, where it still works.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, Tab.Today.name.lowercase())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
