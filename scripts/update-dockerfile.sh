#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r SDK_MANAGER=${SDK_MANAGER:-"${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"}

declare -r GSED=$(which gsed)
declare -r SED=${GSED:-"$(which sed)"}

declare -r GREADLINK=$(which greadlink)
declare -r READLINK=${GREADLINK:-"$(which readlink)"}

declare -r PROJECT_DIR="$(dirname $(${READLINK} -f $0))/.."
declare -r DOCKERFILE="${PROJECT_DIR}/docker-files/Dockerfile"

function latestVersion() {
  local -r PACKAGE=$1
  "${SDK_MANAGER}" --list | "${SED}" -n "s/^[[:space:]]*${PACKAGE}\([^[:space:]]*\).*/\1/p" | sort --human-numeric-sort | uniq | tail -1
}

function updateDockerfile() {
  local -r PACKAGE=$1
  local -r VERSION=$2
  "${SED}" -i "s/ENV ${PACKAGE} .*/ENV ${PACKAGE} \"${VERSION}\"/" "${DOCKERFILE}"
}

declare -r ANDROID_SDK_VERSION=$(latestVersion "platforms;android-")
declare -r ANDROID_BUILD_TOOLS_VERSION=$(latestVersion "build-tools;")
declare -r ANDROID_NDK_VERSION=$(latestVersion "ndk;")

echo "android sdk: ${ANDROID_SDK_VERSION}"
echo "android build tools: ${ANDROID_BUILD_TOOLS_VERSION}"
echo "android ndk: ${ANDROID_NDK_VERSION}"

updateDockerfile "ANDROID_SDK_VERSION" "${ANDROID_SDK_VERSION}"
updateDockerfile "ANDROID_BUILD_TOOLS_VERSION" "${ANDROID_BUILD_TOOLS_VERSION}"
updateDockerfile "ANDROID_NDK_VERSION" "${ANDROID_NDK_VERSION}"
