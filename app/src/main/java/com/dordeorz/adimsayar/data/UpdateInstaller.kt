package com.dordeorz.adimsayar.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class InstallStart { Started, NeedsPermission, Failed }

object UpdateInstaller {

    private const val DIRECTORY = "updates"
    private const val TIMEOUT_MS = 15_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val APK_TYPE = "application/vnd.android.package-archive"

    suspend fun download(context: Context, url: String, version: String, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, DIRECTORY)
        directory.deleteRecursively()
        directory.mkdirs()
        val target = File(directory, "adimsayar-$version.apk")
        val partial = File(directory, "${target.name}.part")
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = true
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val total = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                var done = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0L) onProgress((done * 100 / total).toInt())
                        }
                    }
                }
                if (total > 0L && done != total) return@runCatching null
                if (partial.renameTo(target)) target else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull().also { if (it == null) partial.delete() }
    }

    fun install(context: Context, apk: File): InstallStart {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(settings)
                InstallStart.NeedsPermission
            } catch (e: ActivityNotFoundException) {
                InstallStart.Failed
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            InstallStart.Started
        } catch (e: ActivityNotFoundException) {
            InstallStart.Failed
        }
    }
}
