#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r  BUILD_DIR=${1:?"Please give liblinphone dir as first parameter"}
declare -r  GIT_HASH=${2:-"unknown"}

declare -r PROJECT_DIR="$(dirname $(readlink -f $0))/.."


declare -rx ANDROID_NDK=${ANDROID_NDK:-""}
declare -rx ANDROID_HOME=${ANDROID_HOME:-""}

if [[ -z "${ANDROID_NDK}" || -z "${ANDROID_HOME}" ]] ; then
	echo "ERROR: Please declare ANDROID_NDK and ANDROID_HOME, e.g. by:"
	echo "  export ANDROID_NDK=~/dev/android/android-ndk/android-ndk-r8e/"
	echo "  export ANDROID_HOME=~/Android/Sdk/"
	echo "aborting"
	exit 1
fi

declare -rx PATH=${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${ANDROID_NDK}


declare -r CMAKE_BUILD_DIR="${BUILD_DIR}/linphone-sdk-build_$(date '+%Y%m%d_%H%M%S')"
mkdir "${CMAKE_BUILD_DIR}"
cd "${CMAKE_BUILD_DIR}"
touch settings.gradle

cmake "${BUILD_DIR}/linphone-sdk" \
	-DLINPHONESDK_PLATFORM=Android \
	-DLINPHONESDK_ANDROID_ARCHS=armv7,arm64,x86,x86_64 \
	-DENABLE_AAUDIO=OFF \
	-DENABLE_GPL_THIRD_PARTIES=ON \
	-DENABLE_PQCRYPTO=ON \
	-DENABLE_GSM=OFF \
	-DENABLE_ILBC=OFF \
	-DENABLE_ISAC=OFF \
	-DENABLE_MKV=OFF \
	-DENABLE_VCARD=OFF

cmake --build .


cd "${PROJECT_DIR}"
rm -rf app/src/main/jniLibs/
rm -rf app/libs/

mkdir app/libs/
unzip -o $(find "${CMAKE_BUILD_DIR}/linphone-sdk/bin/distributions" -maxdepth 1 -name linphone-sdk-android\*.zip) -d app/libs/

echo "liblinphone build successfull with git hash: ${GIT_HASH}"
