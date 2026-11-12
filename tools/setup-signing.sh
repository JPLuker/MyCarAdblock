#!/usr/bin/env bash
# Run once on your own trusted machine (not in a GitHub runner).
# Generates or reuses a persistent signing key, then uploads it as GitHub Actions
# encrypted secrets. The keystore and password never enter git or chat.
set -euo pipefail
umask 077

repo="${SIGNING_REPOSITORY:-JPLuker/MyCarAdblock}"
folder="${XDG_DATA_HOME:-$HOME/.local/share}/mycaradblock/android-signing"
store="$folder/mycaradblock.jks"
password_file="$folder/signing-password"
alias_name="mycaradblock"

for app in gh keytool openssl base64; do
    if ! command -v "$app" >/dev/null 2>&1; then
        echo "Missing required program: $app" >&2
        exit 1
    fi
done
gh auth status >/dev/null 2>&1 || {
    echo "Sign in to GitHub first: gh auth login" >&2
    exit 1
}
mkdir -p "$folder"
chmod 700 "$folder"

if [[ -f "$store" && ! -f "$password_file" ]] || [[ ! -f "$store" && -f "$password_file" ]]; then
    echo "Signing files are incomplete. Restore both files from backup; do not create a replacement key." >&2
    exit 1
fi

if [[ ! -f "$store" ]]; then
    echo "Generating one persistent key for debug APKs, optional GeckoView splits and fork releases."
    export MYCAR_SIGNING_PASSWORD="$(openssl rand -hex 32)"
    printf '%s\n' "$MYCAR_SIGNING_PASSWORD" > "$password_file"
    keytool -genkeypair -noprompt -storetype PKCS12 \
        -keystore "$store" -storepass:env MYCAR_SIGNING_PASSWORD \
        -keypass:env MYCAR_SIGNING_PASSWORD -alias "$alias_name" \
        -keyalg RSA -keysize 3072 -validity 20000 \
        -dname "CN=MyCarAdblock Android,O=MyCarAdblock" >/dev/null 2>&1
    echo "New signing key generated."
else
    export MYCAR_SIGNING_PASSWORD="$(cat "$password_file")"
    echo "Reusing existing signing key."
fi
chmod 600 "$password_file" "$store"

# Hash the DER certificate itself. Do not parse keytool's human-readable fingerprint.
fingerprint="$(
    keytool -exportcert -rfc -keystore "$store" -alias "$alias_name" \
        -storepass:env MYCAR_SIGNING_PASSWORD 2>/dev/null |
        openssl x509 -inform PEM -outform DER |
        openssl dgst -sha256 -r | awk '{print $1}'
)"
if [[ ! "$fingerprint" =~ ^[a-f0-9]{64}$ ]]; then
    echo "Cannot read the signing certificate. No GitHub secrets have been changed." >&2
    exit 1
fi

base64 < "$store" | tr -d '\r\n' | gh secret set KEYSTORE_BASE64 --repo "$repo"
printf '%s' "$MYCAR_SIGNING_PASSWORD" | gh secret set KEYSTORE_STORE_PASSWORD --repo "$repo"
printf '%s' "$MYCAR_SIGNING_PASSWORD" | gh secret set KEYSTORE_KEY_PASSWORD --repo "$repo"
printf '%s' "$alias_name" | gh secret set KEYSTORE_KEY_ALIAS --repo "$repo"
printf '%s' "$fingerprint" | gh secret set KEYSTORE_CERT_SHA256 --repo "$repo"
unset MYCAR_SIGNING_PASSWORD

echo
echo "Persistent Android signing is configured for $repo."
echo "Public SHA-256 certificate fingerprint: $fingerprint"
echo
echo "BACK UP BOTH FILES securely. Losing either one prevents future updates:"
echo "  $store"
echo "  $password_file"
echo
echo "Important: previously installed myCar Debug builds signed with the temporary key"
echo "cannot be updated in place. Uninstall myCar Debug ONCE before installing a build"
echo "signed with this persistent key (its debug-app data will be erased)."
