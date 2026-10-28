#!/usr/bin/env sh
# Local opt-in preflight only; does not start CI, install an APK, or deploy.
set -eu
cd "$(dirname "$0")/.."
python3 tools/check-android-structure.py
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
    echo "Android SDK not configured: set ANDROID_HOME or ANDROID_SDK_ROOT, or create a local.properties (never commit it)." >&2
    echo "Android build NOT EXECUTED. Use Android Studio with installed Android API 36." >&2
    exit 2
fi
if ! command -v java >/dev/null 2>&1; then
    echo "Java/JDK not available. Install JDK 17 or newer." >&2
    exit 2
fi
sh ./gradlew --no-daemon :app:assembleDebug
test -s app/build/outputs/apk/debug/app-debug.apk
echo "APK debug generated locally: app/build/outputs/apk/debug/app-debug.apk"
