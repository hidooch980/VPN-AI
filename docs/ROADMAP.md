# VPN AI — Master Roadmap

**Document status:** Canonical project roadmap  
**Project:** VPN AI  
**Repository:** https://github.com/hidooch980/VPN-AI  
**Target release:** `v1.0.0 Stable` (signed; no public beta/test APK)  
**Product direction:** Android-first, AI-first, automation-first, security-first  
**Last updated:** 2026-11-17

> This document is the single source of truth for project scope, engineering sequence, acceptance gates, and release criteria. A listed feature is a requirement or plan—not evidence that it has already been implemented or tested.

## 1. Product mission and boundaries

Build an independent, reliable VPN application that automatically evaluates network conditions, selects an eligible connection path, maintains a secure tunnel, recovers from failures, and learns from verified outcomes.

- The implementation is independently developed; all third-party components require explicit license, security, compatibility, and maintenance review before integration.
- This project is separate from MolidoVPN AI. Do not import its code, modules, branding, keys, or architecture as project dependencies.
- Android is the initial target. Other platforms require a separately approved scope.
- Do not claim that a country, server, protocol, ping, speed, uptime, bypass, or availability works unless supported by current, reproducible measurements.
- Do not mark an engine production-ready merely because its repository exists, its code compiles, or its configuration parses.
- The target is one signed stable release, not a public beta series.

## 2. Product principles

1. **AI-first, automation-first:** automate diagnosis, ranking, selection, recovery, and maintenance where safe and measurable.
2. **Security before convenience:** invalid, unverifiable, or unsafe states fail closed.
3. **One tunnel owner:** a single authoritative owner controls Android `VpnService`, the active TUN descriptor, and packet forwarding for each session.
4. **Evidence before claims:** every health, latency, throughput, loss, server, and availability result carries provenance, timestamp, sample count, and validity.
5. **Deterministic safety core:** AI may recommend or optimize; it must never bypass lifecycle, security, integrity, or policy gates.
6. **Modular engines:** each protocol is isolated behind a common adapter contract and can be tested, enabled, disabled, or rolled back independently.
7. **Privacy by design:** minimize collection, redact secrets, explain telemetry, and keep credentials out of logs.
8. **Recoverability:** every connection, engine, and update operation has bounded timeouts, cleanup, health checks, and a recovery path.
9. **No false readiness:** distinguish configured, built, unit-tested, device-tested, field-tested, and production-approved states.
10. **Battery and resource awareness:** avoid unnecessary scans, retries, engine churn, and background work.

## 3. Target capabilities

### 3.1 Central AI Brain and network intelligence

The central decision service should:
- Observe network type and changes (Wi-Fi/mobile, active network identity, connectivity transitions) using Android-supported APIs.
- Collect valid measurements such as latency, jitter, packet loss, throughput, handshake time, data-plane success, and reconnect duration.
- Identify likely failure classes: DNS resolution failure, route/address-family issue, transport timeout, endpoint reachability failure, handshake failure, data-plane failure, MTU/fragmentation issue, or suspected filtering.
- Detect patterns associated with DPI or protocol interference only when supported by repeatable observations; label inference and confidence explicitly.
- Estimate disconnection/degradation risk and choose a conservative response.
- Rank eligible engines, protocols, and endpoints using measured quality, compatibility, security policy, resource cost, and confidence.
- Learn from successful and failed attempts without treating one observation as universal truth.
- Explain decisions with concise reason codes and evidence.
- Degrade safely to deterministic rules when AI/model services are unavailable, stale, uncertain, or corrupted.

AI is not allowed to invent a server, country, endpoint, ping, speed, availability, or success percentage. Low-confidence recommendations must not override safety policy.

### 3.2 Smart automatic connection

