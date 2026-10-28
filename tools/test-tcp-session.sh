#!/usr/bin/env sh
# Run once on demand; no polling, background service or CI.
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
  tools/DnsTcpSessionSmoke.kt -include-runtime -d "$dir/tcp-session.jar"
"$JAVA" -jar "$dir/tcp-session.jar"
