package ai.vpnai.client

import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileInputPolicyTest {
    @Test fun acceptsValidSize() {
        ProfileInputPolicy.validateSize(1)
        ProfileInputPolicy.validateSize(ProfileInputPolicy.MAX_BYTES)
    }
    @Test fun rejectsEmptyProfile() {
        assertThrows(IllegalArgumentException::class.java) { ProfileInputPolicy.validateSize(0) }
    }
    @Test fun rejectsOversizedProfile() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileInputPolicy.validateSize(ProfileInputPolicy.MAX_BYTES + 1)
        }
    }
}
