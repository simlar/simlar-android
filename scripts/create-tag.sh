#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r USAGE="Usage example: $0 1 0 0 [master]"

declare -ri VERSION_MAJOR=${1?${USAGE}}
declare -ri VERSION_MINOR=${2?${USAGE}}
declare -ri VERSION_BUGFIX=${3?${USAGE}}
declare -r  BRANCH=${4:-"master"}

declare -r BUILD_SCRIPT="$(dirname $(readlink -f $0))/build-for-upload.sh"
declare -r UPDATE_MANIFEST_SCRIPT="$(dirname $(readlink -f $0))/update-android-manifest.sh"
declare -r ANDROID_MANIFEST="$(dirname $(readlink -f $0))/../app/src/main/AndroidManifest.xml"

declare -r SIMLAR_VERSION="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_BUGFIX}"
echo "creating tag: '${SIMLAR_VERSION}'"

if ! git diff --quiet ; then
	git status
	echo "git is dirty => aborting"
	exit 1
fi

git checkout "${BRANCH}"
git fetch
git fetch --tags
git pull --rebase origin "${BRANCH}"

"${UPDATE_MANIFEST_SCRIPT}" "${ANDROID_MANIFEST}"

git add "${ANDROID_MANIFEST}"
git commit -m "[AndroidManifest] increased versionCode for release ${SIMLAR_VERSION}"
git push origin "${BRANCH}":"${BRANCH}"

if [ "${BRANCH}" != "master" ] ; then
	declare -r MANIFEST_COMMIT=$(git rev-parse --verify HEAD)
	git checkout master
	git pull --rebase
	git cherry-pick "${MANIFEST_COMMIT}"
	git push origin master
	git checkout "${BRANCH}"
fi

git tag -a "${SIMLAR_VERSION}" -m "Version: ${SIMLAR_VERSION}"
git push origin "${SIMLAR_VERSION}"
git checkout "${SIMLAR_VERSION}"

"${BUILD_SCRIPT}"

echo "you may now publish simlar.apk at:"
echo "  https://play.google.com/apps/publish/"
echo
