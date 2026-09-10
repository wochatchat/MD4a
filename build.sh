#!/usr/bin/env bash
# MD4a build script
#   ./build.sh          → build release APK, bump version
#   ./build.sh --no-bump → build without incrementing version
#
# Archived APK path: apk/md4a-<versionName>.apk (kept out of Git; CI uploads
# it to the GitHub Release and as a workflow artifact).

set -euo pipefail
cd "$(dirname "$0")"

VERSION_FILE="version.properties"
APK_DIR="app/build/outputs/apk/release"
APK_ARCHIVE="apk"

BUMP=true
if [ "${1:-}" = "--no-bump" ]; then BUMP=false; fi

if [ "$BUMP" = true ] && [ -f "$VERSION_FILE" ]; then
    CODE=$(grep '^versionCode=' "$VERSION_FILE" | cut -d= -f2)
    NAME=$(grep '^versionName=' "$VERSION_FILE" | cut -d= -f2)
    NEW_CODE=$((CODE + 1))
    IFS='.' read -r MAJOR MINOR PATCH <<< "$NAME"
    NEW_PATCH=$((PATCH + 1))
    if [ "$NEW_PATCH" -ge 100 ]; then
        NEW_MINOR=$((MINOR + 1)); NEW_PATCH=0
    else
        NEW_MINOR=$MINOR
    fi
    NEW_NAME="$MAJOR.$NEW_MINOR.$NEW_PATCH"
    cat > "$VERSION_FILE" << EOF
# Auto-managed by build.sh — do not edit manually
versionCode=$NEW_CODE
versionName=$NEW_NAME
EOF
    echo "Version: $CODE -> $NEW_CODE ($NAME -> $NEW_NAME)"
fi

echo "Building release APK..."
./gradlew clean :app:assembleRelease --no-daemon

mkdir -p "$APK_ARCHIVE"
NEW_APK="$APK_ARCHIVE/md4a-$(grep '^versionName=' "$VERSION_FILE" | cut -d= -f2).apk"
cp "$APK_DIR/app-release.apk" "$NEW_APK"
echo "APK archived to $NEW_APK"

# Prune old versioned APKs, keeping only the newest two
ls -1 "$APK_ARCHIVE"/md4a-*.apk 2>/dev/null | sort -rV | tail -n +3 | while read -r apk; do
    rm -f "$apk"
    echo "Pruned old APK: $apk"
done
