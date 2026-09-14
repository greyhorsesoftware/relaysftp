#!/bin/bash
# Build RelaySFTP native installer for the current platform
# macOS -> signed + notarized .dmg
# Windows -> .msi
# Linux -> .deb
set -e

cd "$(dirname "$0")/.."
PROJECT_DIR="$(pwd)"

# Single source of truth for the version (gradle.properties)
APP_VERSION="$(grep -E '^version=' "$PROJECT_DIR/gradle.properties" | head -1 | cut -d= -f2 | tr -d '[:space:]')"
echo "=== App version: $APP_VERSION ==="

echo "=== Building JAR ==="
# Packaging never runs tests (CI runs them in their own job first); exclude the UI tasks too,
# or `build` would drive the desktop with the robot for several minutes.
./gradlew clean build -x test -x uiTest -x uiTestRetry

echo "=== Building installer ==="
case "$(uname -s)" in
    Darwin)
        if [ -n "$MAC_SIGNING_IDENTITY" ]; then
            # Stage JARs
            echo "=== Staging JARs ==="
            ./gradlew copyDependencies copyAppJar

            # Sign native libraries inside JARs
            echo "=== Signing native libraries inside JARs ==="
            LIBS_DIR="$PROJECT_DIR/build/jpackage-libs"

            for JAR in "$LIBS_DIR"/*.jar; do
                [ -f "$JAR" ] || continue

                NATIVES=$(unzip -l "$JAR" 2>/dev/null | grep -E '\.(dylib|jnilib|so)$' | awk '{print $4}') || true
                [ -z "$NATIVES" ] && continue

                echo "  Signing natives in $(basename $JAR)..."
                TMPDIR_JAR=$(mktemp -d)

                pushd "$TMPDIR_JAR" > /dev/null
                unzip -q "$JAR"

                for NATIVE in $NATIVES; do
                    if [ -f "$NATIVE" ]; then
                        codesign --force --timestamp --sign "$MAC_SIGNING_IDENTITY" \
                            --options runtime "$NATIVE"
                    fi
                done

                rm "$JAR"
                zip -q -r "$JAR" .
                popd > /dev/null
                rm -rf "$TMPDIR_JAR"
            done

            # Build app-image first (not DMG — we'll use our own template)
            echo "=== Running jpackage (app-image) with signing ==="
            OUTPUT_DIR="$PROJECT_DIR/build/jpackage-out"
            mkdir -p "$OUTPUT_DIR"
            JAR_NAME=$(ls "$LIBS_DIR"/RelaySFTP*.jar | head -1 | xargs basename)

            jpackage \
                --input "$LIBS_DIR" \
                --dest "$OUTPUT_DIR" \
                --name RelaySFTP \
                --main-jar "$JAR_NAME" \
                --main-class com.gideonsoftworks.Gideon \
                --app-version "$APP_VERSION" \
                --vendor "Grey Horse Software" \
                --description "RelaySFTP - FTP/SFTP/S3 File Transfer Client" \
                --java-options "-Xmx512m" \
                --java-options "--enable-native-access=ALL-UNNAMED" \
                --java-options "-Dapple.awt.application.appearance=system" \
                --icon "$PROJECT_DIR/src/main/resources/I8LN/relaysftp.icns" \
                --type app-image \
                --mac-package-name RelaySFTP \
                --mac-sign \
                --mac-signing-key-user-name "$MAC_SIGNING_IDENTITY"

            # Build DMG from template
            echo "=== Building DMG from template ==="
            DMG_TEMPLATE="$PROJECT_DIR/dmg-template.dmg"
            DMG_RW="$OUTPUT_DIR/RelaySFTP-rw.dmg"
            DMG_FINAL="$OUTPUT_DIR/RelaySFTP-${APP_VERSION}.dmg"
            APP_IMAGE="$OUTPUT_DIR/RelaySFTP.app"
            MOUNT_POINT=$(mktemp -d)

            # Calculate needed size: app size + 50MB headroom
            APP_SIZE_KB=$(du -sk "$APP_IMAGE" | awk '{print $1}')
            DMG_SIZE_MB=$(( (APP_SIZE_KB / 1024) + 50 ))
            echo "  App size: ${APP_SIZE_KB}KB, DMG size: ${DMG_SIZE_MB}MB"

            # Convert template to read-write
            hdiutil convert "$DMG_TEMPLATE" -format UDRW -o "$DMG_RW"

            # Resize to fit the app
            hdiutil resize -size "${DMG_SIZE_MB}m" "$DMG_RW"

            # Mount and swap the app
            hdiutil attach "$DMG_RW" -mountpoint "$MOUNT_POINT" -nobrowse -noautoopen

            # Remove the placeholder app, copy the real one in
            rm -rf "$MOUNT_POINT"/RelaySFTP.app
            cp -R "$APP_IMAGE" "$MOUNT_POINT/"

            # Unmount
            hdiutil detach "$MOUNT_POINT"

            # Convert back to compressed read-only DMG
            rm -f "$DMG_FINAL"
            hdiutil convert "$DMG_RW" -format UDZO -imagekey zlib-level=9 -o "$DMG_FINAL"

            # Clean up
            rm -f "$DMG_RW"
            rm -rf "$OUTPUT_DIR/RelaySFTP.app"
            rmdir "$MOUNT_POINT" 2>/dev/null || true
        else
            echo "=== No signing identity set, building unsigned ==="
            ./gradlew jpackage -PjpackageType=dmg
        fi

        # Notarization
        if [ -n "$APPLE_ID" ] && [ -n "$APPLE_TEAM_ID" ] && [ -n "$APPLE_APP_PASSWORD" ]; then
            echo "=== Notarizing ==="
            DMG=$(find "$PROJECT_DIR/build/jpackage-out" -name "*.dmg" | head -1)
            if [ -n "$DMG" ]; then
                SUBMIT_OUTPUT=$(xcrun notarytool submit "$DMG" \
                    --apple-id "$APPLE_ID" \
                    --team-id "$APPLE_TEAM_ID" \
                    --password "$APPLE_APP_PASSWORD" \
                    --wait 2>&1)
                echo "$SUBMIT_OUTPUT"

                SUBMISSION_ID=$(echo "$SUBMIT_OUTPUT" | grep "id:" | head -1 | awk '{print $2}')
                STATUS=$(echo "$SUBMIT_OUTPUT" | grep "status:" | tail -1 | awk '{print $2}')

                if [ "$STATUS" != "Accepted" ]; then
                    echo "=== Notarization failed (status: $STATUS) ==="
                    if [ -n "$SUBMISSION_ID" ]; then
                        echo "=== Fetching notarization log ==="
                        xcrun notarytool log "$SUBMISSION_ID" \
                            --apple-id "$APPLE_ID" \
                            --team-id "$APPLE_TEAM_ID" \
                            --password "$APPLE_APP_PASSWORD" \
                            developer_log.json 2>&1 || true
                        cat developer_log.json 2>/dev/null || true
                    fi
                    exit 1
                fi

                echo "=== Stapling ==="
                xcrun stapler staple "$DMG"
            fi
        else
            echo "=== Skipping notarization (APPLE_ID, APPLE_TEAM_ID, or APPLE_APP_PASSWORD not set) ==="
        fi
        ;;
    MINGW*|MSYS*|CYGWIN*)
        ./gradlew jpackage -PjpackageType=msi
        ;;
    Linux)
        ./gradlew jpackage -PjpackageType=deb
        ;;
esac

echo ""
echo "=== Done ==="
ls -lh "$PROJECT_DIR/build/jpackage-out/"
