package com.dordeorz.adimsayar.data

import android.content.Context
import android.util.Log
import com.dordeorz.adimsayar.band.BandEvent
import com.dordeorz.adimsayar.band.BandMusic
import com.dordeorz.adimsayar.band.BandNotice
import com.dordeorz.adimsayar.band.BandResult
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

    fun notify(notice: BandNotice) = post?.invoke { session?.sendNotification(notice) }

    fun dismiss(id: Int, packageName: String, key: String?) = post?.invoke { session?.dismissNotification(id, packageName, key) }

    fun call(name: String, number: String) = post?.invoke { session?.sendCall(name, number) }

    fun endCall() = post?.invoke { session?.endCall() }

    fun music(music: BandMusic) = post?.invoke { session?.sendMusic(music) }

    private suspend fun run(context: Context) {
        var retry = RETRY_MIN_MS
        while (coroutineContext.isActive) {
            val (address, name, secret) = BandDirect.device(context) ?: break
            val saver = BandDirect.BandSaver(context)
            var nextSync = 0L
            val band = XiaomiBand(context, address, name, secret)
            val result = band.live(
                store = saver::save,
                hooks = XiaomiLive(
                    shouldSync = { System.currentTimeMillis() >= nextSync },
                    onSynced = { result ->
                        nextSync = System.currentTimeMillis() + SYNC_INTERVAL_MS
                        connected = true
                        BandDirect.finish(context, ReadSource.Service, result, saver.files, session?.fileSummary.orEmpty())
                        saver.reset()
                        complete(result)
                        onConnected()
                    },
                ),
            ) { s, p ->
                s.onEvent = { onEvent(it) }
                session = s
                post = p
            }
            connected = false
            session = null
            post = null
            complete(if (result == BandResult.Ok) BandResult.ConnectFailed else result)
            Log.i(XiaomiBand.TAG, "live ended: $result, retry in ${retry / 1000}s")
            retry = if (result == BandResult.Ok) RETRY_MIN_MS else minOf(retry * 2, RETRY_MAX_MS)
            delay(retry)
        }
        isRunning = false
    }

    private fun complete(result: BandResult) {
        val list = synchronized(waiters) { waiters.toList().also { waiters.clear() } }
        list.forEach { it.complete(result) }
    }
}
