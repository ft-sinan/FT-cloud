#!/usr/bin/env bash
set -euo pipefail

# Generates a permanent FT Cloud Android release keystore and prints/uploads
# the 4 GitHub Actions secrets so every future CI & tag build shares one permanent signature.

KEYSTORE_FILE="${1:-ftcloud-release.jks}"
KEY_ALIAS="ftcloud"

if [[ -f "$KEYSTORE_FILE" ]]; then
  echo "Keystore already exists at $KEYSTORE_FILE — refusing to overwrite."
  exit 1
fi

read -r -s -p "Choose a strong keystore password (min 6 chars): " STORE_PASS
echo ""

keytool -genkeypair -v \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$STORE_PASS" \
  -alias "$KEY_ALIAS" \
  -keypass "$STORE_PASS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=FT Cloud,OU=Mobile,O=FT,C=IN"

B64="$(base64 < "$KEYSTORE_FILE" | tr -d '\n\r')"

if command -v gh >/dev/null 2>&1; then
  echo "Uploading permanent signing secrets to GitHub repository..."
  printf '%s' "$B64" | gh secret set FTCLOUD_RELEASE_KEYSTORE_BASE64
  printf '%s' "$STORE_PASS" | gh secret set FTCLOUD_RELEASE_STORE_PASSWORD
  printf '%s' "$KEY_ALIAS" | gh secret set FTCLOUD_RELEASE_KEY_ALIAS
  printf '%s' "$STORE_PASS" | gh secret set FTCLOUD_RELEASE_KEY_PASSWORD
  echo "Done! Back up $KEYSTORE_FILE safely offline (do NOT commit it to Git)."
else
  echo "Add these 4 repository secrets in GitHub -> Settings -> Secrets and variables -> Actions:"
  echo "  FTCLOUD_RELEASE_KEYSTORE_BASE64 = $B64"
  echo "  FTCLOUD_RELEASE_KEY_ALIAS       = $KEY_ALIAS"
  echo "  FTCLOUD_RELEASE_STORE_PASSWORD  = (your chosen password)"
  echo "  FTCLOUD_RELEASE_KEY_PASSWORD    = (your chosen password)"
fi
