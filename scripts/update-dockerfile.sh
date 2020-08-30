#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r SDK_MANAGER=${SDK_MANAGER:-"${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"}

declare -r GSED=$(which gsed)
declare -r SED=${GSED:-"$(which sed)"}

function latestVersion() {
  local -r PACKAGE=$1
  "${SDK_MANAGER}" --list | "${SED}" -n "s/^[[:space:]]*${PACKAGE}\([^[:space:]]*\).*/\1/p" | sort --human-numeric-sort | uniq | tail -1
}

declare -r ANDROID_SDK_VERSION=$(latestVersion "platforms;android-")
declare -r ANDROID_BUILD_TOOLS_VERSION=$(latestVersion "build-tools;")
declare -r ANDROID_NDK_VERSION=$(latestVersion "ndk;")

echo "android sdk: ${ANDROID_SDK_VERSION}"
echo "android build tools: ${ANDROID_BUILD_TOOLS_VERSION}"
echo "android ndk: ${ANDROID_NDK_VERSION}"

