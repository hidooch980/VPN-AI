package ai.vpnai.client

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/** Sole application entry point for WireGuard lifecycle operations. */
internal object WireGuardSessionController {
    private val lifecycle = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()

    @Volatile private var backend: GoBackend? = null
    @Volatile var currentStatus: String = "آماده"
        private set

    private val tunnel = object : Tunnel {
        override fun getName(): String = "VPN-AI"
        override fun onStateChange(newState: Tunnel.State) {
            publish(when (newState) {
                Tunnel.State.UP -> "تونل بالا است؛ آزمون عبور واقعی داده هنوز لازم است"
                Tunnel.State.DOWN -> "اتصال قطع است"
                Tunnel.State.TOGGLE -> "در حال تغییر وضعیت"
            })
        }
    }

    fun addListener(listener: (String) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (String) -> Unit) { listeners.remove(listener) }

    fun connect(context: Context, profile: ByteArray) {
        lifecycle.execute {
            try {
                ProfileInputPolicy.validateSize(profile.size)
                val parsed = Config.parse(ByteArrayInputStream(profile))
                val engine = backend ?: GoBackend(context.applicationContext).also { backend = it }
                publish("پروفایل معتبر است؛ راه‌اندازی موتور WireGuard…")
                val state = engine.setState(tunnel, Tunnel.State.UP, parsed)
                publish(if (state == Tunnel.State.UP) {
                    "تونل WireGuard بالا است؛ بررسی data-plane لازم است"
                } else "موتور اتصال را فعال نکرد")
            } catch (error: Exception) {
                publish("اتصال ناموفق: ${error.javaClass.simpleName}")
            } finally {
                profile.fill(0)
            }
        }
    }

    fun disconnect() {
        lifecycle.execute {
            try {
                val engine = backend
                if (engine == null) publish("اتصالی فعال نیست")
                else {
                    engine.setState(tunnel, Tunnel.State.DOWN, null)
                    publish("اتصال قطع شد")
                }
            } catch (error: Exception) {
                publish("خطا هنگام قطع اتصال: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun publish(status: String) {
        currentStatus = status
        listeners.forEach { listener -> listener(status) }
    }
}