- Scan only approved, configured, or otherwise verifiable connection candidates.
- Validate profile syntax and policy before attempting a connection.
- Choose a compatible protocol/engine based on actual network evidence and device capability.
- Prefer stable connections over marginally faster but unreliable paths.
- Detect degradation and distinguish transient loss from persistent failure.
- Perform bounded retries with exponential backoff and jitter.
- Switch endpoint or engine only after the current session has been safely stopped or isolated according to the single-TUN lifecycle.
- Reconnect after network handoff or internet restoration when policy allows.
- Prevent rapid switching with hysteresis, cooldowns, and minimum evidence thresholds.
- Respect user preferences, metered-network policy, battery state, and Android background-execution limits.
- Provide manual connect/disconnect and emergency stop controls.

### 3.3 Server and endpoint intelligence

- Keep endpoint inventory separate from measured health.
- Store only verified endpoint metadata and its source.
- Measure latency, jitter, packet loss, throughput, handshake and data-plane success independently.
- Track timestamp, network context, sample count, confidence, and measurement method.
- Mark stale measurements and avoid using them as current facts.
- Detect unavailable, blocked, unstable, or incompatible endpoints from evidence; do not infer a country from an IP without a trusted source.
- Apply failure cooldowns and controlled re-probing.
- Restore an endpoint to candidate status only after successful revalidation.
- Rank dynamically with confidence and minimum-sample rules.
- Use load balancing only if the selected backend and infrastructure explicitly support it; do not imply that a client can create server-side capacity.

### 3.4 Security intelligence and traffic protection

- Validate imported profiles and subscription content as untrusted input.
- Prevent credential, private-key, token, and profile leakage into logs, crash reports, or analytics.
- Enforce strict endpoint, scheme, field, size, and parser limits.
- Detect active-network changes and coordinate safe tunnel transitions.
- Verify DNS behavior and IPv4/IPv6 route coverage independently.
- Implement and test Kill Switch behavior within Android's actual platform capabilities.
- Clearly distinguish app-level protection from OS-level Always-on VPN / lockdown features; never claim the app can silently enable OS lockdown.
- Preserve protection during reconnect and failover to the extent Android and the selected engine support it.
- Reject tampered binaries, invalid manifests, incompatible updates, and rollback attempts outside the approved policy.
- Provide safe failure behavior when an engine crashes, a descriptor becomes invalid, or the control plane fails.

### 3.5 Automated updates

Support a secure update workflow for the application and separately versioned engine components:

1. Retrieve a signed update manifest from an authenticated, approved source.
2. Verify manifest signature using a pinned public verification key.
3. Validate version, channel, minimum supported app version, Android version, CPU ABI, engine compatibility, expiry, and anti-rollback policy.
4. Download through a bounded, resumable process.
5. Verify the artifact digest (SHA-256) and signature before staging.
6. Stage atomically; never overwrite the active known-good component in place.
7. Run compatibility and health checks before activation.
8. Retain a known-good version and support rollback after failed health checks.
9. Display version, release notes, component changes, and verification status.
10. Keep application updates and engine/data updates as separate signed artifacts and policies.

**Android limitation:** on ordinary consumer Android devices, an app generally cannot silently install or replace its own APK. The app may check, download, verify, and initiate the platform-approved installation flow, but user confirmation is normally required. Silent installation requires an appropriate managed-device/system privilege and is not assumed.

### 3.6 Learning and continuous improvement

- Record a minimal structured outcome for each connection attempt.
- Separate transport reachability from actual data-plane success.
- Learn per network context, operator/provider where reliably known, endpoint, engine, protocol, and time window.
- Use minimum sample sizes, confidence intervals or calibrated confidence, freshness windows, and drift detection.
- Prevent one-off successes/failures from dominating ranking.
- Detect repeated failure signatures and avoid retry loops.
- Compare predictions with actual outcomes and track model quality.
- Version models and decision policies; validate before promotion.
- Support rollback to a known-good model/policy.
- Keep a deterministic fallback policy.
- Minimize or aggregate telemetry; provide user-visible privacy controls and retention rules.

