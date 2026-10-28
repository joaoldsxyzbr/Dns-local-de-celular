#!/usr/bin/env sh
# Single-run local JVM test; no network or periodic tasks.
set -eu
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
: "${JAVA:=java}"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT HUP INT TERM
"$KOTLINC" \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsTcpPacketCodec.kt \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsTcpMessageFramer.kt \
  tools/DnsTcpMessageFramerSmoke.kt -include-runtime -d "$dir/framer.jar"
"$JAVA" -jar "$dir/framer.jar"
