package ai.vpnai.client

/** Maps the release tag to the same monotonically increasing versionCode used by Gradle. */
internal object ReleaseVersionPolicy {
    private val releaseTag = Regex("^v?\\d+\\.\\d+\\.(\\d+)$")

    fun versionCode(tag: String): Long? =
        releaseTag.matchEntire(tag.trim())?.groupValues?.get(1)?.toLongOrNull()
}
