package ai.vpnai.client

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import android.os.SystemClock
import com.wireguard.android.backend.Statistics
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/** Sole application entry point for WireGuard lifecycle operations. */
internal object WireGuardSessionController {
    private val lifecycle = Executors.newSingleThreadExecutor()
    private const val DATA_PLANE_POLL_MS = 500L
    private const val DATA_PLANE_VERIFY_TIMEOUT_MS = 10_000L
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
            var backendStartAttempted = false
            try {
                if (stateMachine.state == ConnectionState.FAILED) {
                    // Never reset the logical session until the backend confirms the old
                    // tunnel is down; otherwise a failed cleanup could overlap a new session.
                    ensureBackendDown()
                    transition(ConnectionEvent.RESET)
                }
                if (stateMachine.state != ConnectionState.IDLE) {
                    publish("A VPN session is already active or transitioning")
                    return@execute
                }
                transition(ConnectionEvent.PREPARE)
                publish("در حال اعتبارسنجی پروفایل…")
                ProfileInputPolicy.validateSize(profile.size)
                val parsed = Config.parse(ByteArrayInputStream(profile))
                val engine = backend ?: GoBackend(context.applicationContext).also { backend = it }
                publish("پروفایل معتبر است؛ راه‌اندازی موتور WireGuard…")
                backendStartAttempted = true
                val state = engine.setState(tunnel, Tunnel.State.UP, parsed)
                if (state == Tunnel.State.UP) {
                    transition(ConnectionEvent.BACKEND_READY)
                    transition(ConnectionEvent.VERIFY_DATA_PLANE)
                    verifyWireGuardTraffic(engine, context)
                } else {
                    val teardown = runCatching { ensureBackendDown() }
                    transition(ConnectionEvent.FAILURE)
                    publish(
                        if (teardown.isSuccess) "موتور اتصال را فعال نکرد؛ خاموش بودن تونل تأیید شد"
                        else "موتور اتصال فعال نشد و خاموش بودن تونل هنوز قابل تأیید نیست"
                    )
                }
            } catch (error: Exception) {
                // setState(UP) can fail after partial native setup. Attempt teardown before
                // exposing FAILED, and keep the next connect blocked until DOWN is confirmed.
                if (backendStartAttempted) {
                    runCatching { ensureBackendDown() }
                }
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
                ensureBackendDown()
                transition(ConnectionEvent.STOPPED)
                publish("اتصال قطع شد")
            } catch (error: Exception) {
                failSession()
                publish("خطا هنگام قطع اتصال: ${error.javaClass.simpleName}")
            }
        }
    }

    /** MSN-GUARD-style verified-connect gate: backend UP is not enough. */
    private fun verifyWireGuardTraffic(engine: GoBackend, context: Context) {
        publish("تونل WireGuard بالا است؛ در حال بررسی عبور واقعی داده…")
        val baseline = try {
            engine.getStatistics(tunnel)
        } catch (error: Exception) {
            val teardown = runCatching { ensureBackendDown() }
            transition(ConnectionEvent.FAILURE)
            publish(
                if (teardown.isSuccess) "آمار اولیه WireGuard در دسترس نیست؛ تونل خاموش شد"
                else "آمار اولیه WireGuard در دسترس نیست و خاموش بودن تونل تأیید نشد"
            )
            return
        }
        val baselineTx = baseline.totalTx()
        val baselineRx = baseline.totalRx()
        val deadline = SystemClock.elapsedRealtime() + DATA_PLANE_VERIFY_TIMEOUT_MS
        var verified = false
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                Thread.sleep(DATA_PLANE_POLL_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                runCatching { ensureBackendDown() }
                transition(ConnectionEvent.FAILURE)
                publish("آزمون عبور داده متوقف شد")
                return
            }
            if (engine.getState(tunnel) != Tunnel.State.UP) break
            val stats: Statistics = try {
                engine.getStatistics(tunnel)
            } catch (_: Exception) {
                continue
            }
            // A controlled HTTPS request is explicitly opened on Android's active
            // VPN Network. Counter growth alone is not accepted as end-to-end proof.
            val probeSucceeded = WireGuardDataPlaneProbe(context).verify()
            if (probeSucceeded) {
                val afterProbe = try {
                    engine.getStatistics(tunnel)
                } catch (_: Exception) {
                    continue
                }
                if (afterProbe.totalTx() > baselineTx &&
                    afterProbe.totalRx() > baselineRx &&
                    stats.totalTx() >= baselineTx &&
                    stats.totalRx() >= baselineRx
                ) {
                    verified = true
                    transition(ConnectionEvent.DATA_PLANE_VERIFIED)
                    publish("اتصال WireGuard تأیید شد؛ آزمون HTTPS داخل تونل موفق بود")
                    break
                }
            }
        }
        if (!verified) {
            val teardown = runCatching { ensureBackendDown() }
            transition(ConnectionEvent.FAILURE)
            publish(
                if (teardown.isSuccess) "WireGuard بالا آمد اما عبور واقعی داده تأیید نشد؛ تونل خاموش شد"
                else "عبور واقعی داده تأیید نشد و خاموش بودن تونل نیز قابل تأیید نیست؛ اتصال مجدد مسدود می‌ماند"
            )
        }
    }

    private fun ensureBackendDown() {
        val engine = backend ?: return
        if (engine.getState(tunnel) == Tunnel.State.UP) {
            engine.setState(tunnel, Tunnel.State.DOWN, null)
        }
        check(engine.getState(tunnel) == Tunnel.State.DOWN) {
            "WireGuard backend did not confirm tunnel teardown"
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
        // A UI observer must never interrupt VPN lifecycle work.
        listeners.forEach { listener -> runCatching { listener(status) } }
    }
}
