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

./prepare.py -C
./prepare.py \
	-DENABLE_OPENH264=OFF \
	-DENABLE_AMRNB=OFF \
	-DENABLE_H263=OFF \
	-DENABLE_RTP_MAP_ALWAYS_IN_SDP=OFF \
	-DENABLE_BV16=OFF \
	-DENABLE_PACKAGING=OFF \
	-DENABLE_AMRWB=OFF \
	-DENABLE_DTLS=ON \
	-DENABLE_GSM=ON \
	-DENABLE_DEBUG_LOGS=OFF \
	-DENABLE_MBEDTLS=ON \
	-DENABLE_GPL_THIRD_PARTIES=ON \
	-DENABLE_ILBC=ON \
	-DENABLE_OPUS=ON \
	-DENABLE_DOC=ON \
	-DENABLE_ISAC=ON \
	-DENABLE_SRTP=ON \
	-DENABLE_G729=OFF \
	-DENABLE_VCARD=ON \
	-DENABLE_AMR=OFF \
	-DENABLE_SILK=ON \
	-DENABLE_RELATIVE_PREFIX=OFF \
	-DENABLE_X264=OFF \
	-DENABLE_H263P=OFF \
	-DENABLE_VIDEO=ON \
	-DENABLE_PCAP=OFF \
	-DENABLE_POLARSSL=OFF \
	-DENABLE_FFMPEG=ON \
	-DENABLE_UNIT_TESTS=ON \
	-DENABLE_NON_FREE_CODECS=OFF \
	-DENABLE_ZRTP=ON \
	-DENABLE_CODEC2=OFF \
	-DENABLE_WEBRTC_AEC=ON \
	-DENABLE_MKV=ON \
	-DENABLE_TUNNEL=OFF \
	-DENABLE_VPX=ON \
	-DENABLE_SPEEX=ON \
	-DENABLE_NLS=ON \
	-DENABLE_MPEG4=OFF

make
make liblinphone-android-sdk
cd ../../../..

unzip -o $(find "${BUILD_DIR}/linphone-android" -maxdepth 1 -name liblinphone-android-sdk\*.zip)

## Android Studio
mv libs/*.jar app/libs/
rm -rf app/src/main/jniLibs/
mv libs app/src/main/jniLibs

echo "liblinphone build successfull with git hash: ${GIT_HASH}"
