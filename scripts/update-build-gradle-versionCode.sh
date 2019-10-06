#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r PROJECT_DIR="$(dirname $(readlink -f $0))/.."
declare -r APP_BUILD_GRADLE=${APP_BUILD_GRADLE:-"${PROJECT_DIR}/app/build.gradle"}

## increment versionCode
declare -ri CURRENT_VERSION_CODE=$(sed -n "s/^[[:space:]]*versionCode[[:space:]]*\([[:digit:]]*\)$/\1/p" "${APP_BUILD_GRADLE}")
declare -ri VERSION_CODE=$((1+${CURRENT_VERSION_CODE}))

echo "update '${APP_BUILD_GRADLE}' to new versionCode ${VERSION_CODE}"
sed -i "s/\(^[[:space:]]*versionCode[[:space:]]*\)[[:digit:]]*$/\1${VERSION_CODE}/" "${APP_BUILD_GRADLE}"
