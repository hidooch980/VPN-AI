# VPN AI

An independent Android-first VPN application project focused on secure, intelligent, and automated connectivity.

## Project status

- **Development stage:** Foundation implementation with automated update delivery
- **Target:** VPN AI v1.0.x Stable (signed)
- **Release status:** A stable release is published only after the persistent signing identity is available and the release verification gates pass
- **Engine status:** No engine is yet verified on a physical device
- **Production readiness:** Not established

## Canonical documentation

- [Master roadmap](docs/ROADMAP.md)
- [Architecture](docs/ARCHITECTURE.md)

## Product principles

VPN AI is Android-first, AI-first, automation-first, and security-first. It is designed to evaluate measured network conditions, select eligible connection paths, recover safely from failures, and improve decisions from verified outcomes.

AI may optimize decisions; deterministic lifecycle and security controls remain authoritative. No country, endpoint, latency, throughput, availability, or success rate may be fabricated.

## Automated release and updates

- Each successful signed-release workflow run receives a monotonically increasing Android versionCode and a 1.0.x version name.
- The release workflow publishes a versioned GitHub Release with the APK, certificate details, and SHA-256 record.
- The app checks the latest GitHub Release on launch and periodically (every 12 hours).
- The updater downloads a newer APK only when a SHA-256 digest is available from GitHub release-asset metadata and verifies the downloaded bytes before presenting the installer.
- Android Package Manager validates signing-certificate continuity during installation. A different signing certificate cannot update an existing installation with the same application ID.
- Ordinary Android apps cannot silently install APK updates. Android requires the user to grant per-app install permission where applicable and approve each package installation in the system installer.
- One persistent Android signing keystore and alias must be reused for every release; generating a replacement keystore is forbidden.
- The release workflow requires the protected GitHub Actions secrets ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD, plus the non-secret repository Actions variable ANDROID_SIGNING_CERT_SHA256 containing the pinned certificate SHA-256 fingerprint.
- Every release is rejected unless the keystore certificate matches that pinned fingerprint. After the first release, the workflow also compares the new APK certificate with the previous published APK and rejects any mismatch.
- The private keystore must never be committed to the repository, published as an artifact, or embedded in the APK.


### One-time signing bootstrap

On the trusted Ubuntu VPS or another trusted Linux host, after cloning this repository and checking out `foundation/vpn-ai-start`, run:

```bash
bash tools/bootstrap-android-signing.sh
```

The script generates a single RSA release keystore locally, creates an AES-256 encrypted offline backup at `$HOME/vpn-ai-release-keystore.jks.enc`, and sends the four signing values to GitHub Actions Secrets plus the certificate fingerprint to the repository Actions variable using GitHub CLI standard input. It does not print signing passwords or place the plaintext keystore in the repository. It requires `gh`, `keytool`, `openssl`, and an authenticated GitHub CLI session with permission to manage repository Actions secrets and variables.

Keep the backup encryption passphrase separately from the backup file. Store a second copy of the encrypted backup in a separate secure location. Never regenerate the signing key for an existing application identity. If the backup or its passphrase is lost, future updates signed with that identity may become impossible.

The script is one-time setup. Do not rerun it after successful setup; doing so would create a different signing identity and the release fingerprint gate would reject it.

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