### 3.7 Reliability, lifecycle, and resource control

- One serialized session supervisor controls connect, reconnect, pause/resume, failover, and disconnect.
- Exactly one authoritative TUN owner per active session.
- Engine adapters must expose start, health, stop/abort, capability, structured errors, and resource ownership.
- Stop forwarding before backend shutdown; close descriptors and cancel workers in a deterministic order.
- Use bounded timeouts, watchdogs, stuck-worker detection, and cleanup/reaper strategy.
- Prevent old-session cleanup from racing a new session.
- Handle process death, service revocation, network handoff, device sleep, and OS restrictions.
- Monitor CPU, memory, battery, wakeups, and traffic overhead.
- Avoid background scanning and retry storms.

## 4. Engine and protocol evaluation policy

Potential candidates are research inputs, not approved integrations. Evaluate one at a time and record a decision:

- sing-box
- Xray-core
- AmneziaWG
- Psiphon Tunnel Core
- Outline SDK
- Hysteria2 / TUIC implementations where suitable
- Shadowsocks implementations
- Tor transports such as Snowflake or WebTunnel as separately assessed fallback/research paths
- Hiddify and v2rayNG as Android architecture and UX references, not presumed embeddable engines
- Bepass, Azad, Geneva, ByeDPI, zapret, and similar DPI-related projects as experimental/research candidates only until their platform fit, safety, license, and maintenance are established

For every candidate, complete:
1. Canonical repository and maintainer verification.
2. License and notice obligations review.
3. Security history, maintenance activity, dependency and supply-chain review.
4. Android API, ABI, NDK, Kotlin/Java integration, and build reproducibility review.
5. VpnService/TUN integration and descriptor ownership analysis.
6. Configuration format, secrets handling, and parser safety review.
7. Performance, battery, memory, and stability measurement.
8. Unit, integration, instrumentation, and physical-device testing.
9. Iranian mobile/fixed-network field evidence where available and lawful.
10. Written accept/reject/defer decision with rationale.

Do not integrate all candidates. Do not bundle Windows-only utilities into an Android app. Research tools and proxy components are not automatically complete VPN engines. Any code reuse must satisfy the source license and attribution obligations.

## 5. Delivery phases and exit gates

### Phase 0 — Product baseline and governance
- Confirm Android minimum/target SDK, supported ABIs, distribution route, privacy policy, telemetry policy, and user-facing scope.
- Define threat model, trust boundaries, update source, endpoint source, and signing-key custody.
- Establish contribution, dependency, license, vulnerability, and release policies.

**Exit gate:** approved requirements and threat model; unresolved product decisions explicitly listed.

### Phase 1 — Independent Android foundation
- Establish app structure, build system, CI, lint, static analysis, dependency checks, and test framework.
- Implement VPN service boundary and single-owner TUN lifecycle.
- Implement serialized session supervisor and state machine.
- Add structured telemetry, redaction, and error taxonomy.
- Add deterministic unit and Android instrumentation tests.

**Exit gate:** lifecycle tests pass; no concurrent TUN ownership; disconnect/revocation cleanup verified.

### Phase 2 — Engine research and first end-to-end engine
- Audit candidate engines and select one based on requirements, license, support, and Android compatibility.
- Build a thin adapter and pinned dependency strategy.
- Implement profile validation, start, health, data forwarding, stop, and cleanup.
- Verify real application traffic through the tunnel on physical devices.

**Exit gate:** successful connect/data-plane/disconnect and recovery demonstrated on supported physical Android devices; reproducible build and license record available.

### Phase 3 — Network diagnostics and protection
- Implement network-change handling, MTU diagnostics, DNS validation, IPv4 and IPv6 route tests.
- Add leak-test harness, failure injection, Kill Switch evaluation, and safe network handoff.
- Distinguish unsupported tests from passing tests.

