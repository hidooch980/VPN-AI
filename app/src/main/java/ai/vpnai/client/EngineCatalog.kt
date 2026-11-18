package ai.vpnai.client

/**
 * Evidence-based inventory for protocol engines.
 *
 * A catalog entry is not an engine implementation. Selection is denied until a
 * concrete adapter, lifecycle integration, security review, and device evidence
 * have all been recorded.
 */
internal enum class EngineImplementationStage {
    RESEARCHED,
    BUILD_CONFIGURED,
    ADAPTER_IMPLEMENTED,
    INTEGRATION_TESTED,
    DEVICE_VERIFIED,
    BLOCKED
}

internal data class EngineDescriptor(
    val id: String,
    val displayName: String,
    val upstream: String,
    val licenseSpdx: String,
    val stage: EngineImplementationStage,
    val supportsWholeDeviceTunnel: Boolean,
    val notes: String
) {
    val selectable: Boolean
        get() = stage == EngineImplementationStage.DEVICE_VERIFIED
}

/**
 * The five requested engines are tracked independently. They intentionally
 * remain non-selectable until real Android adapters and physical-device tests
 * exist. Outline MobileProxy is not treated as a whole-device VPN by itself.
 */
internal object EngineCatalog {
    val additionalEngines: List<EngineDescriptor> = listOf(
        EngineDescriptor(
            id = "sing-box",
            displayName = "sing-box",
            upstream = "https://github.com/singbox-android/libbox",
            licenseSpdx = "GPL-3.0",
            stage = EngineImplementationStage.RESEARCHED,
            supportsWholeDeviceTunnel = true,
            notes = "Android libbox binding; pin and audit the exact release before integration."
        ),
        EngineDescriptor(
            id = "xray-core",
            displayName = "Xray-core",
            upstream = "https://github.com/XTLS/Xray-core",
            licenseSpdx = "MPL-2.0",
            stage = EngineImplementationStage.RESEARCHED,
            supportsWholeDeviceTunnel = false,
            notes = "Core alone does not own Android VpnService/TUN; requires a reviewed TUN-to-proxy data plane."
        ),
        EngineDescriptor(
            id = "amneziawg",
            displayName = "AmneziaWG",
            upstream = "https://github.com/amnezia-vpn/amneziawg-android",
            licenseSpdx = "Apache-2.0",
            stage = EngineImplementationStage.RESEARCHED,
            supportsWholeDeviceTunnel = true,
            notes = "Separate WireGuard-derived protocol and configuration; do not run a competing TUN owner."
        ),
        EngineDescriptor(
            id = "psiphon",
            displayName = "Psiphon",
            upstream = "https://github.com/Psiphon-Labs/psiphon-tunnel-core",
            licenseSpdx = "GPL-3.0",
            stage = EngineImplementationStage.RESEARCHED,
            supportsWholeDeviceTunnel = false,
            notes = "Android library exposes a local proxy; full-device routing needs a separately verified TUN bridge."
        ),
        EngineDescriptor(
            id = "outline",
            displayName = "Outline SDK",
            upstream = "https://github.com/OutlineFoundation/outline-sdk",
            licenseSpdx = "Apache-2.0",
            stage = EngineImplementationStage.RESEARCHED,
            supportsWholeDeviceTunnel = false,
            notes = "MobileProxy is a local proxy integration, not a complete Android VpnService implementation by itself."
        )
    )

    fun find(id: String): EngineDescriptor? =
        additionalEngines.firstOrNull { it.id == id }

    fun selectableEngines(): List<EngineDescriptor> =
        additionalEngines.filter { it.selectable }
}
