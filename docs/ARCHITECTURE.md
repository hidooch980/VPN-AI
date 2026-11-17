# VPN AI — Initial Architecture

**Status:** Proposed baseline; subject to product-scope confirmation.  
**Platform assumption:** Android-first, because the reference project is an Android VPN client. Other platforms are not committed.

## 1. Architectural rules

1. **Single TUN owner:** exactly one component owns the active Android `VpnService` interface and its file descriptor for a session.
2. **Explicit engine lifecycle:** every adapter exposes start, health, stop, and capability reporting. Start/stop must be idempotent or guarded by a session state machine.
3. **No false readiness:** a successful configuration parse or transport handshake is not proof that application traffic passes.
4. **Fail closed where security matters:** invalid profiles, unverifiable updates, and integrity failures cannot be silently accepted.
5. **Measured network intelligence:** latency, MTU, packet loss, throughput, and server metadata must include source, timestamp, and validity.
6. **Platform separation:** Android-specific VPN APIs remain in the Android layer; shared session logic must be testable without Android framework dependencies.
7. **No premature multi-engine orchestration:** first prove one engine end-to-end, then add other engines behind the same contract.

## 2. Proposed layers

### Android application layer
- UI, onboarding, settings, profile import, connection controls.
- Localization and accessibility.
- No direct ownership of native engine threads or raw TUN descriptor lifecycle.

### VPN service and TUN layer
- Owns `VpnService.Builder`, `establish()`, descriptor transfer, and revocation.
- Enforces one active TUN owner.
- Applies routes, allowed/disallowed applications, and DNS configuration from a validated session plan.
- Coordinates lockdown expectations with Android's Always-on VPN settings; the app must not claim it can enable OS lockdown by itself.

### Session supervisor
- Serializes connect, reconnect, pause, resume, and disconnect.
- Uses explicit states such as Idle, Preparing, Connecting, Online, Degraded, Stopping, and Failed.
- Prevents a new session from racing old-session cleanup.
- Stops packet forwarding before stopping the backend, then releases descriptors and restores platform state.
- Applies bounded timeouts and a cleanup/reaper strategy for stuck native workers.

### Engine adapter contract
Each engine adapter should provide:
- Stable engine identifier and version.
- Supported platform/ABI and capability declaration.
- Configuration validation.
- Start result with concrete endpoint/session metadata.
- Health check that distinguishes transport reachability from data-plane success.
- Stop/abort semantics and resource ownership.
- Structured error codes and telemetry.

An engine is selectable only when its adapter, native runtime, license, configuration validation, and device test matrix are complete.

### Network intelligence
- Connectivity changes and active-network identity.
- MTU discovery with platform-aware probes and conservative fallback.
- Latency and packet-loss measurements with timestamps and sample counts.
- Server selection based on real measurements, not hard-coded or fabricated values.
- Hysteresis/cooldowns to prevent rapid route or server oscillation.

### DNS and leak protection
- Explicit DNS policy and resolver ownership.
- IPv4 and IPv6 route coverage checked independently.
- DNS behavior tested during connect, reconnect, network handoff, and disconnect.
- Detect unsupported per-app/system-wide claims and disclose limitations.
- Do not equate configuring a DNS address with proving leak protection.

### Security and update manager
- Parse profiles as untrusted input with strict size, scheme, host, and field validation.
- Keep credentials and private keys out of logs and analytics.
- Signed update manifest with pinned verification key, artifact digest, version/rollback policy, ABI and OS compatibility checks.
- Stage updates atomically and retain a known-good rollback path.
- Subscription refresh and application/engine binary updates are separate workflows.

### Diagnostics and telemetry
- Structured state transitions and error codes.
- Counters for packets/bytes, reconnects, uptime, and last successful data-plane probe.
- Avoid reporting Online solely because a process or socket exists.
- Redact secrets, profile contents, and identifying data.

## 3. Session state machine

```text
Idle
  -> Preparing
  -> Connecting
  -> Online <-> Degraded
  -> Stopping
  -> Idle

Preparing/Connecting -> Failed -> Stopping -> Idle
Online/Degraded -> Stopping -> Idle
```

All transitions are serialized. Disconnect is always permitted. A reconnect cannot begin until prior cleanup has completed or a bounded recovery path has explicitly taken ownership of remaining resources.

## 4. MTU discovery requirements

The reference scanner is useful as a design prompt, not as a drop-in implementation. The VPN AI implementation must:
- Detect whether the platform exposes a supported probe mechanism.
- Avoid depending on Linux-only ping flags on Android.
- Use multiple probe sizes and bounded timeouts.
- Distinguish “probe unsupported/blocked” from “path MTU measured”.
- Never manufacture a successful minimum MTU when all probes fail.
- Account for actual encapsulation overhead per selected transport and address family.
- Apply conservative defaults only when explicitly labelled as defaults, not measurements.
- Record the measurement method, timestamp, network identity, and confidence.

## 5. Delivery sequence

### Phase A — requirements and threat model
Confirm Android versions, ABI targets, protocols, server/profile sources, privacy model, update distribution, and release signing ownership.

### Phase B — foundation
Create app shell, service boundary, session state machine, engine contract, structured telemetry, and automated tests.

### Phase C — first engine
Choose one protocol based on requirements and licensing. Complete connect/data-plane/disconnect and failure recovery on physical Android devices.

### Phase D — protection and intelligence
Add DNS/IPv4/IPv6 tests, MTU diagnostics, network handoff, health scoring, and server selection using real observations.

### Phase E — additional engines
Add engines one at a time. Each must pass the same lifecycle and security gates.

### Phase F — updates and release
Complete signing-key custody, reproducible build checks, signed update verification, rollback, privacy review, and release test matrix.

## 6. Definition of done

A feature is not complete because it compiles. It must have:
- A documented contract and failure behavior.
- Unit tests for deterministic logic.
- Instrumentation tests where Android framework behavior is involved.
- Device evidence for real VPN data-plane behavior.
- Security and privacy review where secrets, routes, DNS, or updates are involved.
- Telemetry that reports the actual state.