**Exit gate:** documented evidence for DNS, IPv4, IPv6, reconnect, disconnect, and failure cases; limitations clearly stated.

### Phase 4 — Network Intelligence
- Build measurement pipeline and endpoint inventory.
- Add failure classification, health scoring, confidence, sample thresholds, and freshness.
- Add hysteresis, cooldown, and conservative ranking.
- Provide an explainable deterministic baseline.

**Exit gate:** decisions traceable to valid measurements; no fabricated endpoint or telemetry; stable behavior under noisy conditions.

### Phase 5 — Smart automation and recovery
- Add automatic candidate selection, bounded retries, failover, and reconnect.
- Coordinate all transitions through the session supervisor.
- Add battery/metered-network controls and anti-flapping protection.

**Exit gate:** physical-device tests show safe recovery without overlapping tunnels, resource leaks, or unbounded retries.

### Phase 6 — Additional engines
- Integrate approved candidates individually behind the adapter contract.
- Run the complete common test suite for each engine.
- Keep engine enablement feature-gated until its own evidence is complete.

**Exit gate:** every enabled engine independently passes license, security, lifecycle, performance, and device gates.

### Phase 7 — AI learning and optimization
- Add outcome dataset with privacy minimization.
- Train/evaluate ranking or classification models only on valid, consented/approved observations.
- Add confidence calibration, drift monitoring, model versioning, staged promotion, and rollback.
- Keep deterministic safety and fallback rules authoritative.

**Exit gate:** measurable improvement over baseline on held-out data and field tests, with no safety regression; explainability and fallback verified.

### Phase 8 — Secure automated updates
- Finalize signed manifest format, pinned public key, artifact signatures, digest checks, compatibility, anti-rollback, staged activation, and rollback.
- Separate app APK update flow from engine/component update flow.
- Add release notes and post-update health checks.

**Exit gate:** tampering, wrong ABI, invalid signature, downgrade, interrupted download, failed activation, and rollback tests all pass.

### Phase 9 — Field validation and release hardening
- Test supported Android versions and ABIs on physical devices.
- Run a network matrix across available Iranian mobile and fixed providers, locations, and time windows where lawful and possible.
- Repeat tests; publish sample counts and dates; do not generalize beyond evidence.
- Run privacy, security, accessibility, localization, battery, memory, and crash reviews.
- Resolve critical/high severity defects and document accepted residual risks.

**Exit gate:** signed test report, reproducible release build, complete required test matrix, and no unresolved release-blocking defects.

### Phase 10 — V1.0.0 Stable release
- Approve signing-key custody and release permissions.
- Build from a tagged, reviewed commit using controlled/reproducible CI.
- Sign APKs and update metadata; verify signatures and hashes independently.
- Publish only the stable release and its release notes/checksums.
- Verify install/update/rollback on supported devices.
- Monitor crash and health signals with privacy controls and a rollback/withdrawal plan.

**Exit gate:** release owner approval, signed artifacts, verified update chain, passing CI/security gates, and completed device evidence.

## 6. Mandatory test matrix

### Build and code quality
- Clean reproducible build for each supported ABI.
- Kotlin/Java compile, lint, formatting, static analysis, dependency/license checks.
- Unit, integration, instrumentation, and regression suites.
- CI must run on the exact release commit.

### Tunnel lifecycle
- Connect success/failure/timeout.
- Repeated connect and disconnect.
- Disconnect during each connection state.
- Network switch during connection.
- Service revocation and process death.
- Engine crash/hang and descriptor invalidation.
- Failover while old engine is stopping.
- Verify no concurrent TUN owner, leaked descriptor, worker, or forwarding thread.

### DNS and IP leakage
- DNS behavior before, during, and after connection.
- IPv4-only, IPv6-only, and dual-stack networks.
- DNS changes and resolver failure.
- Route coverage and excluded routes.
- App restart, network handoff, reconnect, and disconnect.
- Distinguish test-not-supported from pass; record packet captures or equivalent evidence where appropriate.

