package ai.vpnai.client

/**
 * Pure deterministic session state model.
 *
 * This class does not claim that a tunnel is usable. ONLINE is reachable only
 * after the supervisor receives an explicit data-plane verification event.
 */
internal enum class ConnectionState {
    IDLE, PREPARING, CONNECTING, VERIFYING, ONLINE, DEGRADED, STOPPING, FAILED
}

internal enum class ConnectionEvent {
    PREPARE, BACKEND_READY, VERIFY_DATA_PLANE, DATA_PLANE_VERIFIED,
    HEALTH_DEGRADED, HEALTH_RECOVERED, STOP, STOPPED, FAILURE, RESET
}

internal class ConnectionStateMachine(initial: ConnectionState = ConnectionState.IDLE) {
    var state: ConnectionState = initial
        private set

    fun dispatch(event: ConnectionEvent): ConnectionState {
        val next = when (state) {
            ConnectionState.IDLE -> when (event) {
                ConnectionEvent.PREPARE -> ConnectionState.PREPARING
                else -> invalid(event)
            }
            ConnectionState.PREPARING -> when (event) {
                ConnectionEvent.BACKEND_READY -> ConnectionState.CONNECTING
                ConnectionEvent.STOP -> ConnectionState.STOPPING
                ConnectionEvent.FAILURE -> ConnectionState.FAILED
                else -> invalid(event)
            }
            ConnectionState.CONNECTING -> when (event) {
                ConnectionEvent.VERIFY_DATA_PLANE -> ConnectionState.VERIFYING
                ConnectionEvent.STOP -> ConnectionState.STOPPING
                ConnectionEvent.FAILURE -> ConnectionState.FAILED
                else -> invalid(event)
            }
            ConnectionState.VERIFYING -> when (event) {
                ConnectionEvent.DATA_PLANE_VERIFIED -> ConnectionState.ONLINE
                ConnectionEvent.HEALTH_DEGRADED -> ConnectionState.DEGRADED
                ConnectionEvent.STOP -> ConnectionState.STOPPING
                ConnectionEvent.FAILURE -> ConnectionState.FAILED
                else -> invalid(event)
            }
            ConnectionState.ONLINE -> when (event) {
                ConnectionEvent.HEALTH_DEGRADED -> ConnectionState.DEGRADED
                ConnectionEvent.STOP -> ConnectionState.STOPPING
                ConnectionEvent.FAILURE -> ConnectionState.FAILED
                else -> invalid(event)
            }
            ConnectionState.DEGRADED -> when (event) {
                ConnectionEvent.HEALTH_RECOVERED -> ConnectionState.ONLINE
                ConnectionEvent.STOP -> ConnectionState.STOPPING
                ConnectionEvent.FAILURE -> ConnectionState.FAILED
                else -> invalid(event)
            }
            ConnectionState.STOPPING -> when (event) {
                ConnectionEvent.STOPPED -> ConnectionState.IDLE
                ConnectionEvent.FAILURE -> ConnectionState.FAILED
                else -> invalid(event)
            }
            ConnectionState.FAILED -> when (event) {
                ConnectionEvent.RESET -> ConnectionState.IDLE
                ConnectionEvent.STOP -> ConnectionState.STOPPING
                else -> invalid(event)
            }
        }
        state = next
        return next
    }

    private fun invalid(event: ConnectionEvent): Nothing =
        throw IllegalStateException("Event $event is invalid while state is $state")
}
