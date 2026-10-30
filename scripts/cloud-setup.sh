#!/bin/bash
# Paste this into the cloud environment's "Setup script" box (OWNER_TASKS.md #6).
# Installs the Android SDK on the cloud VM so sessions can build, test and lint. Needs dl.google.com allowed.
set -euo pipefail

SDK=/root/android-sdk
TOOLS=commandlinetools-linux-16111833_latest.zip

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    mkdir -p "$SDK/cmdline-tools"
    curl -fsSL -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/$TOOLS"
    unzip -q /tmp/cmdline-tools.zip -d "$SDK/cmdline-tools"
    mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm /tmp/cmdline-tools.zip
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses > /dev/null || true
"$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "platforms;android-37" "build-tools;36.0.0" > /dev/null
