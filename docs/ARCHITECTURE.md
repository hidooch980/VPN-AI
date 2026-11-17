# VPN AI — Architecture

**Status:** Initial implementation baseline  
**Platform:** Android  
**Release target:** `v1.0.0 Stable`

## 1. Non-negotiable invariants

1. One authoritative VPN backend owns the active Android TUN interface and descriptor for each session.
2. All lifecycle operations are serialized; a new session cannot race cleanup from an older session.
3. The UI never infers connectivity from a running process or successful configuration parse.
4. AI may rank or recommend; deterministic safety gates decide whether an action is permitted.
5. Endpoint measurements carry source, timestamp, sample count, and validity.
6. Invalid profiles, untrusted updates, and integrity failures fail closed.
7. Platform-specific APIs stay behind Android adapters.

## 2. Runtime layers

### Android application
- Onboarding, profile import, settings, connection controls, diagnostics, and localization.
- No direct ownership of native tunnel threads or TUN descriptors.

### VPN backend and TUN owner
- A single selected backend owns VPN establishment, descriptor transfer, packet forwarding, and teardown.
- The first engine is WireGuard using the official Android embeddable tunnel library.
- Do not add a second competing `VpnService` implementation while this backend owns the TUN.

### Session supervisor
- Serializes connect, reconnect, failover, and disconnect.
- Models Idle, Preparing, Connecting, Online, Degraded, Stopping, and Failed.
- Stops forwarding before backend shutdown and releases resources in a deterministic order.
- Uses bounded timeouts and explicit error states.

### Engine adapter
Every engine integration must expose identity/version, supported ABIs, profile validation, start, health, stop/abort, structured errors, and telemetry. An engine is selectable only after license, security, compatibility, lifecycle, and physical-device gates pass.

### Network intelligence
- Uses Android network callbacks and measured latency, loss, throughput, handshake, and data-plane outcomes.
- Keeps endpoint inventory separate from observed health.
- Applies freshness, minimum sample counts, confidence, hysteresis, and cooldowns.
- Never invents server locations or measurements.

### DNS and IP protection
- Validates DNS behavior and IPv4/IPv6 route coverage independently.
- Tests connect, reconnect, network handoff, disconnect, and failure conditions.
- Distinguishes app-level controls from Android Always-on VPN and lockdown settings.
- Does not claim leak protection from configuration alone.

### Security and update manager
- Treats imported profiles as untrusted; limits size and validates syntax before activation.
- Redacts private keys, tokens, and profile contents from logs.
- Verifies signed manifests, artifact digests, compatibility, and anti-rollback policy.
- Stages updates atomically and retains a known-good rollback path.

## 3. WireGuard integration boundary

The first engine uses the official `com.wireguard.android:tunnel` library and its userspace backend. The library-provided `GoBackend.VpnService` is the sole TUN owner for this engine. The application must request Android VPN consent before bringing a tunnel up.

A WireGuard profile must be supplied by the user or an authorized service. No server, peer key, endpoint, DNS resolver, or location is bundled or fabricated. Private profile material must not be logged or sent to analytics.

## 4. Session state model

```text
Idle -> Preparing -> Connecting -> Online <-> Degraded
                         |             |
                         v             v
                       Failed       Stopping
                         |             |
                         +----------> Idle
```

Disconnect is always permitted. Failover cannot start a replacement tunnel until the prior backend has safely stopped or a documented recovery path has taken ownership.

## 5. MTU and diagnostics

- Use Android-supported measurement methods.
- Distinguish unsupported/blocked probes from measured values.
- Never report a fallback as a measurement.
- Account for protocol and address-family overhead.
- Record method, network context, timestamp, and confidence.

## 6. Delivery gates

1. Android foundation and CI.
2. Single-owner lifecycle and deterministic tests.
3. First WireGuard engine and real data-plane validation.
4. DNS, IPv4/IPv6, Kill Switch evaluation, and recovery tests.
5. Measured network intelligence and explainable selection.
6. Bounded failover and reconnect.
7. Additional engines, one at a time.
8. Learning with deterministic fallback.
9. Signed updates and rollback.
10. Physical-device field validation and signed stable release.

## 7. Definition of done

Compilation alone is insufficient. Each feature needs documented behavior, deterministic tests, Android instrumentation where applicable, physical-device evidence for VPN behavior, and security/privacy review where secrets, routes, DNS, or updates are involved.
