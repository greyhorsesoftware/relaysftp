#!/bin/bash
#
# reset-app-data.sh - wipe ALL RelaySFTP data stores so the next launch behaves
# like a brand-new install. For testers.
#
# Removes:
#   1. The data folder        (bookmarks, favorites, transfer filters, logs)
#   2. Java preferences       (settings) - ONLY RelaySFTP's
#                             node; other Java apps' preferences are untouched
#   3. Keychain entry         (saved site passwords; macOS only)
#   4. macOS window state     (saved application state; best-effort)
#
# Usage: ./reset-app-data.sh [-y]
#   -y   skip the confirmation prompt

set -euo pipefail

FORCE=0
[ "${1:-}" = "-y" ] && FORCE=1

OS="$(uname -s)"

# --- refuse to run while the app is open (it would rewrite stores on quit) ---
if pgrep -f "RelaySFTP" >/dev/null 2>&1 || pgrep -f "com.gideonsoftworks.Gideon" >/dev/null 2>&1; then
    echo "RelaySFTP appears to be running - quit it first, then re-run this script." >&2
    exit 1
fi

# --- resolve per-OS locations ---
case "$OS" in
    Darwin)
        DATA_DIR="$HOME/Library/Application Support/RelaySFTP"
        ;;
    Linux)
        DATA_DIR="$HOME/.relaysftp"
        ;;
    *)
        echo "Unsupported OS: $OS (on Windows, delete %USERPROFILE%\\RelaySFTP and the" >&2
        echo "HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs\\com\\gideonsoftworks registry key)" >&2
        exit 1
        ;;
esac

echo "This will delete ALL RelaySFTP data on this machine:"
echo "  - Data folder:   $DATA_DIR"
echo "  - Preferences:   RelaySFTP settings"
[ "$OS" = "Darwin" ] && echo "  - Keychain:      saved site passwords (service \"RelaySFTP\")"
echo
if [ "$FORCE" -ne 1 ]; then
    read -r -p "Continue? [y/N] " reply
    case "$reply" in [Yy]*) ;; *) echo "Aborted."; exit 0 ;; esac
fi

# --- 1. data folder (bookmarks, logs, ...) ---
if [ -d "$DATA_DIR" ]; then
    rm -rf "$DATA_DIR"
    echo "Removed data folder: $DATA_DIR"
else
    echo "Data folder not present (already clean): $DATA_DIR"
fi

# --- 2. java.util.prefs node (settings) ---
# The prefs store is SHARED by all Java apps - delete only our node.
if [ "$OS" = "Darwin" ]; then
    PLIST="$HOME/Library/Preferences/com.apple.java.util.prefs.plist"
    if [ -f "$PLIST" ] && /usr/libexec/PlistBuddy -c "Print :\/:com\/:gideonsoftworks\/" "$PLIST" >/dev/null 2>&1; then
        /usr/libexec/PlistBuddy -c "Delete :\/:com\/:gideonsoftworks\/" "$PLIST"
        # cfprefsd caches the plist - kill it so the deletion isn't resurrected
        killall cfprefsd 2>/dev/null || true
        echo "Removed Java preferences node: com.gideonsoftworks"
    else
        echo "Java preferences node not present (already clean)"
    fi
else
    PREFS_DIR="$HOME/.java/.userPrefs/com/gideonsoftworks"
    if [ -d "$PREFS_DIR" ]; then
        rm -rf "$PREFS_DIR"
        echo "Removed Java preferences: $PREFS_DIR"
    else
        echo "Java preferences not present (already clean)"
    fi
fi

# --- 3. keychain entry (saved site passwords) ---
if [ "$OS" = "Darwin" ]; then
    if security delete-generic-password -s "RelaySFTP" -a "passwords" >/dev/null 2>&1; then
        echo "Removed Keychain entry: RelaySFTP/passwords"
    else
        echo "Keychain entry not present (already clean)"
    fi
fi

# --- 4. macOS saved window state (best-effort) ---
if [ "$OS" = "Darwin" ]; then
    for state in "$HOME/Library/Saved Application State/"*[Gg]ideon*.savedState \
                 "$HOME/Library/Saved Application State/"*RelaySFTP*.savedState; do
        if [ -e "$state" ]; then
            rm -rf "$state"
            echo "Removed saved window state: $(basename "$state")"
        fi
    done
fi

echo
echo "Done. The next RelaySFTP launch will behave like a fresh install"
echo "(first-run defaults, no bookmarks or saved passwords)."
