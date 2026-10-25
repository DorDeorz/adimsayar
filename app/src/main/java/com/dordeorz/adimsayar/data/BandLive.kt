package com.dordeorz.adimsayar.data

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dordeorz.adimsayar.background.BandAlerts
import com.dordeorz.adimsayar.band.BandEvent
import com.dordeorz.adimsayar.band.BandMusic
import com.dordeorz.adimsayar.band.BandNotice
import com.dordeorz.adimsayar.band.BandResult
import com.dordeorz.adimsayar.band.BandWeatherReport
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.band.XiaomiLive
import com.dordeorz.adimsayar.band.XiaomiSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * The always-open band connection used for notifications, calls and music. It also syncs steps
 * and health data every 15 minutes, and other syncs go through it while it runs. It lives in the
 * app process, which Android keeps alive because FitWren holds notification access.
 */
object BandLive {

    private const val SYNC_INTERVAL_MS = 15 * 60 * 1000L
    private const val SYNC_WAIT_MS = 60_000L
    private const val RETRY_MIN_MS = 30_000L
    private const val RETRY_MAX_MS = 10 * 60 * 1000L
    private const val LOST_AFTER_MS = 60_000L
    private const val FIND_BAND_MS = 30_000L

    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor { Thread(it, "FitWrenBandLive") }.asCoroutineDispatcher(),
    )
    private var job: Job? = null

    @Volatile
    var isRunning = false
        private set

    @Volatile
    var connected = false
        private set

    @Volatile
    private var session: XiaomiSession? = null

    @Volatile
    private var post: ((() -> Unit) -> Unit)? = null

    private val waiters = ArrayList<CompletableDeferred<BandResult>>()
    private val main = Handler(Looper.getMainLooper())
    private var lostCheck: Runnable? = null
    private val bandFound = Runnable { findingBand = false }

    /** True while the band vibrates because the user pressed "Find band" in the app. */
    @Volatile
    var findingBand = false
        private set

    /** Band-side actions; set by the notification listener. Called on the connection thread. */
    @Volatile
    var onEvent: (BandEvent) -> Unit = {}

    /** Called after each sync while connected, so the band can get the current music state. */
    @Volatile
    var onConnected: () -> Unit = {}

    /** Starts or stops the connection to match the settings. */
    @Synchronized
    fun refresh(context: Context) {
        val app = context.applicationContext
        val wanted = BandNotify.wanted(app)
        Log.i(XiaomiBand.TAG, "live refresh: wanted=$wanted running=${job?.isActive == true}")
        if (wanted && job?.isActive != true) {
            isRunning = true
            job = scope.launch { run(app) }
        } else if (!wanted && job != null) {
            stop()
        }
    }

    /** Reconnects with the current device and key. */
    @Synchronized
    fun restart(context: Context) {
        if (job != null) stop()
        refresh(context)
    }

    @Synchronized
    private fun stop() {
        lostCheck?.let { main.removeCallbacks(it) }
        lostCheck = null
        job?.cancel()
        job = null
        isRunning = false
        connected = false
        session = null
        post = null
    }

    /** Asks the open connection for a sync. Null when the band is not connected right now. */
    suspend fun syncNow(): BandResult? {
        val send = post ?: return null
        val done = CompletableDeferred<BandResult>()
        send {
            synchronized(waiters) { waiters += done }
            session?.requestSync()
        }
        return withTimeoutOrNull(SYNC_WAIT_MS) { done.await() }
    }

    fun notify(notice: BandNotice) {
        Log.i(XiaomiBand.TAG, "notify ${notice.id} ${notice.packageName} connected=$connected")
        post?.invoke { session?.sendNotification(notice) }
    }

    fun dismiss(id: Int, packageName: String, key: String?) = post?.invoke { session?.dismissNotification(id, packageName, key) }

    fun call(name: String, number: String) = post?.invoke { session?.sendCall(name, number) }

    fun endCall() = post?.invoke { session?.endCall() }

    fun music(music: BandMusic) = post?.invoke { session?.sendMusic(music) }

    /** Starts or stops the band's vibration; false when the band is not connected. */
    fun findBand(start: Boolean): Boolean {
        val send = post ?: return false
        if (!connected) return false
        send { session?.findBand(start) }
        findingBand = start
        main.removeCallbacks(bandFound)
        if (start) main.postDelayed(bandFound, FIND_BAND_MS)
        return true
    }

    fun findPhoneStopped() = post?.invoke { session?.findPhoneStopped() }

    /** Sends the quick replies to the band; false when it is not connected. */
    fun setReplies(replies: List<String>, min: Int, max: Int): Boolean {
        val send = post ?: return false
        if (!connected) return false
        send { session?.setReplies(replies, min, max) }
        return true
    }

    fun weather(report: BandWeatherReport) = post?.invoke { session?.sendWeather(report) }

    fun replySent(success: Boolean) = post?.invoke { session?.replySent(success) }

    private fun onBandEvent(context: Context, event: BandEvent) {
        when (event) {
            is BandEvent.FindPhone -> if (event.start) BandAlerts.startRinging(context) else BandAlerts.stopRinging(context, tellBand = false)
            BandEvent.WeatherRequest -> BandWeather.refresh(context)
            BandEvent.BandFound -> {
                findingBand = false
                main.removeCallbacks(bandFound)
            }
            else -> onEvent(event)
        }
    }

    /** Warns on the phone when the band stays away for a minute after being connected. */
    private fun watchLost(context: Context) {
        lostCheck?.let { main.removeCallbacks(it) }
        val check = Runnable {
            lostCheck = null
            val bluetoothOn = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
            if (isRunning && !connected && bluetoothOn && BandNotify.lostAlert(context)) BandAlerts.lost(context)
        }
        lostCheck = check
        main.postDelayed(check, LOST_AFTER_MS)
    }

    private fun onBack(context: Context) {
        lostCheck?.let { main.removeCallbacks(it) }
        lostCheck = null
        BandAlerts.cancelLost(context)
    }

    private suspend fun run(context: Context) {
        var retry = RETRY_MIN_MS
        var failures = 0
        while (coroutineContext.isActive) {
            val (address, name, secret) = BandDirect.device(context) ?: break
            val saver = BandDirect.BandSaver(context)
            var nextSync = 0L
            val band = XiaomiBand(context, address, name, secret)
            // A quick direct connect first; once that fails the band is away, so let Android wait for it.
            val autoConnect = failures > 0
            Log.i(XiaomiBand.TAG, "live start, autoConnect=$autoConnect")
            val result = band.live(
                store = saver::save,
                hooks = XiaomiLive(
                    shouldSync = { System.currentTimeMillis() >= nextSync },
                    onSynced = { result ->
                        nextSync = System.currentTimeMillis() + SYNC_INTERVAL_MS
                        if (!connected) onBack(context)
                        connected = true
                        BandDirect.finish(context, ReadSource.Service, result, saver.files, session?.fileSummary.orEmpty())
                        saver.reset()
                        complete(result)
                        Log.i(XiaomiBand.TAG, "live synced: $result")
                        onConnected()
                        BandWeather.refresh(context)
                    },
                ),
                autoConnect = autoConnect,
            ) { s, p ->
                s.onEvent = { onBandEvent(context, it) }
                session = s
                post = p
            }
            val wasConnected = connected
            connected = false
            session = null
            post = null
            findingBand = false
            if (wasConnected) watchLost(context)
            complete(if (result == BandResult.Ok) BandResult.ConnectFailed else result)
            Log.i(XiaomiBand.TAG, "live ended: $result, retry in ${retry / 1000}s")
            if (result == BandResult.Ok) {
                failures = 0
                retry = RETRY_MIN_MS
            } else {
                failures++
                retry = minOf(retry * 2, RETRY_MAX_MS)
            }
            // autoConnect waits by itself, so retry it right away; a quick connect waits [retry] first.
            delay(if (failures == 1) 0L else retry)
        }
        isRunning = false
    }

    private fun complete(result: BandResult) {
        val list = synchronized(waiters) { waiters.toList().also { waiters.clear() } }
        list.forEach { it.complete(result) }
    }
}
