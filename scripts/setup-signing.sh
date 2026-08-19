#!/usr/bin/env bash
# NexaFlow release-signing bootstrap and verification.
#
# This script deliberately NEVER creates a key by default. Android only accepts
# an APK update when its signing certificate matches the installed application.
# Accidentally generating a new key would force existing NexaFlow installs to be
# uninstalled, so a new identity requires two explicit acknowledgement flags.
#
# The signing material is intentionally gitignored:
#   keystore/nexaflow-release.jks
#   keystore/keystore.properties
#
# CI reads the same material through these GitHub Actions secrets:
#   NEXAFLOW_KEYSTORE_BASE64
#   NEXAFLOW_KEYSTORE_PASSWORD
#   NEXAFLOW_KEY_ALIAS
#   NEXAFLOW_KEY_PASSWORD
#
# Usage:
#   ./scripts/setup-signing.sh --verify
#       Verify the existing NexaFlow release key and print its SHA-256 certificate
#       fingerprint. This is the safe default.
#
#   ./scripts/setup-signing.sh --configure-existing
#       Interactively create/update keystore/keystore.properties for an already
#       restored release key. Passwords are read without echoing.
#
#   ./scripts/setup-signing.sh --upload
#       Verify the existing key, then upload its four signing values as GitHub
#       Actions secrets. It never uploads a generated or unverified key.
#
#   ./scripts/setup-signing.sh --create-new-key --acknowledge-no-old-updates
#       Create a new PKCS12 identity ONLY when the original signing certificate is
#       permanently unavailable and uninstalling old sideloaded installations is
#       acceptable. Do not use this for a published Play App Signing application.
#
# Options:
#   --repo OWNER/REPO     GitHub repository for --upload (default: Alaa91H/NexaFlow)
#   --help                Show this help.
set -euo pipefail

REPO="${GITHUB_REPOSITORY:-Alaa91H/NexaFlow}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE_DIR="$ROOT/keystore"
KEYSTORE="$KEYSTORE_DIR/nexaflow-release.jks"
PROPS="$KEYSTORE_DIR/keystore.properties"
DEFAULT_ALIAS="nexaflow"
MODE="verify"
ACKNOWLEDGE_NEW_IDENTITY=0

usage() {
    sed -n '2,42p' "$0"
}

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

note() {
    printf '%s\n' "$*"
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

resolve_keytool() {
    if command -v keytool >/dev/null 2>&1; then
        KEYTOOL="$(command -v keytool)"
    elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
        KEYTOOL="$JAVA_HOME/bin/keytool"
    else
        die "keytool was not found. Install a JDK or set JAVA_HOME."
    fi
}

random_password() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 32
    else
        od -An -tx1 -N32 /dev/urandom | tr -d ' \n'
    fi
}

read_property() {
    local key="$1"
    [ -f "$PROPS" ] || return 0
    sed -n "s/^${key}=//p" "$PROPS" | head -n 1
}

validate_property_value() {
    local label="$1"
    local value="$2"
    case "$value" in
        *$'\n'*|*$'\r'*) die "$label must not contain a line break." ;;
    esac
}

write_properties() {
    local store_password="$1"
    local alias="$2"
    local key_password="$3"
    local maps_api_key
    maps_api_key="$(read_property mapsApiKey || true)"

    validate_property_value "store password" "$store_password"
    validate_property_value "key alias" "$alias"
    validate_property_value "key password" "$key_password"

    umask 077
    cat > "$PROPS" <<EOF
# Local NexaFlow release credentials. Gitignored: do not commit or share.
storeFile=keystore/nexaflow-release.jks
storePassword=$store_password
keyAlias=$alias
keyPassword=$key_password
EOF
    if [ -n "$maps_api_key" ]; then
        validate_property_value "Maps API key" "$maps_api_key"
        printf 'mapsApiKey=%s\n' "$maps_api_key" >> "$PROPS"
    fi
    chmod 600 "$PROPS"
    note "Wrote protected local credentials to $PROPS"
}

load_credentials() {
    STORE_PASSWORD="${NEXAFLOW_KEYSTORE_PASSWORD:-$(read_property storePassword || true)}"
    KEY_ALIAS="${NEXAFLOW_KEY_ALIAS:-$(read_property keyAlias || true)}"
    KEY_PASSWORD="${NEXAFLOW_KEY_PASSWORD:-$(read_property keyPassword || true)}"

    [ -n "$STORE_PASSWORD" ] || die "Missing store password. Run --configure-existing or set NEXAFLOW_KEYSTORE_PASSWORD for this command."
    [ -n "$KEY_ALIAS" ] || die "Missing key alias. Run --configure-existing or set NEXAFLOW_KEY_ALIAS for this command."
    [ -n "$KEY_PASSWORD" ] || die "Missing key password. Run --configure-existing or set NEXAFLOW_KEY_PASSWORD for this command."
}

