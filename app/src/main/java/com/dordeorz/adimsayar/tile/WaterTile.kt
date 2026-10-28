package com.dordeorz.adimsayar.tile

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Reminders
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.Water
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/** Quick settings tile: a tap adds one glass of water; the tile shows today's water. */
@RequiresApi(Build.VERSION_CODES.N)
class WaterTile : TileService() {

    private var job: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh(add = false)
    }

    override fun onStopListening() {
        job?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        refresh(add = true)
    }

    private fun refresh(add: Boolean) {
        job?.cancel()
        job = AppScope.launch(Dispatchers.IO) {
            if (add) Reminders.addWater(applicationContext)
            val drunk = Water.today(applicationContext)
            withContext(Dispatchers.Main) { show(drunk) }
        }
    }

    private fun show(drunk: Int) {
        val tile = qsTile ?: return
        val settings = SettingsStore.get(applicationContext).settings.value
        val format = NumberFormat.getIntegerInstance(Locales.current)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_add_water)
        tile.state = if (drunk >= settings.waterGoalMl) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val amount = getString(R.string.water_value, format.format(drunk.toLong()), format.format(settings.waterGoalMl.toLong()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.water_add_glass, settings.glassMl)
            tile.subtitle = amount
        } else {
            tile.label = amount
        }
        tile.contentDescription = getString(R.string.water_add_glass, settings.glassMl) + ", " + amount
        tile.updateTile()
    }
}
