#!/bin/bash
# Build and run RelaySFTP
set -e

cd "$(dirname "$0")/.."
PROJECT_DIR="$(pwd)"

./gradlew clean build -x test -x uiTest -x uiTestRetry

# Dev runs use an isolated data folder so they never touch the real
# ~/Library/Application Support/RelaySFTP (bookmarks, favorites, etc.).
DEV_DATA_DIR="$PROJECT_DIR/build/dev-data"
mkdir -p "$DEV_DATA_DIR"

exec java \
    -Xdock:name="RelaySFTP" \
    -Xdock:icon="$PROJECT_DIR/src/main/resources/I8LN/relaysftp.icns" \
    --enable-native-access=ALL-UNNAMED \
    -Dapple.awt.application.appearance=system \
    -Drelaysftp.dataDir="$DEV_DATA_DIR" \
    -jar "$PROJECT_DIR/build/libs/RelaySFTP.jar"
