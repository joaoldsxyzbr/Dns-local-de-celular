#!/usr/bin/env sh
# On-demand JVM check; no polling or CI.
set -eu
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
: "${JAVA:=java}"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT HUP INT TERM
"$KOTLINC" \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsTcpPacketCodec.kt \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsPacketCodec.kt \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsResponseValidator.kt \
  tools/DnsTcpPacketCodecSmoke.kt -include-runtime -d "$dir/tcp-smoke.jar"
"$JAVA" -jar "$dir/tcp-smoke.jar"
