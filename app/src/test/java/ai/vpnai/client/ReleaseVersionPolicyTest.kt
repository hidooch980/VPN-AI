package ai.vpnai.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseVersionPolicyTest {
    @Test fun parsesVersionedReleaseTag() {
        assertEquals(40L, ReleaseVersionPolicy.versionCode("v1.0.40"))
        assertEquals(41L, ReleaseVersionPolicy.versionCode("1.0.41"))
    }

    @Test fun rejectsBranchNamesAndMalformedTags() {
        assertNull(ReleaseVersionPolicy.versionCode("main"))
        assertNull(ReleaseVersionPolicy.versionCode("v1.0"))
        assertNull(ReleaseVersionPolicy.versionCode("v1.0.beta"))
    }

    @Test fun rejectsOverflowingVersionCode() {
        assertNull(ReleaseVersionPolicy.versionCode("v1.0.999999999999999999999"))
    }
}
