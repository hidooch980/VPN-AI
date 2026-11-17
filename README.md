# VPN AI

An independent VPN application project. MSN-GUARD is used as a technical reference for architecture and engineering practices; this repository is not a fork and does not contain copied MSN-GUARD source code.

## Project status

**Stage:** Foundation / architecture definition  
**Release status:** No release or APK is declared  
**Runtime status:** No VPN engine is declared production-ready  
**Reference repository:** [mbm110/MSN-GUARD](https://github.com/mbm110/MSN-GUARD)  
**Reference license:** AGPL-3.0 (review required before any code reuse)

## Engineering goals

- Build a reliable VPN client with explicit tunnel lifecycle and resource ownership.
- Keep the platform TUN interface under one authoritative owner per active session.
- Support independently testable engine adapters; do not advertise an engine as ready until its runtime and device tests pass.
- Make DNS, IPv4, IPv6, route handling, reconnect, and teardown observable and testable.
- Use measured network facts only. Never invent countries, server locations, ping, throughput, or availability.
- Treat imported profiles and subscriptions as untrusted input.
- Verify update manifests and artifacts cryptographically before activation; support rollback.
- Keep user-visible connection state tied to runtime telemetry, not log-string inference.

## Initial architecture

The first architecture proposal is documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). It deliberately leaves the protocol/engine roster open until product requirements, licensing, Android compatibility, and maintainability have been assessed.

## Reference audit

Initial findings and adaptation boundaries are recorded in [docs/REFERENCE_AUDIT.md](docs/REFERENCE_AUDIT.md).

## Development gates

1. Confirm product scope, target Android versions, supported CPU ABIs, and distribution model.
2. Finalize engine roster and license/dependency review.
3. Implement one end-to-end engine path with single-owner TUN lifecycle.
4. Add deterministic unit/instrumentation tests for lifecycle, DNS, routes, and recovery.
5. Run real-device network and leak tests before claiming production readiness.
6. Add signed update and release pipeline only after key custody and threat model are approved.

## Safety and release policy

- No test APK is treated as a final release.
- No signing key is generated, replaced, or published without explicit approval and a documented custody plan.
- A failed health check must not silently become a successful connection state.
- A disconnect must close owned descriptors, cancel workers, stop forwarding, and restore platform-managed state.
- Any code derived from AGPL-3.0 material requires a deliberate license-compliance decision before inclusion.

## Repository structure (planned)

```text
app/                  Android application and UI
vpn/                  platform VPN service and TUN ownership
engines/              isolated protocol engine adapters
core/                 session supervisor and shared contracts
network/              connectivity, DNS, MTU, route diagnostics
security/             profile validation, integrity, update verification
tests/                unit, instrumentation, and device test plans
docs/                 architecture, threat model, audits, release process
```

This tree is a plan, not a claim that these modules already exist.
