package ai.vpnai.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private var profileBytes: ByteArray? = null
    private val stateListener: (String) -> Unit = { state ->
        runOnUiThread { statusView.text = state }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        WireGuardSessionController.addListener(stateListener)
        statusView.text = WireGuardSessionController.currentStatus
    }

    override fun onDestroy() {
        WireGuardSessionController.removeListener(stateListener)
        super.onDestroy()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 32)
        }
        val title = TextView(this).apply {
            text = "VPN AI"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        statusView = TextView(this).apply {
            text = "پروفایل بارگذاری نشده"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        }
        val importButton = Button(this).apply {
            text = "وارد کردن پروفایل WireGuard"
            setOnClickListener { openProfilePicker() }
        }
        val connectButton = Button(this).apply {
            text = "اتصال"
            setOnClickListener { requestConnect() }
        }
        val disconnectButton = Button(this).apply {
            text = "قطع اتصال"
            setOnClickListener { WireGuardSessionController.disconnect() }
        }
        root.addView(title, matchWidth())
        root.addView(statusView, matchWidth())
        root.addView(importButton, matchWidth())
        root.addView(connectButton, matchWidth())
        root.addView(disconnectButton, matchWidth())
        setContentView(root)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun openProfilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, REQUEST_PROFILE)
    }

    @Deprecated("Uses the platform activity result contract for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PROFILE && resultCode == RESULT_OK) {
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
                statusView.text = "پروفایل دریافت شد؛ آماده اعتبارسنجی"
            } catch (error: Exception) {
                profileBytes?.fill(0)
                profileBytes = null
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
    }
}