certificate_sha256() {
    "$KEYTOOL" -list -v \
        -keystore "$KEYSTORE" \
        -storepass "$STORE_PASSWORD" \
        -alias "$KEY_ALIAS" 2>/dev/null \
        | awk -F': ' '/SHA256:/{print $2; exit}'
}

verify_existing_key() {
    [ -f "$KEYSTORE" ] || die "Release keystore not found at $KEYSTORE. Restore the original NexaFlow signing key before attempting an update."
    load_credentials

    if ! "$KEYTOOL" -list \
        -keystore "$KEYSTORE" \
        -storepass "$STORE_PASSWORD" \
        -alias "$KEY_ALIAS" >/dev/null 2>&1; then
        die "The keystore, store password, or alias is invalid. Do not create a replacement key; restore the key that signed the installed application."
    fi

    CERT_SHA256="$(certificate_sha256)"
    [ -n "$CERT_SHA256" ] || die "Could not read the SHA-256 certificate fingerprint from the release key."
    chmod 600 "$KEYSTORE" 2>/dev/null || true
    note "Verified NexaFlow release key. Certificate SHA-256: $CERT_SHA256"
    note "Compare this fingerprint with the installed APK or the Play App Signing certificate before publishing."
}

configure_existing() {
    [ -f "$KEYSTORE" ] || die "No release keystore exists at $KEYSTORE. Restore the original NexaFlow key first; --configure-existing never generates one."
    local existing_alias
    existing_alias="$(read_property keyAlias || true)"
    printf 'Key alias [%s]: ' "${existing_alias:-$DEFAULT_ALIAS}"
    read -r entered_alias
    entered_alias="${entered_alias:-${existing_alias:-$DEFAULT_ALIAS}}"

    printf 'Keystore password: '
    read -r -s entered_store_password
    printf '\nKey password (press Enter when it is the same): '
    read -r -s entered_key_password
    printf '\n'
    entered_key_password="${entered_key_password:-$entered_store_password}"

    write_properties "$entered_store_password" "$entered_alias" "$entered_key_password"
}

create_new_key() {
    [ "$ACKNOWLEDGE_NEW_IDENTITY" -eq 1 ] || die "Refusing to create a new identity. Add --acknowledge-no-old-updates only after confirming the original key cannot be recovered."
    [ ! -e "$KEYSTORE" ] || die "A keystore already exists at $KEYSTORE. Refusing to overwrite a signing identity."

    note "WARNING: creating a new signing identity. It cannot update an APK signed by the original NexaFlow key."
    local new_password
    new_password="$(random_password)"
    umask 077
    "$KEYTOOL" -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storetype PKCS12 \
        -storepass "$new_password" \
        -keypass "$new_password" \
        -alias "$DEFAULT_ALIAS" \
        -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
        -validity 10000 \
        -dname "CN=NexaFlow, OU=Mobile, O=NexaFlow, L=Internet, C=US" >/dev/null
    chmod 600 "$KEYSTORE"
    write_properties "$new_password" "$DEFAULT_ALIAS" "$new_password"
    note "Created a new post-recovery identity. Back up both gitignored files securely before publishing."
}

upload_secrets() {
    require_command gh
    gh auth status >/dev/null 2>&1 || die "GitHub CLI is not authenticated. Run 'gh auth login' with permission to manage Actions secrets."

    # Verify first so CI never receives an accidental, invalid, or mismatched local key.
    verify_existing_key
    printf '%s' "$(base64 "$KEYSTORE" | tr -d '\n')" | gh secret set NEXAFLOW_KEYSTORE_BASE64 --repo "$REPO"
    gh secret set NEXAFLOW_KEYSTORE_PASSWORD --repo "$REPO" --body "$STORE_PASSWORD"
    gh secret set NEXAFLOW_KEY_ALIAS --repo "$REPO" --body "$KEY_ALIAS"
    gh secret set NEXAFLOW_KEY_PASSWORD --repo "$REPO" --body "$KEY_PASSWORD"
    note "Configured the four NexaFlow signing secrets for $REPO."
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --verify)
            MODE="verify"
            ;;
        --configure-existing)
            MODE="configure-existing"
            ;;
        --upload|-u)
            MODE="upload"
            ;;
        --create-new-key)
            MODE="create-new-key"
            ;;
        --acknowledge-no-old-updates)
            ACKNOWLEDGE_NEW_IDENTITY=1
            ;;
        --repo)
            shift
            [ "$#" -gt 0 ] || die "--repo requires OWNER/REPO."
            REPO="$1"
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            die "Unknown option: $1. Use --help for supported options."
            ;;
    esac
    shift
done

resolve_keytool
mkdir -p "$KEYSTORE_DIR"
chmod 700 "$KEYSTORE_DIR" 2>/dev/null || true

case "$MODE" in
    verify)
        verify_existing_key
        ;;
    configure-existing)
        configure_existing
        verify_existing_key
        ;;
    create-new-key)
        create_new_key
        verify_existing_key
        ;;
    upload)
        upload_secrets
        ;;
    *)
        die "Unsupported mode: $MODE"
        ;;
esac

note "Signing setup completed safely."
