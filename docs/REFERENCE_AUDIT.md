# MSN-GUARD Reference Audit — Initial Findings

**Reference:** https://github.com/mbm110/MSN-GUARD  
**Branch inspected:** `master`  
**Purpose:** Extract engineering patterns for an independent VPN AI implementation.  
**Important:** This is an initial source review, not a full security audit, build, or runtime test.

## Files inspected

- `app/src/main/java/com/msnguard/vpn/MtuAutoScan.kt`
- `app/src/main/java/com/msnguard/vpn/MtuProbe.kt`
- `app/src/main/java/com/msnguard/vpn/ShardHealth.kt` (partial source review)
- `core/aether/src/prober.rs` (partial source review)
- `core/aether/src/wireguard.rs` (partial source review)
- `core/fcae-ffi/runtime/src/session.rs`

The broader repository also contains Android engine wrappers, DNS handling, and telemetry components that require further file-by-file review.

## Patterns worth adapting

### 1. Session supervisor and ordered teardown
The Rust session supervisor documents and implements an explicit teardown order: stop TUN forwarding, stop backend resources, perform OS cleanup, then shut down the runtime. It also serializes session transitions and avoids starting a new session while old cleanup is still running.

**VPN AI adaptation:** adopt the lifecycle contract and tests, not the source code by default. Make the Android `VpnService` the single authority for TUN creation and descriptor ownership.

### 2. TUN bridge abstraction
The `TunBridge` contract separates session supervision from the concrete packet-forwarding implementation and supports health checks, stop, abort, and pre-authorized file descriptors.

**VPN AI adaptation:** define an equivalent platform boundary. Specify whether each descriptor is borrowed, duplicated, transferred, or owned, and exactly which component closes it.

### 3. Structured telemetry
The reference telemetry design exposes explicit backend/session state and counters rather than requiring the UI to infer status from logs.

**VPN AI adaptation:** use typed state and error codes. Distinguish control-plane handshake, TUN forwarding, and verified data-plane reachability.

### 4. Per-node health memory
`ShardHealth` stores prior latency, success streak, consecutive failures, and throughput observations. It avoids ranking solely by a single latency sample.

**VPN AI adaptation:** use decayed and timestamped observations, minimum sample counts, failure cooldowns, and network-context separation. Do not reuse hard-coded thresholds without representative measurements.

### 5. MTU scan isolation
`MtuAutoScan` scopes scan results to a connection attempt and associates a selected transport with its own MTU result.

**VPN AI adaptation:** keep per-session/per-network measurements isolated and invalidate them on network changes.

## Findings that must not be copied unchanged

### MTU probe limitations
The inspected `MtuProbe.kt` uses a fixed ICMP target, a Linux-style `ping -M do` argument, and a fixed probe timeout. These assumptions are not portable across Android devices, OEM builds, or networks that block ICMP.

More importantly, if no probe succeeds, the code's initial `bestPathMtu` value can still flow into a clamped result. That risks presenting a floor value as if it were measured. VPN AI must represent “unsupported”, “blocked”, and “measured” as separate outcomes and must not fabricate a successful measurement.

### Method overhead assumptions
The reference has a fixed overhead table for its own transport stack. Those values are not universal. VPN AI must calculate or validate overhead for each actual protocol, encapsulation, address family, and transport mode.

### Probe endpoint and server lists
Reference IP ranges, gateways, and probe endpoints are specific to its own service and deployment. They must not be copied as VPN AI server inventory or presented as verified VPN AI infrastructure.

### DNS and OS-specific behavior
Linux resolver/route management code cannot be assumed to implement Android DNS protection. Android VPN DNS, routes, per-app exclusions, and lockdown behavior need separate implementation and physical-device verification.

## Licensing boundary

The reference repository declares AGPL-3.0. This project begins with architectural learning and independent implementation. Before copying, modifying, or incorporating any source file or derived code, record:
- the exact source file and commit,
- the applicable license and notices,
- whether the component is linked, modified, or distributed,
- source-disclosure and corresponding-source obligations,
- third-party dependency licenses.

Do not copy source code into this repository until the compliance decision is documented.

## Verification status

- Source review: partial, completed for the files listed above.
- Full repository audit: not completed.
- Build or test execution: not run.
- Android physical-device test: not run.
- WireGuard handshake/data-plane test: not run.
- DNS/IPv4/IPv6 leak test: not run.
- Release readiness: not established.
