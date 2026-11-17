package ai.vpnai.client

internal object ProfileInputPolicy {
    const val MAX_BYTES = 64 * 1024
    fun validateSize(size: Int) {
        require(size in 1..MAX_BYTES) { "Invalid WireGuard profile size" }
    }
}
