#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# NexaFlow — Release signing setup (idempotent, cross-platform)
#
# Generates the project release keystore ONCE and optionally uploads the
# credentials to GitHub Actions so CI signs release builds with the SAME
# stable key. A stable signing certificate is what lets Android install
# updates over existing installs without uninstalling.
#
# Usage:
#   ./scripts/setup-signing.sh              generate locally (idempotent)
#   ./scripts/setup-signing.sh --upload     also push secrets to GitHub (needs gh)
#   ./scripts/setup-signing.sh --verify     only print the current fingerprint
#   ./scripts/setup-signing.sh --force      regenerate WARNING — breaks updates
#
# Outputs (all gitignored — never commit):
#   keystore/nexaflow-release.jks          the keystore
#   keystore/keystore.properties           storeFile / storePassword / keyAlias / keyPassword
#
# CI secrets (android-ci.yml reads these):
#   NEXAFLOW_KEYSTORE_BASE64, NEXAFLOW_KEYSTORE_PASSWORD,
#   NEXAFLOW_KEY_ALIAS, NEXAFLOW_KEY_PASSWORD
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO="${GITHUB_REPOSITORY:-Alaa91H/NexaFlow}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE_DIR="$ROOT/keystore"
KEYSTORE="$KEYSTORE_DIR/nexaflow-release.jks"
PROPS="$KEYSTORE_DIR/keystore.properties"
ALIAS="nexaflow"
UPLOAD=0
VERIFY_ONLY=0
FORCE_REGEN=0

for arg in "$@"; do
    case "$arg" in
        --upload|-u)  UPLOAD=1 ;;
        --verify|-v)  VERIFY_ONLY=1 ;;
        --force|-f)   FORCE_REGEN=1 ;;
        --help|-h)
            sed -n '2,26p' "$0"
            exit 0
            ;;
        *)
            echo "Unknown option: $arg (try --help)" >&2
            exit 1
            ;;
    esac
done

# ── locate keytool ──────────────────────────────────────────────────────────
find_keytool() {
    # 1) PATH
    if command -v keytool >/dev/null 2>&1; then
        echo "keytool"; return
    fi
    # 2) JAVA_HOME
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
        echo "$JAVA_HOME/bin/keytool"; return
    fi
    # 3) Common JDK locations (Windows / macOS / Linux)
    for candidate in \
        "/c/Program Files/Eclipse Adoptium" \
        "/c/Program Files/Java" \
        "/c/Program Files/Android/Android Studio/jbr" \
        "$HOME/.jdks" \
        "/usr/local/opt/openjdk" \
        "/usr/lib/jvm"; do
        if [ -d "$candidate" ]; then
            found=$(find "$candidate" -name keytool -type f 2>/dev/null | head -1)
            if [ -n "$found" ]; then echo "$found"; return; fi
        fi
    done
    echo ""
}

KEYTOOL="$(find_keytool)"
if [ -z "$KEYTOOL" ]; then
    echo "ERROR: keytool not found." >&2
    echo "Install a JDK (17+) and set JAVA_HOME, or add keytool to PATH." >&2
    exit 1
fi
echo "Using keytool: $KEYTOOL"

mkdir -p "$KEYSTORE_DIR"

# ── print fingerprint of existing keystore ──────────────────────────────────
print_fingerprint() {
    local ks="$1" pass="$2" alias="$3"
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  Signing certificate fingerprint                           ║"
    echo "╠══════════════════════════════════════════════════════════════╣"
    "$KEYTOOL" -list -v -keystore "$ks" -storepass "$pass" -alias "$alias" 2>/dev/null \
        | grep -E "Alias name|Owner|SHA256|Valid from" \
        | sed 's/^/║  /' || true
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Compare the SHA256 above with what your installed app uses."
    echo "If they differ, you MUST uninstall the old app before installing."
    echo ""
}

# ── read password from keystore.properties ──────────────────────────────────
read_password() {
    grep -E '^storePassword=' "$PROPS" 2>/dev/null | cut -d= -f2- || true
}

# ── --verify mode ──────────────────────────────────────────────────────────
if [ "$VERIFY_ONLY" -eq 1 ]; then
    if [ ! -f "$KEYSTORE" ]; then
        echo "No keystore found at $KEYSTORE" >&2
        exit 1
    fi
    PASS="$(read_password)"
    if [ -z "$PASS" ]; then
        echo "ERROR: $PROPS is missing storePassword." >&2
        exit 1
    fi
    PROPS_ALIAS="$(grep -E '^keyAlias=' "$PROPS" 2>/dev/null | cut -d= -f2- || true)"
    [ -n "$PROPS_ALIAS" ] && ALIAS="$PROPS_ALIAS"
    print_fingerprint "$KEYSTORE" "$PASS" "$ALIAS"
    exit 0
fi

