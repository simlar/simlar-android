#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r BUILD_SCRIPT="$(dirname $(readlink -f $0))/build-for-upload.sh"
declare -r UPDATE_MANIFEST_SCRIPT="$(dirname $(readlink -f $0))/update-android-manifest.sh"
declare -r ANDROID_MANIFEST="$(dirname $(readlink -f $0))/AndroidManifest.xml"

declare -r SIMLAR_VERSION_CODE="0.0.1 alpha $(date +'%Y-%m-%d')"
declare -r SIMLAR_VERSION_TAG=$(echo "${SIMLAR_VERSION_CODE}" | sed "s/[[:space:]]/_/g")

git checkout master
git fetch
git fetch --tags
git pull --rebase

"${UPDATE_MANIFEST_SCRIPT}" "${SIMLAR_VERSION_CODE}" "${ANDROID_MANIFEST}"

git add "${ANDROID_MANIFEST}"
git commit -m "[Release] Version: ${SIMLAR_VERSION_CODE}"
git push

git tag -a "${SIMLAR_VERSION_TAG}" -m "Version: 0.0.1 alpha 2013-09-17"
git push origin "${SIMLAR_VERSION_TAG}"
git checkout "${SIMLAR_VERSION_TAG}"

"${BUILD_SCRIPT}"

echo "you may now publish simlar.apk at:"
echo "  https://play.google.com/apps/publish/"
echo
