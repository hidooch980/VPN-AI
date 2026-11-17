# VPN AI

An independent Android-first VPN application project focused on secure, intelligent, and automated connectivity.

## Project status

- **Development stage:** Foundation and first implementation
- **Target:** VPN AI `v1.0.0 Stable` (signed)
- **Release status:** No APK or release has been published
- **Engine status:** No engine is yet verified on a physical device
- **Production readiness:** Not established

## Canonical documentation

- [Master roadmap](docs/ROADMAP.md)
- [Architecture](docs/ARCHITECTURE.md)

## Product principles

VPN AI is Android-first, AI-first, automation-first, and security-first. It is designed to evaluate measured network conditions, select eligible connection paths, recover safely from failures, and improve decisions from verified outcomes.

AI may optimize decisions; deterministic lifecycle and security controls remain authoritative. No country, endpoint, latency, throughput, availability, or success rate may be fabricated.

## Engineering and release rules

- Exactly one authoritative owner controls the active VPN tunnel per session.
- Connection, reconnection, failover, and disconnection are serialized.
- An engine is not considered ready until its runtime, security, and physical-device tests pass.
- DNS, IPv4, IPv6, route handling, failure recovery, and teardown must be independently tested.
- Imported profiles and subscriptions are untrusted input.
- Updates require cryptographic verification, compatibility checks, anti-rollback policy, and recovery.
- Private signing keys and credentials must never be committed or embedded in source code.
- No public beta APK is planned. The release target is a signed stable version after all acceptance gates pass.

## Current implementation

The repository is being built incrementally. A code file or successful compilation is not proof of a working VPN connection. Real tunnel behavior must be demonstrated on supported physical Android devices before any stable-release claim.

