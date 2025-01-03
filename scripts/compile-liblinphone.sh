#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r  BUILD_DIR=${1:?"Please give liblinphone dir as first parameter"}
declare -r  VERSION=${2:?"Please specify version as second parameter"}

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

cd "${BUILD_DIR}/linphone-sdk"

declare -r CMAKE_BUILD_DIR="${BUILD_DIR}/linphone-sdk/build-android_$(date '+%Y%m%d_%H%M%S')"
mkdir "${CMAKE_BUILD_DIR}"
touch "${CMAKE_BUILD_DIR}/settings.gradle"

cmake  \
	--preset=android-sdk \
	-B "${CMAKE_BUILD_DIR}" \
	-DLINPHONESDK_ANDROID_ARCHS=armv7,arm64,x86,x86_64 \
	-DENABLE_AAUDIO=OFF \
	-DENABLE_GPL_THIRD_PARTIES=ON \
	-DENABLE_NON_FREE_FEATURES=ON \
	-DENABLE_PQCRYPTO=ON \
	-DENABLE_GSM=OFF \
	-DENABLE_ILBC=OFF \
	-DENABLE_ISAC=OFF \
	-DENABLE_MKV=OFF \
	-DENABLE_VCARD=OFF

cmake --build "${CMAKE_BUILD_DIR}"

cd "${PROJECT_DIR}"
rm -rf "app/libs/linphone-sdk/${VERSION}"

mkdir -p "app/libs/linphone-sdk/${VERSION}"
unzip -o $(find "${CMAKE_BUILD_DIR}/linphone-sdk/bin/distributions" -maxdepth 1 -name linphone-sdk-android\*.zip -and -not -name \*debug\*) -d "app/libs/linphone-sdk/${VERSION}"

echo "liblinphone build successfull"
