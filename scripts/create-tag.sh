#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r USAGE="Usage example: $0 1 0 0 [master]"

declare -ri VERSION_MAJOR=${1?${USAGE}}
declare -ri VERSION_MINOR=${2?${USAGE}}
declare -ri VERSION_BUGFIX=${3?${USAGE}}
declare -r  BRANCH=${4:-"master"}

declare -r PROJECT_DIR="$(dirname $(readlink -f $0))/.."
declare -r BUILD_SCRIPT="${PROJECT_DIR}/scripts/build-and-publish.sh"
declare -r RUN_WITH_DECRYPTED_PLAYSTORE_CREDENTIALS="${PROJECT_DIR}/scripts/run-with-decrypted-play-store-credentials.sh"

declare -r SIMLAR_VERSION="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_BUGFIX}"
echo "creating tag: '${SIMLAR_VERSION}'"
declare -r COMMENT=${COMMENT:-"Version: ${SIMLAR_VERSION}"}

if ! git diff --quiet ; then
	git status
	echo "git is dirty => aborting"
	exit 1
fi

git fetch
git fetch --tags
git checkout "${BRANCH}"
git reset "origin/${BRANCH}" --hard

git tag -s "${SIMLAR_VERSION}" -m "${COMMENT}"
git push origin "${SIMLAR_VERSION}"
git checkout "${SIMLAR_VERSION}"

echo "you may now run:"
echo "  ${RUN_WITH_DECRYPTED_PLAYSTORE_CREDENTIALS} ${BUILD_SCRIPT}"
echo
