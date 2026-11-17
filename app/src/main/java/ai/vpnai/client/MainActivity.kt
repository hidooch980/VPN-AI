package ai.vpnai.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ScrollView
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var profileView: TextView
    private var profileBytes: ByteArray? = null
    private val stateListener: (String) -> Unit = { state ->
        runOnUiThread { statusView.text = state }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        WireGuardSessionController.addListener(stateListener)
        statusView.text = WireGuardSessionController.currentStatus
        scheduleAutomaticUpdates()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        if (intent.getBooleanExtra(ReleaseUpdateWorker.EXTRA_INSTALL_UPDATE, false)) {
            installDownloadedUpdate()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra(ReleaseUpdateWorker.EXTRA_INSTALL_UPDATE, false) == true) {
            installDownloadedUpdate()
        }
    }

    override fun onDestroy() {
        WireGuardSessionController.removeListener(stateListener)
        super.onDestroy()
    }

    private fun scheduleAutomaticUpdates() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<ReleaseUpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "vpn-ai-release-update-check",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
        val immediate = OneTimeWorkRequestBuilder<ReleaseUpdateWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueue(immediate)
    }

    private fun installDownloadedUpdate() {
        val prefs = getSharedPreferences(ReleaseUpdateWorker.PREFS, MODE_PRIVATE)
        val path = prefs.getString(ReleaseUpdateWorker.PENDING_APK, null) ?: return
        val apk = File(path)
        if (!apk.isFile || apk.length() == 0L) {
            prefs.edit().remove(ReleaseUpdateWorker.PENDING_APK).apply()
            Toast.makeText(this, "فایل به‌روزرسانی معتبر نیست", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivityForResult(Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            ), REQUEST_INSTALL_PERMISSION)
            Toast.makeText(this, "برای نصب به‌روزرسانی، اجازه نصب برای VPN AI را فعال کنید", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.updates", apk)
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prefs.edit().remove(ReleaseUpdateWorker.PENDING_APK).apply()
            startActivity(install)
        } catch (error: Exception) {
            Toast.makeText(this, "باز کردن نصب‌کننده ناموفق بود: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildScreen() {
        window.statusBarColor = Color.rgb(11, 18, 32)
        window.navigationBarColor = Color.rgb(11, 18, 32)
        val scroll = ScrollView(this).apply {
            fillViewport = true
            setBackgroundColor(Color.rgb(11, 18, 32))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        scroll.addView(root)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val brand = column()
        brand.addView(label("VPN AI", 25f, WHITE, true))
        brand.addView(label("محافظ اتصال هوشمند", 12f, MUTED))
        header.addView(brand, LinearLayout.LayoutParams(0, dp(54), 1f))
        val logo = label("盾", 25f, TEAL, true).apply {
            gravity = Gravity.CENTER
            background = rounded(BLUE_CARD, 18)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))
        root.addView(header)
        space(root, 22)

        val hero = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = rounded(CARD, 24)
        }
        hero.addView(label("وضعیت اتصال", 14f, MUTED, true))
        space(hero, 14)
        val orb = label("⏻", 43f, TEAL, true).apply {
            gravity = Gravity.CENTER
            background = rounded(BLUE_CARD, 100)
        }
        hero.addView(orb, LinearLayout.LayoutParams(dp(92), dp(92)))
        space(hero, 13)
        statusView = label("آماده اتصال", 18f, WHITE, true).apply { gravity = Gravity.CENTER }
        hero.addView(statusView, matchWidth())
        space(hero, 7)
        hero.addView(label("برای شروع، پروفایل WireGuard خود را وارد کنید", 12f, MUTED).apply {
            gravity = Gravity.CENTER
        })
        root.addView(hero, matchWidth())
        space(root, 18)

        val profileCard = column().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(CARD, 18)
        }
        profileCard.addView(label("پروفایل اتصال", 15f, WHITE, true))
        space(profileCard, 7)
        profileView = label("هنوز پروفایلی انتخاب نشده است", 12f, MUTED)
        profileCard.addView(profileView, matchWidth())
        space(profileCard, 12)
        profileCard.addView(actionButton("＋   وارد کردن پروفایل WireGuard", BLUE) {
            openProfilePicker()
        }, matchWidth())
        root.addView(profileCard, matchWidth())
        space(root, 14)
        root.addView(actionButton("اتصال امن", TEAL_DARK) { requestConnect() }, matchWidth())
        space(root, 9)
        root.addView(actionButton("قطع اتصال", BUTTON_DARK) {
            WireGuardSessionController.disconnect()
        }, matchWidth())
        space(root, 20)

        root.addView(label("وضعیت شبکه", 17f, WHITE, true))
        space(root, 10)
        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        metrics.addView(metricCard("پینگ", "اندازه‌گیری نشده"), weightedParams())
        metrics.addView(metricCard("دانلود", "اندازه‌گیری نشده"), weightedParams().apply { marginStart = dp(8) })
        metrics.addView(metricCard("آپلود", "اندازه‌گیری نشده"), weightedParams().apply { marginStart = dp(8) })
        root.addView(metrics, matchWidth())
        space(root, 18)

        val security = column().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(CARD, 18)
        }
        security.addView(label("امنیت و محافظت", 15f, WHITE, true))
        space(security, 8)
        security.addView(label("وضعیت DNS، IPv4 و IPv6 پس از آزمون‌های واقعی نمایش داده می‌شود.", 12f, MUTED))
        space(security, 8)
        security.addView(label("محافظت سراسری و Kill Switch هنوز تأیید نشده‌اند.", 12f, AMBER))
        root.addView(security, matchWidth())
        space(root, 22)
        root.addView(label("VPN AI  •  نسخه اولیه", 11f, MUTED).apply { gravity = Gravity.CENTER }, matchWidth())
        setContentView(scroll)
    }

    private fun metricCard(title: String, value: String) = column().apply {
        setPadding(dp(10), dp(13), dp(10), dp(13))
        background = rounded(CARD, 16)
        addView(label(title, 12f, MUTED, true))
        space(this, 7)
        addView(label(value, 10f, WHITE))
    }

    private fun actionButton(title: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = title
        textSize = 14f
        isAllCaps = false
        setTextColor(WHITE)
        background = rounded(color, 15)
        minHeight = dp(52)
        setOnClickListener { action() }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun weightedParams() = LinearLayout.LayoutParams(0, dp(78), 1f)

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun openProfilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_PROFILE)
    }

    @Deprecated("Uses the platform activity result contract for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
                installDownloadedUpdate()
            } else {
                Toast.makeText(this, "مجوز نصب به‌روزرسانی فعال نشد", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQUEST_PROFILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val output = ByteArrayOutputStream()
                val input = contentResolver.openInputStream(uri) ?: error("فایل قابل خواندن نیست")
                input.use { stream ->
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= ProfileInputPolicy.MAX_BYTES) { "حجم پروفایل بیش از حد مجاز است" }
                        output.write(buffer, 0, count)
                    }
                }
                val bytes = output.toByteArray()
                ProfileInputPolicy.validateSize(bytes.size)
                profileBytes?.fill(0)
                profileBytes = bytes
                profileView.text = "پروفایل انتخاب شده و آماده اعتبارسنجی است"
                statusView.text = "پروفایل دریافت شد؛ آماده اتصال"
            } catch (error: Exception) {
                profileBytes?.fill(0)
                profileBytes = null
                profileView.text = "پروفایل بارگذاری نشد"
                statusView.text = "بارگذاری پروفایل ناموفق بود"
                Toast.makeText(this, error.message ?: "خطای فایل", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQUEST_VPN_CONSENT) {
            if (resultCode == RESULT_OK) connectWithLoadedProfile()
            else statusView.text = "مجوز VPN داده نشد"
        }
    }

    private fun requestConnect() {
        if (profileBytes == null) {
            Toast.makeText(this, "ابتدا پروفایل خود را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val consentIntent = VpnService.prepare(this)
        if (consentIntent != null) startActivityForResult(consentIntent, REQUEST_VPN_CONSENT)
        else connectWithLoadedProfile()
    }

    private fun connectWithLoadedProfile() {
        val bytes = profileBytes ?: return
        profileBytes = null
        statusView.text = "در حال اعتبارسنجی و اتصال…"
        WireGuardSessionController.connect(applicationContext, bytes)
    }

    companion object {
        private const val REQUEST_PROFILE = 1001
        private const val REQUEST_VPN_CONSENT = 1002
        private const val REQUEST_INSTALL_PERMISSION = 1003
        private const val REQUEST_NOTIFICATIONS = 1004
    }
}
