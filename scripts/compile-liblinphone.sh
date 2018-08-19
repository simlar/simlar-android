#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r  BUILD_DIR=${1:?"Please give liblinphone dir as first parameter"}
declare -r  GIT_HASH=${2:-"unknown"}

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

cd "${BUILD_DIR}/linphone-android"

rm -f liblinphone-android-sdk-*.zip

./prepare.py -c
./prepare.py \
	-DENABLE_AMRNB=OFF \
	-DENABLE_AMRWB=OFF \
	-DENABLE_ARCH_SUFFIX=ON \
	-DENABLE_BV16=OFF \
	-DENABLE_CODEC2=OFF \
	-DENABLE_CSHARP_WRAPPER=OFF \
	-DENABLE_CXX_WRAPPER=OFF \
	-DENABLE_DEBUG_LOGS=OFF \
	-DENABLE_DOC=ON \
	-DENABLE_EMBEDDED_OPENH264=OFF \
	-DENABLE_FFMPEG=OFF \
	-DENABLE_G726=OFF \
	-DENABLE_G729B_CNG=OFF \
	-DENABLE_G729=OFF \
	-DENABLE_GPL_THIRD_PARTIES=ON \
	-DENABLE_GSM=OFF \
	-DENABLE_GTK_UI=OFF \
	-DENABLE_H263=OFF \
	-DENABLE_H263P=OFF \
	-DENABLE_ILBC=OFF \
	-DENABLE_ISAC=OFF \
	-DENABLE_JPEG=ON \
	-DENABLE_LIME=ON \
	-DENABLE_MBEDTLS=ON \
	-DENABLE_MKV=OFF \
	-DENABLE_MPEG4=OFF \
	-DENABLE_NLS=NO \
	-DENABLE_NON_FREE_CODECS=OFF \
	-DENABLE_OPENH264=OFF \
	-DENABLE_OPUS=ON \
	-DENABLE_PCAP=OFF \
	-DENABLE_POLARSSL=OFF \
	-DENABLE_RELATIVE_PREFIX=OFF \
	-DENABLE_SILK=OFF \
	-DENABLE_SPEEX=ON \
	-DENABLE_SRTP=ON \
	-DENABLE_TOOLS=OFF \
	-DENABLE_UNIT_TESTS=ON \
	-DENABLE_UNMAINTAINED=OFF \
	-DENABLE_VCARD=OFF \
	-DENABLE_VIDEO=ON \
	-DENABLE_VPX=ON \
	-DENABLE_WEBRTC_AECM=ON \
	-DENABLE_WEBRTC_AEC=OFF \
	-DENABLE_X264=OFF \
	-DENABLE_ZRTP=ON

make
make debug-sdk
cd ../../../..

rm -rf app/src/main/jniLibs/
rm -rf app/libs/

mkdir app/libs/
unzip -o $(find "${BUILD_DIR}/linphone-android/liblinphone-sdk/bin/distributions/" -maxdepth 1 -name liblinphone-android-sdk\*.zip) -d app/libs/

echo "liblinphone build successfull with git hash: ${GIT_HASH}"
