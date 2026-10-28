#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
: "${JAVA:=java}"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT HUP INT TERM
"$KOTLINC" \
  app/src/main/java/br/com/joaolds/dnslocal/dns/*.kt \
  app/src/main/java/br/com/joaolds/dnslocal/blocklist/DomainBlocklist.kt \
  tools/DnsPacketCodecSmoke.kt tools/DnsUpstreamClientSmoke.kt tools/DnsTunnelProcessorSmoke.kt tools/DnsFailoverResolverSmoke.kt tools/DnsPacketTestFixtures.kt tools/DnsPacketIntegritySmoke.kt tools/DnsResponseValidatorSmoke.kt \
  -include-runtime -d "$dir/dns-core-smoke.jar"
"$JAVA" -jar "$dir/dns-core-smoke.jar"
