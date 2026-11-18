package ai.vpnai.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineCatalogTest {
    @Test
    fun containsExactlyFiveAdditionalCandidates() {
        assertEquals(
            setOf("sing-box", "xray-core", "amneziawg", "psiphon", "outline"),
            EngineCatalog.additionalEngines.map { it.id }.toSet()
        )
        assertEquals(5, EngineCatalog.additionalEngines.size)
    }

    @Test
    fun researchCandidatesAreNeverSelectable() {
        assertTrue(EngineCatalog.additionalEngines.all { !it.selectable })
        assertTrue(EngineCatalog.selectableEngines().isEmpty())
    }

    @Test
    fun eachCandidateHasUpstreamAndLicenseMetadata() {
        EngineCatalog.additionalEngines.forEach {
            assertTrue(it.upstream.startsWith("https://"))
            assertTrue(it.licenseSpdx.isNotBlank())
            assertNotNull(it.notes)
        }
    }

    @Test
    fun proxyOnlyCoresAreNotMisrepresentedAsFullDeviceVpn() {
        assertFalse(EngineCatalog.find("xray-core")!!.supportsWholeDeviceTunnel)
        assertFalse(EngineCatalog.find("psiphon")!!.supportsWholeDeviceTunnel)
        assertFalse(EngineCatalog.find("outline")!!.supportsWholeDeviceTunnel)
        assertTrue(EngineCatalog.find("amneziawg")!!.supportsWholeDeviceTunnel)
        assertTrue(EngineCatalog.find("sing-box")!!.supportsWholeDeviceTunnel)
    }
}
