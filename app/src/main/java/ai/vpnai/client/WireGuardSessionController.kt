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
    private val stateMachine = ConnectionStateMachine()

    @Volatile private var backend: GoBackend? = null
    @Volatile var currentStatus: String = "آماده"
        private set
    @Volatile var connectionState: ConnectionState = ConnectionState.IDLE
        private set

    private val tunnel = object : Tunnel {
        override fun getName(): String = "VPN-AI"
        override fun onStateChange(newState: Tunnel.State) {
            // Backend callbacks are observations, not proof of end-to-end data flow.
            publish(when (newState) {
                Tunnel.State.UP -> "تونل راه‌اندازی شده؛ آزمون عبور واقعی داده لازم است"
                Tunnel.State.DOWN -> "تونل خاموش است"
                Tunnel.State.TOGGLE -> "در حال تغییر وضعیت تونل"
            })
        }
    }

    fun addListener(listener: (String) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (String) -> Unit) { listeners.remove(listener) }

    fun connect(context: Context, profile: ByteArray) {
        lifecycle.execute {
            try {
                if (stateMachine.state == ConnectionState.FAILED) {
                    transition(ConnectionEvent.RESET)
                }
                check(stateMachine.state == ConnectionState.IDLE) {
                    "A VPN session is already active or transitioning"
                }
                transition(ConnectionEvent.PREPARE)
                publish("در حال اعتبارسنجی پروفایل…")
                ProfileInputPolicy.validateSize(profile.size)
                val parsed = Config.parse(ByteArrayInputStream(profile))
                val engine = backend ?: GoBackend(context.applicationContext).also { backend = it }
                publish("پروفایل معتبر است؛ راه‌اندازی موتور WireGuard…")
                val state = engine.setState(tunnel, Tunnel.State.UP, parsed)
                if (state == Tunnel.State.UP) {
                    transition(ConnectionEvent.BACKEND_READY)
                    transition(ConnectionEvent.VERIFY_DATA_PLANE)
                    publish("تونل WireGuard بالا است؛ تا تأیید عبور واقعی داده آنلاین محسوب نمی‌شود")
                } else {
                    transition(ConnectionEvent.FAILURE)
                    publish("موتور اتصال را فعال نکرد")
                }
            } catch (error: Exception) {
                failSession()
                publish("اتصال ناموفق: ${error.javaClass.simpleName}")
            } finally {
                profile.fill(0)
            }
        }
    }

    fun disconnect() {
        lifecycle.execute {
            try {
                if (stateMachine.state == ConnectionState.IDLE) {
                    publish("اتصالی فعال نیست")
                    return@execute
                }
                if (stateMachine.state != ConnectionState.STOPPING) {
                    transition(ConnectionEvent.STOP)
                }
                backend?.setState(tunnel, Tunnel.State.DOWN, null)
                transition(ConnectionEvent.STOPPED)
                publish("اتصال قطع شد")
            } catch (error: Exception) {
                failSession()
                publish("خطا هنگام قطع اتصال: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun transition(event: ConnectionEvent) {
        connectionState = stateMachine.dispatch(event)
    }

    private fun failSession() {
        when (stateMachine.state) {
            ConnectionState.PREPARING,
            ConnectionState.CONNECTING,
            ConnectionState.VERIFYING,
            ConnectionState.ONLINE,
            ConnectionState.DEGRADED,
            ConnectionState.STOPPING -> runCatching { transition(ConnectionEvent.FAILURE) }
            else -> Unit
        }
    }

    private fun publish(status: String) {
        currentStatus = status
        connectionState = stateMachine.state
        listeners.forEach { listener -> listener(status) }
    }
}
