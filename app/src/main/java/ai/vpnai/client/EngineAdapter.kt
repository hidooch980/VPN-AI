package ai.vpnai.client

/**
 * Contract for independently integrated VPN engines.
 *
 * Implementations must document descriptor ownership and must not create a
 * second Android VpnService/TUN owner. Merely implementing this interface does
 * not make an engine selectable or production-ready.
 */
internal interface EngineAdapter {
    val id: String
    val displayName: String
    val version: String
    val supportedAbis: Set<String>

    fun validateProfile(profile: ByteArray): EngineValidation
    fun start(request: EngineStartRequest): EngineStartResult
    fun health(): EngineHealth
    fun telemetry(): EngineTelemetry
    fun stop()
    fun abort()
}

internal data class EngineStartRequest(
    val sessionId: String,
    val profile: ByteArray
)

internal sealed class EngineValidation {
    data object Valid : EngineValidation()
    data class Rejected(val reasonCode: String) : EngineValidation()
}

internal sealed class EngineStartResult {
    data class Started(val backendSessionId: String) : EngineStartResult()
    data class Failed(val reasonCode: String, val retryable: Boolean) : EngineStartResult()
}

internal enum class EngineHealthState { UNKNOWN, HEALTHY, DEGRADED, UNHEALTHY }

internal data class EngineHealth(
    val state: EngineHealthState,
    val observedAtEpochMillis: Long?,
    val reasonCode: String?
)

internal data class EngineTelemetry(
    val receivedBytes: Long?,
    val transmittedBytes: Long?,
    val receiveBytesPerSecond: Long?,
    val transmitBytesPerSecond: Long?,
    val roundTripMillis: Long?,
    val sampleTimeEpochMillis: Long?
) {
    init {
        require(receivedBytes == null || receivedBytes >= 0)
        require(transmittedBytes == null || transmittedBytes >= 0)
        require(receiveBytesPerSecond == null || receiveBytesPerSecond >= 0)
        require(transmitBytesPerSecond == null || transmitBytesPerSecond >= 0)
        require(roundTripMillis == null || roundTripMillis >= 0)
    }
}
