#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r USAGE="Usage example: $0 1 0 0"

declare -ri VERSION_MAJOR=${1?${USAGE}}
declare -ri VERSION_MINOR=${2?${USAGE}}
declare -ri VERSION_BUGFIX=${3?${USAGE}}

declare -r BUILD_SCRIPT="$(dirname $(readlink -f $0))/build-for-upload.sh"
declare -r UPDATE_MANIFEST_SCRIPT="$(dirname $(readlink -f $0))/update-android-manifest.sh"
declare -r ANDROID_MANIFEST="$(dirname $(readlink -f $0))/AndroidManifest.xml"

declare -r SIMLAR_VERSION="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_BUGFIX}"
echo "creating tag: '${SIMLAR_VERSION}'"

if ! git diff --quiet ; then
	git status
	echo "git is dirty => aborting"
	exit 1
fi

git checkout master
git fetch
git fetch --tags
git pull --rebase

"${UPDATE_MANIFEST_SCRIPT}" "${SIMLAR_VERSION}" "${ANDROID_MANIFEST}"

git add "${ANDROID_MANIFEST}"
git commit -m "[Release] Version: ${SIMLAR_VERSION}"
git push

git tag -a "${SIMLAR_VERSION}" -m "Version: ${SIMLAR_VERSION}"
git push origin "${SIMLAR_VERSION}"
git checkout "${SIMLAR_VERSION}"

"${BUILD_SCRIPT}"

echo "you may now publish simlar.apk at:"
echo "  https://play.google.com/apps/publish/"
echo
