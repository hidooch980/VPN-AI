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
                // Backend UP is not equivalent to verified internet/data-plane connectivity.
                val verified = WireGuardSessionController.connectionState == ConnectionState.ONLINE
                statusView.setTextColor(if (verified) CYAN else WARNING)
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
            clipToPadding = false
        }
        val root = column().apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }

        // Brand header inspired by the supplied MolidoVPN reference.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = column()
        brand.addView(label("MolidoVPN", 23f, WHITE, true))
        brand.addView(label(tr("شبکه خصوصی هوشمند", "SMART PRIVATE NETWORK"), 9f, MUTED, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(pill(tr("●  متصل نیست", "●  OFFLINE"), WARNING))
        root.addView(header, matchWrap())
        root.addView(space(22))

        root.addView(label(tr("داشبورد", "DASHBOARD"), 11f, CYAN, true))
        root.addView(space(9))

        // Main connection card.
        val hero = column().apply {
            background = rounded(CARD, 26, STROKE)
            setPadding(dp(18), dp(18), dp(18), dp(16))
        }
        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        val actionColumn = column().apply { gravity = Gravity.CENTER }
        connectButton = actionButton("⏻", CYAN, BG).apply {
            textSize = 35f
            minWidth = dp(76)
            minHeight = dp(76)
            setOnClickListener { requestConnect() }
        }
        actionColumn.addView(connectButton, LinearLayout.LayoutParams(dp(82), dp(82)))
        actionColumn.addView(space(6))
        actionColumn.addView(label(tr("برای اتصال", "TAP TO CONNECT"), 9f, MUTED, true))
        heroTop.addView(actionColumn, LinearLayout.LayoutParams(dp(94), -2))

        val stateColumn = column().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(4), 0)
        }
        stateColumn.addView(pill(tr("آماده اتصال", "READY TO CONNECT"), CYAN))
        stateColumn.addView(space(10))
        stateColumn.addView(label(tr("اتصال برقرار نیست", "Not connected"), 18f, WHITE, true).also { statusView = it })
        stateColumn.addView(space(5))
        stateColumn.addView(label(tr("برای شروع، پروفایل WireGuard را وارد کنید", "Import a WireGuard profile to begin"), 11f, MUTED, false))
        heroTop.addView(stateColumn, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(heroTop, matchWrap())
        hero.addView(space(16))

        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(BG, 18, STROKE)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        metrics.addView(metricCell(tr("پینگ", "PING"), "—", CYAN), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(metricCell(tr("دانلود", "DOWNLOAD"), "— B/s", MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(metricCell(tr("آپلود", "UPLOAD"), "— B/s", MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(metrics, matchWrap())
        hero.addView(space(12))
        disconnectButton = actionButton(tr("قطع اتصال", "DISCONNECT"), PANEL, WHITE).apply {
            setOnClickListener { WireGuardSessionController.disconnect() }
        }
        hero.addView(disconnectButton, matchWrap())
        root.addView(hero, matchWrap())

        root.addView(space(13))
        val ipCard = card().apply {
            background = rounded(Color.rgb(15, 34, 48), 20, Color.rgb(105, 99, 59))
        }
        ipCard.addView(label(tr("آی‌پی عمومی", "PUBLIC IP"), 10f, MUTED, true))
        ipCard.addView(space(5))
        ipCard.addView(label("— — — — — —", 21f, WHITE, true))
        ipCard.addView(label(tr("پس از اتصال و دریافت داده واقعی نمایش داده می‌شود", "Shown after connection and verified measurement"), 10f, MUTED, false))
        root.addView(ipCard, matchWrap())

        root.addView(space(15))
        root.addView(sectionTitle(tr("وضعیت سرویس‌ها", "SERVICE STATUS")))
        val pair = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        val serverCard = card()
        serverCard.addView(label("◎  " + tr("سرورها", "SERVERS"), 13f, CYAN, true))
        serverCard.addView(space(7))
        serverCard.addView(label(tr("فهرست سرور متصل نیست", "No server directory connected"), 11f, WHITE, true))
        serverCard.addView(space(4))
        serverCard.addView(label(tr("موقعیت و پینگ نامشخص", "Location and ping unavailable"), 10f, MUTED, false))
        val securityCard = card()
        securityCard.addView(label("⬡  " + tr("امنیت", "SECURITY"), 13f, CYAN, true))
        securityCard.addView(space(7))
        securityCard.addView(label(tr("آزمون‌ها تأیید نشده‌اند", "Tests not verified"), 11f, WHITE, true))
        securityCard.addView(space(4))
        securityCard.addView(label(tr("DNS • IPv4/IPv6 • Kill Switch", "DNS • IPv4/IPv6 • Kill Switch"), 9f, MUTED, false))
        pair.addView(serverCard, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        pair.addView(securityCard, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        root.addView(pair, matchWrap())

        root.addView(space(13))
        root.addView(sectionTitle(tr("هوش مصنوعی و ترافیک", "AI & TRAFFIC")))
        val intelligence = card()
        intelligence.addView(label(tr("دستیار هوشمند", "AI ASSISTANT"), 13f, CYAN, true))
        intelligence.addView(space(5))
        intelligence.addView(label(tr("تحلیل و انتخاب خودکار تا زمان اتصال منابع داده معتبر غیرفعال است.", "Analysis and automatic selection stay inactive until trusted live data sources are connected."), 11f, MUTED, false))
        intelligence.addView(separator())
        intelligence.addView(infoRow(tr("مصرف ترافیک", "Traffic usage"), tr("هنوز اندازه‌گیری نشده", "Not measured"), MUTED))
        intelligence.addView(space(8))
        val importButton = actionButton(tr("＋  وارد کردن پروفایل WireGuard", "＋  IMPORT WIREGUARD PROFILE"), DARK_CYAN, CYAN).apply {
            setOnClickListener { openProfilePicker() }
        }
        intelligence.addView(importButton, matchWrap())
        profileView = label(tr("پروفایلی انتخاب نشده", "No profile selected"), 10f, MUTED, false)
        intelligence.addView(space(7))
        intelligence.addView(profileView)
        root.addView(intelligence, matchWrap())

        root.addView(space(18))
        root.addView(label(tr("MOLIDOVPN  •  حریم خصوصی با داده‌های قابل‌اعتماد", "MOLIDOVPN  •  PRIVACY BUILT ON VERIFIED DATA"), 9f, MUTED, true).apply {
            gravity = Gravity.CENTER
        })
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun metricCell(title: String, value: String, color: Int): View {
        val cell = column().apply { gravity = Gravity.CENTER }
        cell.addView(label(title, 9f, MUTED, true).apply { gravity = Gravity.CENTER })
        cell.addView(space(4))
        cell.addView(label(value, 12f, color, true).apply { gravity = Gravity.CENTER })
        return cell
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
        private val BG = Color.rgb(8, 13, 25)
        private val CARD = Color.rgb(17, 27, 45)
        private val PANEL = Color.rgb(26, 39, 59)
        private val DARK_CYAN = Color.rgb(13, 48, 60)
        private val STROKE = Color.rgb(42, 62, 84)
        private val CYAN = Color.rgb(72, 225, 208)
        private val WHITE = Color.rgb(238, 245, 255)
        private val MUTED = Color.rgb(148, 166, 190)
        private val WARNING = Color.rgb(255, 190, 90)
    }
}
