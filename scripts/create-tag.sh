#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r USAGE="Usage example: $0 1 0 0 [master]"

declare -ri VERSION_MAJOR=${1?${USAGE}}
declare -ri VERSION_MINOR=${2?${USAGE}}
declare -ri VERSION_BUGFIX=${3?${USAGE}}
declare -r  BRANCH=${4:-"master"}

declare -r BUILD_SCRIPT="$(dirname $(readlink -f $0))/build-for-upload.sh"
declare -r UPDATE_VERSION_CODE_SCRIPT="$(dirname $(readlink -f $0))/update-build-gradle-versionCode.sh"
declare -r APP_BUILD_GRADLE="$(dirname $(readlink -f $0))/../app/build.gradle"

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

"${UPDATE_VERSION_CODE_SCRIPT}"

git add "${APP_BUILD_GRADLE}"
git commit -S -m "[gradle] increased versionCode for release ${SIMLAR_VERSION}"
git push origin "${BRANCH}":"${BRANCH}"

if [ "${BRANCH}" != "master" ] ; then
	declare -r VERSION_CODE_COMMIT=$(git rev-parse --verify HEAD)
	git checkout master
	git pull --rebase
	git cherry-pick "${VERSION_CODE_COMMIT}"
	git push origin master
	git checkout "${BRANCH}"
fi

git tag -s "${SIMLAR_VERSION}" -m "Version: ${SIMLAR_VERSION}"
git push origin "${SIMLAR_VERSION}"
git checkout "${SIMLAR_VERSION}"

echo "you may now run:"
echo "  ${BUILD_SCRIPT}"
echo
