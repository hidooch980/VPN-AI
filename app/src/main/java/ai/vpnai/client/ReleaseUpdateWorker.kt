package ai.vpnai.client

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Checks the canonical GitHub Releases feed, downloads only a newer APK,
 * verifies the release asset SHA-256, then asks Android's package installer
 * to perform the update. Android still requires the user's install approval
 * for ordinary, non-device-owner applications.
 */
class ReleaseUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val release = getJson(RELEASE_API)
            val tag = release.optString("tag_name")
            val assets = release.optJSONArray("assets") ?: return@withContext Result.success()
            val packageInfo = applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
            @Suppress("DEPRECATION")
            val currentCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode
                else packageInfo.versionCode.toLong()
            var selectedUrl: String? = null
            var expectedDigest: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == APK_NAME) {
                    selectedUrl = asset.optString("browser_download_url")
                    expectedDigest = asset.optString("digest").removePrefix("sha256:")
                    break
                }
            }
            val remoteCode = tag.substringAfterLast(".").toLongOrNull()
                ?: release.optLong("target_commitish", -1L)
            if (selectedUrl.isNullOrBlank() || expectedDigest.isNullOrBlank() ||
                remoteCode <= currentCode) return@withContext Result.success()

            val apk = File(applicationContext.getExternalFilesDir(null), APK_NAME)
            download(selectedUrl, apk)
            val actual = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
                .joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedDigest, ignoreCase = true)) {
                apk.delete()
                return@withContext Result.failure()
            }
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PENDING_APK, apk.absolutePath).putString(PENDING_TAG, tag).apply()
            notifyUpdate(tag)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            .also { connection.disconnect() }
    }

    private fun download(address: String, destination: File) {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/octet-stream")
        try {
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun notifyUpdate(tag: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(EXTRA_INSTALL_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext, 4102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("VPN AI update is ready")
            .setContentText("Version $tag downloaded and verified. Tap to install.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { /* Notification permission may be disabled by Android. */ }
    }

    companion object {
        const val EXTRA_INSTALL_UPDATE = "ai.vpnai.client.INSTALL_UPDATE"
        const val PREFS = "release_updates"
        const val PENDING_APK = "pending_apk"
        const val PENDING_TAG = "pending_tag"
        private const val RELEASE_API = "https://api.github.com/repos/hidooch980/VPN-AI/releases/latest"
        private const val APK_NAME = "app-release.apk"
        private const val CHANNEL = "vpn_ai_updates"
        private const val NOTIFICATION_ID = 4102
    }
}
