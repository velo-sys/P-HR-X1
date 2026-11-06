#!/usr/bin/env bash
# Baut die APK im Container (Gradle 8.10 / JDK 17). Ergebnis: P-HR-X1.apk
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_SDK:-$(cd .. && pwd)/.android-sdk}
GH=${GRADLE_CACHE:-$(cd .. && pwd)/.gradle-home}
APK=app/build/outputs/apk/release/app-release.apk
rm -f "$APK"
podman run --rm -v "$PWD:/proj:z" -v "$SDK:/sdk:z" -v "$GH:/gh:z" -w /proj \
  -e ANDROID_HOME=/sdk -e GRADLE_USER_HOME=/gh docker.io/library/gradle:8.10.2-jdk17 \
  gradle --no-daemon -q "${1:-assembleRelease}" 2>&1 | grep -v '^w: ' || true
if [ -f "$APK" ]; then cp "$APK" P-HR-X1.apk && ls -la P-HR-X1.apk; else echo "BUILD FEHLGESCHLAGEN" >&2; exit 1; fi