### Reliability and security
- Kill Switch behavior within Android platform capabilities.
- Invalid/malformed/oversized profiles and subscriptions.
- Certificate and signature failures.
- Corrupted update, wrong digest, wrong ABI, downgrade, interrupted download.
- Rollback after failed health check.
- Secrets redaction in logs and crash reports.
- Resource, battery, memory, and long-duration stability.

### Network field matrix
- Wi-Fi and mobile data.
- Each provider/operator actually available to the test team.
- Different locations and time windows.
- Latency, jitter, loss, throughput, handshake, data-plane success, reconnect duration.
- Repeat samples and confidence; do not publish invented or extrapolated results.

## 7. Signing, secrets, and release governance

- VPN AI must have its own signing identity, separate from every other project.
- Never reuse an exposed or unrelated project's private key.
- Never commit private signing keys, passwords, access tokens, or keystores.
- Do not generate, rotate, or use a production signing key until an explicit owner-approved custody plan exists.
- Store private signing material in an appropriately protected signing environment; CI should receive only the minimum authorized access.
- Pin public verification keys in the app/update trust design and plan a secure key-rotation mechanism before release.
- Protect release tags and require review/CI gates.
- Publish checksums and verifiable signatures for release artifacts.
- No public test APK or beta release is part of the delivery target.

## 8. Privacy and data governance

- Collect only what is needed for connection reliability and user-requested diagnostics.
- Do not collect browsing content, credentials, subscription secrets, or unnecessary identifiers.
- Redact IPs and identifiers in routine logs where feasible; define retention and deletion.
- Make diagnostic upload opt-in unless a clearly disclosed and legally reviewed policy says otherwise.
- Keep local operational telemetry separate from optional analytics.
- Document data fields, purpose, retention, access, and deletion behavior.
- Treat endpoint and network metadata as potentially sensitive.

## 9. Status vocabulary and reporting rules

Use these states consistently:
- **Proposed:** documented idea, not implemented.
- **In development:** code exists but acceptance gates are incomplete.
- **Built:** compilation succeeded; runtime behavior not thereby proven.
- **Unit-tested:** deterministic tests pass.
- **Device-tested:** behavior verified on specified physical device(s).
- **Field-tested:** repeated tests on named network contexts and dates.
- **Approved:** all relevant security, license, compatibility, and acceptance gates passed.
- **Released:** signed artifact published from an approved commit.

Every progress report must identify commit/branch, tests actually run, result, evidence, known limitations, and next blocking item. Never convert an intention or code presence into a completion claim.

## 10. Immediate execution order

1. Confirm product decisions and threat model.
2. Inspect current repository and establish CI/static-analysis baseline.
3. Audit and compare engine candidates; record license and Android compatibility.
4. Implement single-owner TUN and serialized session lifecycle.
5. Complete one engine end-to-end on a physical Android device.
6. Complete DNS, IPv4/IPv6, leak, Kill Switch, and recovery tests.
7. Implement measured network intelligence and explainable selection.
8. Implement smart failover/reconnect and resource controls.
9. Add further engines one at a time after independent approval.
10. Add learning/optimization with confidence and deterministic fallback.
11. Implement secure signed updates and rollback.
12. Complete field validation, signing governance, and stable release gate.
13. Publish **VPN AI v1.0.0 Stable** only after every mandatory gate passes.

## 11. Current project truth

At the time this roadmap was written:
- The repository contains initial documentation, not a completed VPN application.
- No engine is declared production-ready.
- No real-device field-test result is claimed by this document.
- No signed APK or public release is claimed.
- No production signing key is authorized by this roadmap.
- Candidate engines and DPI-related projects remain subject to independent review.

These statements must be updated only when supported by repository commits, CI results, test artifacts, and verifiable device evidence.
