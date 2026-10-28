#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
: "${JAVA:=java}"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT HUP INT TERM
"$KOTLINC" \
  app/src/main/java/br/com/joaolds/dnslocal/blocklist/DomainBlocklist.kt \
  tools/DomainBlocklistSmoke.kt tools/DomainBlocklistNormalizationSmoke.kt -include-runtime -d "$dir/blocklist-smoke.jar"
"$JAVA" -jar "$dir/blocklist-smoke.jar"
