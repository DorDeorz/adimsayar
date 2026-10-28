package com.dordeorz.adimsayar.data

import android.os.Build
import java.io.IOException

// Tells the maker's own Android (MIUI/HyperOS and the like) apart from AOSP-based custom ROMs such as
// crDroid or LineageOS: those keep the maker name in Build.MANUFACTURER but drop its step record and
// battery screens.
object RomInfo {

    private val CUSTOM_ROM_PROPS = listOf("ro.crdroid.version", "ro.lineage.version", "ro.modversion", "ro.pixelexperience.version", "ro.evolution.version")
    private val MIUI_PROPS = listOf("ro.miui.ui.version.name", "ro.mi.os.version.name")

    val isCustomRom: Boolean by lazy { CUSTOM_ROM_PROPS.any { !prop(it).isNullOrBlank() } }

    // True on MIUI/HyperOS. When the properties cannot be read, a Xiaomi phone is assumed to run its own ROM.
    val isMiui: Boolean by lazy {
        if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return@lazy false
        if (isCustomRom) return@lazy false
        val values = MIUI_PROPS.map { prop(it) }
        values.all { it == null } || values.any { !it.isNullOrBlank() }
    }

    // False when the phone runs a custom ROM, so maker-specific battery steps do not apply.
    val isVendorRom: Boolean by lazy {
        when {
            isCustomRom -> false
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) -> isMiui
            else -> true
        }
    }

    private fun prop(name: String): String? =
        try {
            val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
            val value = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.waitFor() == 0) value else null
        } catch (e: IOException) {
            null
        } catch (e: InterruptedException) {
            null
        } catch (e: SecurityException) {
            null
        }
}
