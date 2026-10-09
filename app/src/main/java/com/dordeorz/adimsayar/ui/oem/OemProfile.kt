package com.dordeorz.adimsayar.ui.oem

import android.content.ComponentName
import android.os.Build
import androidx.annotation.StringRes
import com.dordeorz.adimsayar.R

enum class OemTarget { Autostart, BatterySettings, AppDetails, Manual }

data class OemStep(@param:StringRes val text: Int, val target: OemTarget)

private val BATTERY_STEP = OemStep(R.string.oem_battery_common, OemTarget.BatterySettings)

enum class OemProfile(
    val manufacturerKeys: List<String>,
    val romMarkers: List<String>,
    val aggressive: Boolean,
    val autostartComponents: List<ComponentName>,
    val steps: List<OemStep>,
) {
    Xiaomi(
        manufacturerKeys = listOf("xiaomi", "redmi", "poco"),
        romMarkers = listOf("miui", "hyperos"),
        aggressive = true,
        autostartComponents = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        steps = listOf(
            OemStep(R.string.oem_xiaomi_1, OemTarget.Autostart),
            OemStep(R.string.oem_xiaomi_2, OemTarget.AppDetails),
        ),
    ),
    Huawei(
        manufacturerKeys = listOf("huawei", "honor"),
        romMarkers = listOf("emui", "magicos", "harmonyos"),
        aggressive = true,
        autostartComponents = listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ),
        steps = listOf(OemStep(R.string.oem_huawei_1, OemTarget.Autostart), BATTERY_STEP),
    ),
    Oppo(
        manufacturerKeys = listOf("oppo", "realme", "oneplus"),
        romMarkers = listOf("coloros", "oxygenos", "realmeui"),
        aggressive = true,
        autostartComponents = listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ),
        steps = listOf(OemStep(R.string.oem_oppo_1, OemTarget.AppDetails), OemStep(R.string.oem_oppo_2, OemTarget.Autostart), BATTERY_STEP),
    ),
    Vivo(
        manufacturerKeys = listOf("vivo", "iqoo"),
        romMarkers = listOf("funtouch", "originos"),
        aggressive = true,
        autostartComponents = listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ),
        steps = listOf(OemStep(R.string.oem_vivo_1, OemTarget.AppDetails), OemStep(R.string.oem_vivo_2, OemTarget.Autostart), BATTERY_STEP),
    ),
    Samsung(
        manufacturerKeys = listOf("samsung"),
        romMarkers = emptyList(),
        aggressive = true,
        autostartComponents = emptyList(),
        steps = listOf(OemStep(R.string.oem_samsung_1, OemTarget.AppDetails), OemStep(R.string.oem_samsung_2, OemTarget.BatterySettings)),
    ),
    Asus(
        manufacturerKeys = listOf("asus"),
        romMarkers = emptyList(),
        aggressive = true,
        autostartComponents = listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        ),
        steps = listOf(OemStep(R.string.oem_asus_1, OemTarget.Autostart), BATTERY_STEP),
    ),
    Transsion(
        manufacturerKeys = listOf("transsion", "infinix", "tecno", "itel"),
        romMarkers = emptyList(),
        aggressive = true,
        autostartComponents = listOf(
            ComponentName("com.cyin.himgr", "com.cyin.himgr.AutobootManageActivity"),
            ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
        ),
        steps = listOf(OemStep(R.string.oem_transsion_1, OemTarget.Autostart), BATTERY_STEP),
    ),
    Unknown(
        manufacturerKeys = emptyList(),
        romMarkers = emptyList(),
        aggressive = false,
        autostartComponents = emptyList(),
        steps = listOf(BATTERY_STEP),
    );

    companion object {
        fun current(): OemProfile =
            detect(Build.MANUFACTURER, Build.BRAND, Build.DISPLAY, Build.FINGERPRINT)

        fun detect(manufacturer: String?, brand: String?, display: String?, fingerprint: String?): OemProfile {
            val maker = listOf(manufacturer, brand).joinToString(" ") { it.orEmpty().lowercase() }
            val rom = listOf(display, fingerprint).joinToString(" ") { it.orEmpty().lowercase() }
            return entries.firstOrNull { profile -> profile.manufacturerKeys.any { maker.contains(it) } }
                ?: entries.firstOrNull { profile -> profile.romMarkers.any { rom.contains(it) } }
                ?: Unknown
        }
    }
}
