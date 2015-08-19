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

rm -f liblinphone-android-sdk-*.zip

make BUILD_OPENH264=0 BUILD_AMRNB=0 BUILD_AMRWB=0 BUILD_SILK=0 BUILD_G729=0
make liblinphone-android-sdk
cd ../../../..

unzip -o $(find "${BUILD_DIR}/linphone-android" -maxdepth 1 -name liblinphone-android-sdk\*.zip)

## Android Studio
mv libs/*.jar app/libs/
rm -rf app/src/main/jniLibs/
mv libs app/src/main/jniLibs

echo "liblinphone build successfull with git hash: ${GIT_HASH}"
