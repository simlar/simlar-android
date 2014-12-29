#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r  BUILD_DIR=${1:?"Please give liblinphone dir as first parameter"}
declare -r  GIT_HASH=${2:-"unknown"}

declare -rx ANDROID_NDK=${ANDROID_NDK:-""}
declare -rx ANDROID_SDK=${ANDROID_SDK:-""}

if [[ -z "${ANDROID_NDK}" || -z "${ANDROID_SDK}" ]] ; then
	echo "ERROR: Please declare ANDROID_NDK and ANDROID_SDK, e.g. by:"
	echo "  export ANDROID_NDK=~/dev/android/android-ndk/android-ndk-r8e/"
	echo "  export ANDROID_SDK=~/dev/android/android-sdk/adt-bundle-linux-x86_64-20130514/sdk/"
	echo "aborting"
	exit 1
fi

declare -rx PATH=${PATH}:${ANDROID_SDK}/tools:${ANDROID_SDK}/platform-tools:${ANDROID_NDK}

cd "${BUILD_DIR}/linphone-android"

rm -f liblinphone-sdk-*.zip

make BUILD_GPLV3_ZRTP=1
make liblinphone-android-sdk
cd ../..

unzip -o $(find "${BUILD_DIR}/linphone-android" -maxdepth 1 -name liblinphone-android-sdk\*.zip)

echo "liblinphone build successfull with git hash: ${GIT_HASH}"
