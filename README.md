# VPN AI

An independent VPN application project. MSN-GUARD is used as a technical reference for architecture and engineering practices; this repository is not a fork and does not contain copied MSN-GUARD source code.

## Project status

**Stage:** Foundation / architecture definition  
**Release target:** VPN AI `v1.0.0 Stable` (signed; no public beta/test APK)  
**Release status:** No release or APK is declared  
**Runtime status:** No VPN engine is declared production-ready  
**Reference repository:** [mbm110/MSN-GUARD](https://github.com/mbm110/MSN-GUARD)  
**Reference license:** AGPL-3.0 (review required before any code reuse)

## Canonical project documentation

- **[Master Roadmap](docs/ROADMAP.md):** the single source of truth for product scope, AI and automation requirements, phases, acceptance gates, mandatory test matrix, security, privacy, signing, updates, and V1.0.0 Stable release criteria.
- [Initial Architecture](docs/ARCHITECTURE.md): platform boundaries, single-TUN ownership, session lifecycle, engine contract, diagnostics, and technical design.
- [Reference Audit](docs/REFERENCE_AUDIT.md): findings and boundaries for using external projects as references.

Read the Master Roadmap before proposing new components or asking for repeated project requirements. New decisions and verified progress must be recorded in the relevant canonical document.

## Product direction

VPN AI is **AI-first, automation-first, security-first, and Android-first**. The system is intended to diagnose network conditions, select eligible engines/endpoints using measured evidence, recover from connection failures, learn from verified outcomes, and securely update the app and independently versioned components.

AI is an optimization and decision-support layer. Deterministic lifecycle and security controls remain authoritative. No country, server, ping, throughput, availability, bypass, or success rate may be fabricated.

## Core engineering rules

- Keep the platform TUN interface under one authoritative owner per active session.
- Serialize connect, reconnect, failover, and disconnect through one session supervisor.
- Support independently testable engine adapters; do not advertise an engine as ready until its runtime and device tests pass.
- Make DNS, IPv4, IPv6, route handling, reconnect, teardown, and failure recovery observable and testable.
- Treat imported profiles and subscriptions as untrusted input.
- Verify update manifests and artifacts cryptographically before activation; support rollback.
- Keep user-visible connection state tied to runtime telemetry, not log-string inference.
- Keep private signing keys and credentials out of GitHub, source code, APKs, and logs.
- Review third-party licenses and security before integration. Public availability does not remove license obligations.

## Development and release gates

The required sequence is defined in [docs/ROADMAP.md](docs/ROADMAP.md): independent foundation, engine audit, one end-to-end engine, DNS/IP/leak and lifecycle testing, measured network intelligence, automatic failover/reconnect, individually approved additional engines, AI learning, secure updates, real-device field validation, and signed stable release.

No test APK is treated as a final release. Android consumer devices generally require user confirmation for APK installation; silent self-installation is not assumed. A signing key must have an explicit owner-approved custody plan before generation or use.

## Repository structure (planned)

```text
app/                  Android application and UI
vpn/                  platform VPN service and TUN ownership
engines/              isolated protocol engine adapters
core/                 session supervisor and shared contracts
network/              connectivity, DNS, MTU, route diagnostics
security/             profile validation, integrity, update verification
tests/                unit, instrumentation, and device test plans
docs/                 roadmap, architecture, threat model, audits, release process
```

This tree is a plan, not a claim that these modules already exist.
