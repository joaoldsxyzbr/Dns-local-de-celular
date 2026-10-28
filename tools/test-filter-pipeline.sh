#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
: "${JAVA:=java}"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT HUP INT TERM
sources="app/src/main/java/br/com/joaolds/dnslocal"
"$KOTLINC" "$sources/blocklist/DomainBlocklist.kt" \
    "$sources/blocklist/HaGeZiListStore.kt" \
    "$sources/dns/DnsDomainFilter.kt" \
    tools/DnsDomainFilterSmoke.kt -include-runtime -d "$dir/filter.jar"
"$JAVA" -cp "$dir/filter.jar" br.com.joaolds.dnslocal.dns.DnsDomainFilterSmokeKt
"$KOTLINC" "$sources/blocklist/DomainBlocklist.kt" \
    "$sources/blocklist/HaGeZiListStore.kt" \
    tools/HaGeZiListStoreSmoke.kt -include-runtime -d "$dir/store.jar"
"$JAVA" -cp "$dir/store.jar" br.com.joaolds.dnslocal.blocklist.HaGeZiListStoreSmokeKt
"$KOTLINC" "$sources/blocklist/DomainBlocklist.kt" \
    "$sources/dns/DnsPacketCodec.kt" \
    "$sources/dns/DnsTunnelProcessor.kt" \
    "$sources/dns/DnsDomainFilter.kt" \
    tools/DnsPacketTestFixtures.kt tools/DnsDomainTunnelSmoke.kt -include-runtime -d "$dir/tunnel-filter.jar"
"$JAVA" -cp "$dir/tunnel-filter.jar" br.com.joaolds.dnslocal.dns.DnsDomainTunnelSmokeKt
