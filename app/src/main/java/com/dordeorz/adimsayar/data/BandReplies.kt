package com.dordeorz.adimsayar.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.dordeorz.adimsayar.band.XiaomiBand

/**
 * Quick replies: rejecting a call on the band can send the caller one of these as a text message.
 * The band keeps the list; FitWren mirrors it so it can be edited, and sends the SMS itself.
 */
object BandReplies {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_LIST = "band_replies"
    private const val KEY_MIN = "band_replies_min"
    private const val KEY_MAX = "band_replies_max"
    private const val SEPARATOR = "\u001f"

    /** SMS to send, and the caller's number (the call notification often has no number). */
    val PERMISSIONS: Array<String> = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)

    /** Number of the call that is ringing now, from the phone state broadcast. */
    @Volatile
    var ringingNumber: String? = null
        internal set

    fun granted(context: Context): Boolean =
        PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun replies(context: Context): List<String> =
        prefs(context).getString(KEY_LIST, null)?.split(SEPARATOR)?.filter { it.isNotBlank() && it != "-" }.orEmpty()

    /** How many replies the band allows; 0 until the band told us. */
    fun max(context: Context): Int = prefs(context).getInt(KEY_MAX, 0)

    /** The band's own list, read after each connect. */
    fun fromBand(context: Context, replies: List<String>, min: Int, max: Int) {
        Log.i(XiaomiBand.TAG, "replies ${replies.size} min=$min max=$max")
        prefs(context).edit {
            putString(KEY_LIST, replies.joinToString(SEPARATOR))
            putInt(KEY_MIN, min)
            putInt(KEY_MAX, max)
        }
    }

    /** Saves the user's list and sends it to the band; false when the band is not connected. */
    fun save(context: Context, replies: List<String>): Boolean {
        val clean = replies.map { it.trim() }.filter { it.isNotEmpty() }
        val p = prefs(context)
        p.edit { putString(KEY_LIST, clean.joinToString(SEPARATOR)) }
        return BandLive.setReplies(clean, p.getInt(KEY_MIN, 0), p.getInt(KEY_MAX, 0))
    }

    /** Sends [message] to the caller; [bandNumber] is what the band got with the call. */
    fun send(context: Context, bandNumber: String, message: String): Boolean {
        if (message.isBlank() || ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        val number = ringingNumber?.takeIf(::looksLikeNumber) ?: bandNumber.takeIf(::looksLikeNumber) ?: return false
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return false
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(message), null, null)
            true
        } catch (e: Exception) {
            Log.w(XiaomiBand.TAG, "reply sms: $e")
            false
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}

internal fun looksLikeNumber(text: String): Boolean {
    val digits = text.count(Char::isDigit)
    return digits >= 3 && text.all { it.isDigit() || it in "+-() ." }
}

/** Remembers the ringing number; READ_CALL_LOG is needed for the number to be included. */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.takeIf { it.isNotBlank() }?.let { BandReplies.ringingNumber = it }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> BandReplies.ringingNumber = null
        }
    }
}