# ── random password ────────────────────────────────────────────────────────
random_password() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 24
    elif [ -f /dev/urandom ]; then
        od -An -tx1 -N24 /dev/urandom | tr -d ' \n'
    else
        # Windows fallback: PowerShell
        powershell.exe -NoProfile -Command "[System.Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24)) -replace '[/+=]',''" 2>/dev/null || \
        python3 -c "import secrets; print(secrets.token_hex(24))" 2>/dev/null || \
        echo "NEEDS_MANUAL_PASSWORD"
    fi
}

PASS=""

# ── generate exactly once ──────────────────────────────────────────────────
if [ -f "$KEYSTORE" ] && [ "$FORCE_REGEN" -eq 0 ]; then
    echo "Keystore exists at $KEYSTORE — leaving it untouched."
    PASS="$(read_password)"
    PROPS_ALIAS="$(grep -E '^keyAlias=' "$PROPS" 2>/dev/null | cut -d= -f2- || true)"
    [ -n "$PROPS_ALIAS" ] && ALIAS="$PROPS_ALIAS"
    if [ -z "$PASS" ]; then
        echo "ERROR: keystore exists but $PROPS is missing storePassword." >&2
        echo "Fill it in and re-run with --upload to push to GitHub." >&2
        exit 1
    fi
    print_fingerprint "$KEYSTORE" "$PASS" "$ALIAS"
else
    if [ "$FORCE_REGEN" -eq 1 ] && [ -f "$KEYSTORE" ]; then
        echo "⚠  FORCE REGENERATION — the old keystore will be backed up."
        BACKUP="$KEYSTORE_DIR/nexaflow-release.$(date +%Y%m%d-%H%M%S).bak.jks"
        cp "$KEYSTORE" "$BACKUP"
        echo "   Old keystore backed up to: $BACKUP"
        echo "   WARNING: Existing users will NOT be able to update without uninstalling."
        echo ""
    fi
    PASS="$(random_password)"
    if [ "$PASS" = "NEEDS_MANUAL_PASSWORD" ]; then
        echo "ERROR: Cannot generate a random password. Install openssl or python3." >&2
        exit 1
    fi
    "$KEYTOOL" -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass "$PASS" \
        -keypass "$PASS" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 4096 \
        -validity 10000 \
        -dname "CN=NexaFlow, OU=Mobile, O=NexaFlow, L=Internet, C=US"
    echo "Generated $KEYSTORE"
    print_fingerprint "$KEYSTORE" "$PASS" "$ALIAS"
fi

# ── (re)write keystore.properties (preserve mapsApiKey) ────────────────────
MAPS_KEY="$(grep -E '^mapsApiKey=' "$PROPS" 2>/dev/null | cut -d= -f2- || true)"
cat > "$PROPS" <<EOF
# Generated by scripts/setup-signing.sh — gitignored, never commit.
# IMPORTANT: Do NOT delete this file or regenerate the keystore —
# the signing certificate must remain stable for update-over-install.
storeFile=keystore/nexaflow-release.jks
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
EOF
if [ -n "$MAPS_KEY" ]; then
    echo "mapsApiKey=$MAPS_KEY" >> "$PROPS"
fi
echo "Wrote $PROPS"

# ── upload to GitHub Actions secrets ────────────────────────────────────────
if [ "$UPLOAD" -eq 1 ]; then
    if ! command -v gh >/dev/null 2>&1; then
        echo "ERROR: gh CLI not found. Install from https://cli.github.com" >&2
        exit 1
    fi
    gh auth status >/dev/null 2>&1 || {
        echo "ERROR: gh is not authenticated. Run: gh auth login" >&2
        exit 1
    }
    # base64 encode for safe storage as a secret
    if command -v base64 >/dev/null 2>&1; then
        B64="$(base64 "$KEYSTORE" | tr -d '\n')"
    else
        # Windows: PowerShell base64
        B64="$(powershell.exe -NoProfile -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('$KEYSTORE'))" 2>/dev/null | tr -d '\r\n')"
    fi
    printf '%s' "$B64" | gh secret set NEXAFLOW_KEYSTORE_BASE64 --repo "$REPO"
    gh secret set NEXAFLOW_KEYSTORE_PASSWORD --repo "$REPO" --body "$PASS"
    gh secret set NEXAFLOW_KEY_ALIAS       --repo "$REPO" --body "$ALIAS"
    gh secret set NEXAFLOW_KEY_PASSWORD     --repo "$REPO" --body "$PASS"
    echo ""
    echo "✅ Signing secrets uploaded to GitHub repo: $REPO"
    echo "   NEXAFLOW_KEYSTORE_BASE64"
    echo "   NEXAFLOW_KEYSTORE_PASSWORD"
    echo "   NEXAFLOW_KEY_ALIAS"
    echo "   NEXAFLOW_KEY_PASSWORD"
fi

echo ""
echo "Done. All future release builds will sign with this stable key."
echo "Users can install updates over existing installs (no uninstall needed)."
echo ""
echo "Quick commands:"
echo "  ./scripts/setup-signing.sh --verify   print the certificate fingerprint"
echo "  ./scripts/setup-signing.sh --upload   push secrets to GitHub Actions"
