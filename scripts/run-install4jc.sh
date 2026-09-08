#!/usr/bin/env bash
# install4jc wrapper: injects local secrets from files, never prints them.
# install4jc needs -D and name=value as two argv words (same as build.xml),
# not -Dname=value (that errors: 'D' requires a value).
# Usage (repo root): scripts/run-install4jc.sh [install4jc args...]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SP="$ROOT/signpath"
I4J="$ROOT/install4j"
extra=()

license=""
if [[ -s "$I4J/license.txt" ]]; then
  license=$(tr -d '\r\n' < "$I4J/license.txt")
elif [[ -s "$ROOT/packaging/signpath/install4j-license.txt" ]]; then
  license=$(tr -d '\r\n' < "$ROOT/packaging/signpath/install4j-license.txt")
elif [[ -s "$SP/install4j-license.txt" ]]; then
  license=$(tr -d '\r\n' < "$SP/install4j-license.txt")
fi
if [[ -n "$license" ]]; then
  extra+=(--license="$license")
fi

p12="$SP/macos-developer-id-application.p12"
p8="$SP/AuthKey.p8"
issuer_file="$SP/apple-issuer-id.txt"
key_file="$SP/apple-key-id.txt"

if [[ -s "$p12" ]]; then
  pw="${JAER_MAC_KEYSTORE_PASSWORD:-}"
  if [[ -z "$pw" && -s "$SP/macos-p12-password.txt" ]]; then
    pw=$(tr -d '\r\n' < "$SP/macos-p12-password.txt")
  fi
  if [[ -z "$pw" ]]; then
    echo "install4jc cannot prompt for the .p12 password under Ant (no TTY)." >&2
    echo "Write it to signpath/macos-p12-password.txt (one line) or export JAER_MAC_KEYSTORE_PASSWORD." >&2
    echo "Do not paste the password into chat." >&2
    exit 1
  fi
  extra+=(--mac-keystore-password="$pw")
  if [[ -e "$p8" && -s "$issuer_file" && -s "$key_file" ]]; then
    extra+=(-D "appleIssuerId=$(tr -d '[:space:]' < "$issuer_file")")
    extra+=(-D "appleKeyId=$(tr -d '[:space:]' < "$key_file")")
  else
    extra+=(--disable-notarization)
  fi
else
  extra+=(--disable-signing --disable-notarization)
fi

exec install4jc "${extra[@]}" "$@"
