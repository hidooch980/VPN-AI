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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var profileView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private var profileBytes: ByteArray? = null

    private val isPersian: Boolean
        get() = Locale.getDefault().language in listOf("fa", "ps", "ur")

    private fun tr(fa: String, en: String) = if (isPersian) fa else en

    private val stateListener: (String) -> Unit = { state ->
        runOnUiThread {
            if (::statusView.isInitialized) {
                statusView.text = state
                val up = state.contains("بالا") || state.contains("UP", ignoreCase = true)
                statusView.setTextColor(if (up) CYAN else MUTED)
            }
        }
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
            Toast.makeText(this, tr("فایل به‌روزرسانی معتبر نیست", "Update file is invalid"), Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivityForResult(Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            ), REQUEST_INSTALL_PERMISSION)
            Toast.makeText(this, tr("برای نصب به‌روزرسانی، اجازه نصب را فعال کنید", "Allow app installs to continue"), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Installer error: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildScreen() {
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = column().apply {
            addView(label("VPN AI", 25f, WHITE, true))
            addView(label(tr("شبکه امن هوشمند", "INTELLIGENT SECURE NETWORK"), 10f, MUTED, false))
        }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(pill(tr("نسخه پایه", "FOUNDATION"), CYAN))
        root.addView(header, matchWrap())
        root.addView(space(22))

        root.addView(label(tr("مرکز کنترل", "CONTROL CENTER"), 11f, CYAN, true))
        root.addView(space(8))
        val hero = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(20))
            background = rounded(CARD, 24, STROKE)
        }
        val orb = TextView(this).apply {
            text = "◉"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(CYAN)
            background = rounded(DARK_CYAN, 60, CYAN)
            setPadding(dp(16), dp(5), dp(16), dp(5))
        }
        hero.addView(orb)
        hero.addView(space(12))
        hero.addView(label(tr("اتصال برقرار نیست", "NOT CONNECTED"), 17f, WHITE, true).also { statusView = it })
        hero.addView(space(5))
        hero.addView(label(tr("برای شروع، پروفایل WireGuard را وارد کنید", "Import a WireGuard profile to get started"), 12f, MUTED, false))
        hero.addView(space(16))
        connectButton = actionButton(tr("اتصال", "CONNECT"), CYAN, BG).apply {
            setOnClickListener { requestConnect() }
        }
        disconnectButton = actionButton(tr("قطع اتصال", "DISCONNECT"), PANEL, WHITE).apply {
            setOnClickListener { WireGuardSessionController.disconnect() }
        }
        hero.addView(connectButton, matchWrap())
        hero.addView(space(8))
        hero.addView(disconnectButton, matchWrap())
        root.addView(hero, matchWrap())

        root.addView(space(18))
        root.addView(sectionTitle(tr("شبکه و ترافیک", "NETWORK & TRAFFIC")))
        val network = card()
        network.addView(infoRow(tr("وضعیت تونل", "Tunnel state"), tr("هنوز متصل نیست", "Not connected"), CYAN))
        network.addView(separator())
        network.addView(infoRow(tr("پینگ", "Latency"), tr("پس از اتصال و اندازه‌گیری", "Awaiting measurement"), MUTED))
        network.addView(separator())
        network.addView(infoRow(tr("دانلود / آپلود", "Download / Upload"), tr("داده آماری موجود نیست", "No telemetry available"), MUTED))
        network.addView(separator())
        network.addView(infoRow(tr("مصرف داده", "Data usage"), tr("هنوز اندازه‌گیری نمی‌شود", "Not measured yet"), MUTED))
        root.addView(network)

        root.addView(space(14))
        root.addView(sectionTitle(tr("کشورها و سرورها", "LOCATIONS & SERVERS")))
        val servers = card()
        servers.addView(label(tr("انتخاب سرور در دسترس نیست", "Server selection unavailable"), 14f, WHITE, true))
        servers.addView(space(5))
        servers.addView(label(tr("هنوز فهرست سرور معتبر یا سرویس دریافت موقعیت متصل نشده است. هیچ کشور یا پینگی حدس زده نمی‌شود.", "No verified server directory or location provider is connected yet. Countries and latency are not fabricated."), 12f, MUTED, false))
        servers.addView(space(10))
        val importButton = actionButton(tr("وارد کردن فایل کانفیگ", "IMPORT CONFIG FILE"), DARK_CYAN, CYAN).apply {
            setOnClickListener { openProfilePicker() }
        }
        servers.addView(importButton, matchWrap())
        profileView = label(tr("پروفایلی انتخاب نشده", "No profile selected"), 11f, MUTED, false)
        servers.addView(space(8))
        servers.addView(profileView)
        root.addView(servers)

        root.addView(space(14))
        root.addView(sectionTitle(tr("امنیت و محافظت", "SECURITY & PROTECTION")))
        val security = card()
        security.addView(infoRow(tr("موتور WireGuard", "WireGuard engine"), tr("یکپارچه‌سازی اولیه", "Integrated; device test pending"), CYAN))
        security.addView(separator())
        security.addView(infoRow(tr("آزمون نشت DNS و IP", "DNS / IP leak tests"), tr("تأیید نشده", "Not verified"), WARNING))
        security.addView(separator())
        security.addView(infoRow(tr("Kill Switch", "Kill switch"), tr("هنوز تأیید نشده", "Not verified"), WARNING))
        root.addView(security)

        root.addView(space(14))
        root.addView(sectionTitle(tr("دستیار هوشمند", "AI ASSISTANT")))
        val ai = card()
        ai.addView(label(tr("موتور تحلیل هوشمند در حال توسعه است", "AI decision engine is under development"), 14f, WHITE, true))
        ai.addView(space(5))
        ai.addView(label(tr("انتخاب خودکار سرور و تصمیم‌گیری شبکه فقط پس از اتصال منابع داده واقعی فعال می‌شود.", "Automatic server selection and network decisions will remain inactive until verified live data sources are integrated."), 12f, MUTED, false))
        root.addView(ai)

        root.addView(space(18))
        root.addView(label(tr("VPN AI  •  داده‌های واقعی، بدون آمار ساختگی", "VPN AI  •  REAL DATA, NO FABRICATED METRICS"), 10f, MUTED, false).apply { gravity = Gravity.CENTER })
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun requestConnect() {
        if (profileBytes == null) {
            Toast.makeText(this, tr("ابتدا پروفایل WireGuard را وارد کنید", "Import a WireGuard profile first"), Toast.LENGTH_SHORT).show()
            return
        }
        val consentIntent = VpnService.prepare(this)
        if (consentIntent != null) startActivityForResult(consentIntent, REQUEST_VPN_CONSENT)
        else connectWithLoadedProfile()
    }

    private fun connectWithLoadedProfile() {
        val bytes = profileBytes ?: return
        profileBytes = null
        profileView.text = tr("پروفایل برای اتصال مصرف شد", "Profile consumed for connection")
        statusView.text = tr("در حال اعتبارسنجی و اتصال…", "Validating and connecting…")
        WireGuardSessionController.connect(applicationContext, bytes)
    }

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
                Toast.makeText(this, tr("مجوز نصب به‌روزرسانی فعال نشد", "Install permission was not granted"), Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQUEST_PROFILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val output = ByteArrayOutputStream()
                val input = contentResolver.openInputStream(uri) ?: error("Cannot read selected file")
                input.use { stream ->
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= ProfileInputPolicy.MAX_BYTES) { "Profile file is too large" }
                        output.write(buffer, 0, count)
                    }
                }
                val bytes = output.toByteArray()
                ProfileInputPolicy.validateSize(bytes.size)
                profileBytes?.fill(0)
                profileBytes = bytes
                profileView.text = tr("پروفایل انتخاب شد؛ آماده اعتبارسنجی", "Profile selected; ready for validation")
                statusView.text = tr("پروفایل دریافت شد؛ برای اتصال آماده است", "Profile loaded; ready to connect")
            } catch (error: Exception) {
                profileBytes?.fill(0)
                profileBytes = null
                profileView.text = tr("بارگذاری پروفایل ناموفق بود", "Profile import failed")
                statusView.text = tr("خطا در دریافت پروفایل", "Profile import error")
                Toast.makeText(this, error.message ?: "Invalid profile", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQUEST_VPN_CONSENT) {
            if (resultCode == RESULT_OK) connectWithLoadedProfile()
            else statusView.text = tr("مجوز VPN داده نشد", "VPN permission was not granted")
        }
    }

    private fun sectionTitle(value: String) = label(value, 13f, CYAN, true).apply {
        setPadding(dp(2), dp(0), dp(2), dp(8))
    }

    private fun card() = column().apply {
        background = rounded(CARD, 18, STROKE)
        setPadding(dp(15), dp(14), dp(15), dp(14))
    }

    private fun infoRow(name: String, value: String, valueColor: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        row.addView(label(name, 12f, MUTED, false), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(value, 11f, valueColor, true))
        return row
    }

    private fun separator() = View(this).apply {
        setBackgroundColor(STROKE)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(10); bottomMargin = dp(10)
        }
    }

    private fun actionButton(text: String, backgroundColor: Int, textColor: Int) = Button(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        background = rounded(backgroundColor, 14, backgroundColor)
        isAllCaps = false
        minHeight = dp(48)
        stateListAnimator = null
    }

    private fun pill(text: String, color: Int) = TextView(this).apply {
        this.text = text
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color)
        background = rounded(DARK_CYAN, 30, STROKE)
        setPadding(dp(11), dp(7), dp(11), dp(7))
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = if (isPersian) Gravity.RIGHT else Gravity.LEFT
        textDirection = View.TEXT_DIRECTION_LOCALE
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    companion object {
        private const val REQUEST_PROFILE = 1001
        private const val REQUEST_VPN_CONSENT = 1002
        private const val REQUEST_INSTALL_PERMISSION = 1003
        private const val REQUEST_NOTIFICATIONS = 1004
        private const val BG = Color.rgb(8, 13, 25)
        private const val CARD = Color.rgb(17, 27, 45)
        private const val PANEL = Color.rgb(26, 39, 59)
        private const val DARK_CYAN = Color.rgb(13, 48, 60)
        private const val STROKE = Color.rgb(42, 62, 84)
        private const val CYAN = Color.rgb(72, 225, 208)
        private const val WHITE = Color.rgb(238, 245, 255)
        private const val MUTED = Color.rgb(148, 166, 190)
        private const val WARNING = Color.rgb(255, 190, 90)
    }
}
