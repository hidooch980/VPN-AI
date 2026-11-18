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

/** Downloads a newer signed release, verifies its published SHA-256, then requests user-approved installation. */
class ReleaseUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var temporary: File? = null
        try {
            val release = getJson(RELEASE_API)
            val tag = release.optString("tag_name")
            val remoteCode = ReleaseVersionPolicy.versionCode(tag)
                ?: return@withContext Result.failure()
            val packageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            @Suppress("DEPRECATION")
            val currentCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
            if (remoteCode <= currentCode) return@withContext Result.success()

            val assets = release.optJSONArray("assets") ?: return@withContext Result.retry()
            var apkUrl: String? = null
            var digest: String? = null
            var checksumUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                when (asset.optString("name")) {
                    APK_NAME -> {
                        apkUrl = asset.optString("browser_download_url")
                        digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                    }
                    CHECKSUM_NAME -> checksumUrl = asset.optString("browser_download_url")
                }
            }
            if (apkUrl.isNullOrBlank()) return@withContext Result.retry()
            if (digest == null && !checksumUrl.isNullOrBlank()) {
                digest = fetchExpectedDigest(checksumUrl!!)
            }
            if (digest == null) return@withContext Result.retry()

            val directory = applicationContext.getExternalFilesDir(null) ?: return@withContext Result.retry()
            val destination = File(directory, APK_NAME)
            temporary = File(directory, "$APK_NAME.part")
            if (temporary!!.exists()) temporary!!.delete()
            download(apkUrl!!, temporary!!)
            val actual = sha256(temporary!!)
            if (!actual.equals(digest, ignoreCase = true)) {
                temporary!!.delete()
                return@withContext Result.failure()
            }
            if (destination.exists() && !destination.delete()) return@withContext Result.retry()
            if (!temporary!!.renameTo(destination)) return@withContext Result.retry()
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PENDING_APK, destination.absolutePath).putString(PENDING_TAG, tag).apply()
            notifyUpdate(tag)
            Result.success()
        } catch (_: Exception) {
            temporary?.delete()
            Result.retry()
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "GitHub release request failed" }
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }

    private fun fetchExpectedDigest(address: String): String? {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            val lines = connection.inputStream.bufferedReader().use { it.readLines() }
            val line = lines.firstOrNull { it.trim().endsWith("  $APK_NAME") || it.trim().endsWith(" *$APK_NAME") }
                ?: return null
            return line.trim().substringBefore(' ').takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
        } finally { connection.disconnect() }
    }

    private fun download(address: String, destination: File) {
        require(URL(address).protocol == "https" && URL(address).host == "github.com") {
            "Release asset must originate from github.com over HTTPS"
        }
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/octet-stream")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "APK download failed" }
            val expectedLength = connection.contentLengthLong
            require(expectedLength in 1..MAX_APK_BYTES) { "Unexpected APK size" }
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES && total <= expectedLength) { "APK exceeds expected size" }
                        output.write(buffer, 0, count)
                    }
                    check(total == expectedLength) { "Incomplete APK download" }
                }
            }
        } finally { connection.disconnect() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun notifyUpdate(tag: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(EXTRA_INSTALL_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(applicationContext, 4102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("VPN AI update is ready")
            .setContentText("Version $tag downloaded and verified. Tap to install.")
            .setContentIntent(pending).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { /* Notifications may be disabled by the user. */ }
    }

    companion object {
        const val EXTRA_INSTALL_UPDATE = "ai.vpnai.client.INSTALL_UPDATE"
        const val PREFS = "release_updates"
        const val PENDING_APK = "pending_apk"
        const val PENDING_TAG = "pending_tag"
        private const val RELEASE_API = "https://api.github.com/repos/hidooch980/VPN-AI/releases/latest"
        private const val APK_NAME = "app-release.apk"
        private const val CHECKSUM_NAME = "SHA256SUMS.txt"
        private const val MAX_APK_BYTES = 100L * 1024L * 1024L
        private const val CHANNEL = "vpn_ai_updates"
        private const val NOTIFICATION_ID = 4102
    }
}
