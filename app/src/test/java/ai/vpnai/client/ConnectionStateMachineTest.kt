package ai.vpnai.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConnectionStateMachineTest {
    @Test fun onlineRequiresExplicitDataPlaneVerification() {
        val machine = ConnectionStateMachine()
        assertEquals(ConnectionState.PREPARING, machine.dispatch(ConnectionEvent.PREPARE))
        assertEquals(ConnectionState.CONNECTING, machine.dispatch(ConnectionEvent.BACKEND_READY))
        assertEquals(ConnectionState.VERIFYING, machine.dispatch(ConnectionEvent.VERIFY_DATA_PLANE))
        assertEquals(ConnectionState.ONLINE, machine.dispatch(ConnectionEvent.DATA_PLANE_VERIFIED))
    }

    @Test fun backendReadyAloneNeverMeansOnline() {
        val machine = ConnectionStateMachine()
        machine.dispatch(ConnectionEvent.PREPARE)
        machine.dispatch(ConnectionEvent.BACKEND_READY)
        assertEquals(ConnectionState.CONNECTING, machine.state)
    }

    @Test fun invalidTransitionsAreRejected() {
        val machine = ConnectionStateMachine()
        assertThrows(IllegalStateException::class.java) {
            machine.dispatch(ConnectionEvent.DATA_PLANE_VERIFIED)
        }
        assertEquals(ConnectionState.IDLE, machine.state)
    }

    @Test fun degradedSessionCanRecoverOrStop() {
        val machine = ConnectionStateMachine()
        machine.dispatch(ConnectionEvent.PREPARE)
        machine.dispatch(ConnectionEvent.BACKEND_READY)
        machine.dispatch(ConnectionEvent.VERIFY_DATA_PLANE)
        machine.dispatch(ConnectionEvent.DATA_PLANE_VERIFIED)
        assertEquals(ConnectionState.DEGRADED, machine.dispatch(ConnectionEvent.HEALTH_DEGRADED))
        assertEquals(ConnectionState.ONLINE, machine.dispatch(ConnectionEvent.HEALTH_RECOVERED))
        assertEquals(ConnectionState.STOPPING, machine.dispatch(ConnectionEvent.STOP))
        assertEquals(ConnectionState.IDLE, machine.dispatch(ConnectionEvent.STOPPED))
    }

    @Test fun failedSessionMustResetBeforeNewAttempt() {
        val machine = ConnectionStateMachine()
        machine.dispatch(ConnectionEvent.PREPARE)
        assertEquals(ConnectionState.FAILED, machine.dispatch(ConnectionEvent.FAILURE))
        assertEquals(ConnectionState.IDLE, machine.dispatch(ConnectionEvent.RESET))
    }
}
