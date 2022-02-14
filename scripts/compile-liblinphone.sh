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
	-DENABLE_AMRNB=OFF \
	-DENABLE_AMRWB=OFF \
	-DENABLE_ARCH_SUFFIX=ON \
	-DENABLE_BV16=OFF \
	-DENABLE_CODEC2=OFF \
	-DENABLE_CSHARP_WRAPPER=OFF \
	-DENABLE_CXX_WRAPPER=OFF \
	-DENABLE_DEBUG_LOGS=OFF \
	-DENABLE_DOC=OFF \
	-DENABLE_FFMPEG=OFF \
	-DENABLE_G726=OFF \
	-DENABLE_G729B_CNG=OFF \
	-DENABLE_G729=OFF \
	-DENABLE_GPL_THIRD_PARTIES=ON \
	-DENABLE_GSM=OFF \
	-DENABLE_GTK_UI=OFF \
	-DENABLE_ILBC=OFF \
	-DENABLE_ISAC=OFF \
	-DENABLE_JAVA_WRAPPER=ON \
	-DENABLE_JPEG=ON \
	-DENABLE_LIME=ON \
	-DENABLE_LIME_X3DH=ON \
	-DENABLE_MBEDTLS=ON \
	-DENABLE_MDNS=OFF \
	-DENABLE_MKV=OFF \
	-DENABLE_NLS=NO \
	-DENABLE_NON_FREE_CODECS=OFF \
	-DENABLE_OPENH264=OFF \
	-DENABLE_OPUS=ON \
	-DENABLE_PCAP=OFF \
	-DENABLE_POLARSSL=OFF \
	-DENABLE_QRCODE=ON \
	-DENABLE_RELATIVE_PREFIX=OFF \
	-DENABLE_RTP_MAP_ALWAYS_IN_SDP=OFF \
	-DENABLE_SILK=OFF \
	-DENABLE_SPEEX=ON \
	-DENABLE_SRTP=ON \
	-DENABLE_TOOLS=OFF \
	-DENABLE_TUNNEL=OFF \
	-DENABLE_UNIT_TESTS=OFF \
	-DENABLE_UNMAINTAINED=OFF \
	-DENABLE_UPDATE_CHECK=OFF \
	-DENABLE_VCARD=OFF \
	-DENABLE_VIDEO=ON \
	-DENABLE_VPX=ON \
	-DENABLE_WEBRTC_AEC=ON \
	-DENABLE_WEBRTC_AECM=ON \
	-DENABLE_WEBRTC_VAD=OFF \
	-DENABLE_ZRTP=ON
cmake --build .


cd "${PROJECT_DIR}"
rm -rf app/src/main/jniLibs/
rm -rf app/libs/

mkdir app/libs/
unzip -o $(find "${CMAKE_BUILD_DIR}/linphone-sdk/bin/distributions" -maxdepth 1 -name linphone-sdk-android\*.zip) -d app/libs/

echo "liblinphone build successfull with git hash: ${GIT_HASH}"
