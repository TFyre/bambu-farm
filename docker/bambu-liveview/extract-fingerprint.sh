#!/usr/bin/env bash
#
# Prints the SHA-256 fingerprint of a Bambu printer's RTSPS certificate.
#
# This is the value mediamtx needs in MTX_PATHS_<NAME>_SOURCEFINGERPRINT. The
# printers use self-signed certificates, and mediamtx falls back to normal CA
# verification when the fingerprint is empty - so every printer needs one.
#
# Output is the plain lowercase hex digest, no colons and no "sha256:" prefix,
# which is exactly what mediamtx compares against (see
# internal/protocols/tls/tls_config.go).
#
# usage: ./extract-fingerprint.sh <printer-ip> [port]
#   e.g. ./extract-fingerprint.sh 192.168.1.50

set -euo pipefail

host="${1:-}"
port="${2:-322}"

if [[ -z "$host" ]]; then
    echo "usage: $0 <printer-ip> [port]" >&2
    exit 2
fi

if ! command -v openssl >/dev/null 2>&1; then
    echo "$0: openssl not found in PATH" >&2
    exit 1
fi

# -servername sends SNI (harmless if the printer ignores it).
# </dev/null makes s_client exit as soon as the handshake completes.
# The awk takes only the FIRST certificate block, so a chain doesn't confuse
# openssl x509 further down.
cert="$(
    openssl s_client -connect "${host}:${port}" -servername "${host}" </dev/null 2>/dev/null \
        | awk '/BEGIN CERTIFICATE/{f=1} f{print} /END CERTIFICATE/{exit}' \
        || true
)"

if [[ -z "$cert" ]]; then
    echo "$0: no certificate returned by ${host}:${port}" >&2
    exit 1
fi

# 2>/dev/null hides openssl's "reading certificate from stdin" notice; a real
# failure still surfaces as a non-zero exit through pipefail.
printf '%s\n' "$cert" \
    | openssl x509 -noout -fingerprint -sha256 2>/dev/null \
    | cut -d= -f2 \
    | tr -d ':' \
    | tr 'A-F' 'a-f'
