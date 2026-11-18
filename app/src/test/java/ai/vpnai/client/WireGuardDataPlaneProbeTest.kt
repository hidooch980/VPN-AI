package ai.vpnai.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardDataPlaneProbeTest {
    @Test fun onlyNoContentConnectivityResponseIsAccepted() {
        assertTrue(WireGuardDataPlaneProbe.acceptsStatus(204))
    }

    @Test fun ordinaryHttpSuccessIsNotEnough() {
        assertFalse(WireGuardDataPlaneProbe.acceptsStatus(200))
        assertFalse(WireGuardDataPlaneProbe.acceptsStatus(201))
    }

    @Test fun redirectsAndServerErrorsAreRejected() {
        assertFalse(WireGuardDataPlaneProbe.acceptsStatus(301))
        assertFalse(WireGuardDataPlaneProbe.acceptsStatus(302))
        assertFalse(WireGuardDataPlaneProbe.acceptsStatus(500))
    }
}
