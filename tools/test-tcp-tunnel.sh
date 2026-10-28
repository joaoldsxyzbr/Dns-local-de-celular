#!/usr/bin/env sh
# JVM tests executed on demand only. Never starts CI, polling or background jobs.
set -eu
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
: "${JAVA:=java}"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT HUP INT TERM
"$KOTLINC" \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsPacketCodec.kt \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsResponseValidator.kt \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsTcpPacketCodec.kt \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsTcpMessageFramer.kt \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsTcpSession.kt \
  app/src/main/java/br/com/joaolds/dnslocal/dns/DnsTcpTunnelProcessor.kt \
  tools/DnsTcpTunnelProcessorSmoke.kt -include-runtime -d "$dir/tcp-tunnel.jar"
"$JAVA" -jar "$dir/tcp-tunnel.jar"
