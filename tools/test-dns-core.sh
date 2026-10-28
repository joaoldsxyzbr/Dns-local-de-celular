#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
: "${JAVA:=java}"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT HUP INT TERM
"$KOTLINC" \
  app/src/main/java/br/com/joaolds/dnslocal/dns/*.kt \
  tools/DnsPacketCodecSmoke.kt tools/DnsUpstreamClientSmoke.kt \
  -include-runtime -d "$dir/dns-core-smoke.jar"
"$JAVA" -jar "$dir/dns-core-smoke.jar"
