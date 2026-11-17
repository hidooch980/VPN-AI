#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

REPO="hidooch980/VPN-AI"
BRANCH="foundation/vpn-ai-start"
ALIAS="vpnai-release"
WORK_DIR="$HOME/.vpn-ai-signing-bootstrap"
KEYSTORE="$WORK_DIR/vpn-ai-release.jks"
CREDENTIALS="$WORK_DIR/signing-credentials.txt"
ARCHIVE="$WORK_DIR/signing-backup.tar.gz"
BACKUP="$HOME/vpn-ai-release-keystore.jks.enc"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
for cmd in gh keytool openssl base64 git tar; do command -v "$cmd" >/dev/null 2>&1 || fail "Required command not found: $cmd"; done
[[ -d .git ]] || fail "Run this script from the root of a cloned VPN-AI repository."
CURRENT_REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null)" || fail "GitHub CLI is not authenticated. Run: gh auth login"
[[ "$CURRENT_REPO" == "$REPO" ]] || fail "Wrong repository: expected $REPO, found $CURRENT_REPO"
HAS_ADMIN="$(gh api "repos/$REPO" --jq '.permissions.admin' 2>/dev/null)" || fail "Cannot verify GitHub repository permissions."
[[ "$HAS_ADMIN" == "true" ]] || fail "GitHub account needs repository administrator permission to configure Actions secrets and variables."
CURRENT_BRANCH="$(git branch --show-current)"
[[ "$CURRENT_BRANCH" == "$BRANCH" ]] || fail "Checkout branch $BRANCH first (current: $CURRENT_BRANCH)"
[[ ! -e "$WORK_DIR" ]] || fail "Work directory already exists: $WORK_DIR"
[[ ! -e "$BACKUP" ]] || fail "Encrypted backup already exists: $BACKUP"

mkdir -m 700 "$WORK_DIR"
cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT

STORE_PASS="$(openssl rand -hex 32)"
KEY_PASS="$(openssl rand -hex 32)"
read -r -p "Key alias [$ALIAS]: " ENTERED_ALIAS
if [[ -n "$ENTERED_ALIAS" ]]; then ALIAS="$ENTERED_ALIAS"; fi

STORE_PASS="$STORE_PASS" KEY_PASS="$KEY_PASS" keytool -genkeypair -noprompt -v \
  -keystore "$KEYSTORE" -storetype JKS \
  -storepass:env STORE_PASS -keypass:env KEY_PASS \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=VPN AI Android Release, OU=Mobile, O=VPN AI, C=US"

CERT_FP="$(STORE_PASS="$STORE_PASS" keytool -exportcert -rfc -keystore "$KEYSTORE" -storepass:env STORE_PASS -alias "$ALIAS" | openssl x509 -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':' | tr '[:lower:]' '[:upper:]')"
[[ "$(printf '%s' "$CERT_FP" | wc -c)" -eq 64 ]] || fail "Could not calculate a valid SHA-256 certificate fingerprint."

printf '\nCreate a strong passphrase to encrypt the offline Keystore backup.\n'
read -r -s -p "Backup encryption passphrase: " BACKUP_PASS; printf '\n'
read -r -s -p "Repeat backup passphrase: " BACKUP_PASS2; printf '\n'
[[ -n "$BACKUP_PASS" && "$BACKUP_PASS" == "$BACKUP_PASS2" ]] || fail "Backup passphrases do not match or are empty."

{
  printf 'ANDROID_KEYSTORE_PASSWORD=%s\n' "$STORE_PASS"
  printf 'ANDROID_KEY_ALIAS=%s\n' "$ALIAS"
  printf 'ANDROID_KEY_PASSWORD=%s\n' "$KEY_PASS"
  printf 'ANDROID_SIGNING_CERT_SHA256=%s\n' "$CERT_FP"
} > "$CREDENTIALS"
chmod 600 "$CREDENTIALS"
tar -czf "$ARCHIVE" -C "$WORK_DIR" "$(basename "$KEYSTORE")" "$(basename "$CREDENTIALS")"
BACKUP_PASS="$BACKUP_PASS" openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt \
  -in "$ARCHIVE" -out "$BACKUP" -pass env:BACKUP_PASS
chmod 600 "$BACKUP"
unset BACKUP_PASS BACKUP_PASS2

# Secret values are piped on stdin; none are printed or passed as command-line arguments.
base64 "$KEYSTORE" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$STORE_PASS" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set ANDROID_KEY_ALIAS --repo "$REPO"
printf '%s' "$KEY_PASS" | gh secret set ANDROID_KEY_PASSWORD --repo "$REPO"
printf '%s' "$CERT_FP" | gh variable set ANDROID_SIGNING_CERT_SHA256 --repo "$REPO"

printf '\nSUCCESS: GitHub signing secrets and pinned certificate fingerprint were configured.\n'
printf 'Repository: %s\nBranch: %s\nAlias: %s\nCertificate SHA-256: %s\n' "$REPO" "$BRANCH" "$ALIAS" "$CERT_FP"
printf 'Encrypted offline backup: %s\n' "$BACKUP"
printf 'Keep the backup passphrase separately. Never upload the backup or Keystore to GitHub.\n'
printf 'The temporary plaintext Keystore and generated passwords have been removed.\n'
