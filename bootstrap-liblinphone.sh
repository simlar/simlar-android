#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r COMPILE_SCRIPT="$(dirname $(readlink -f $0))/compile-liblinphone.sh"
declare -r LINPHONE_ANDROID_PATCH_DIR="$(dirname $(readlink -f $0))/patches/linphone-android"
declare -r LINPHONE_PATCH_DIR="$(dirname $(readlink -f $0))/patches/linphone"
declare -r MEDIASTREAMER2_PATCH_DIR="$(dirname $(readlink -f $0))/patches/mediastreamer2"
declare -r BELLESIP_PATCH_DIR="$(dirname $(readlink -f $0))/patches/belle-sip"
declare -r BRANCH=${1:-""}

declare -r BUILD_DIR="liblinphone_build_$(date '+%Y%m%d_%H%M%S')"
mkdir "${BUILD_DIR}"
cd "${BUILD_DIR}"

if [ "${BRANCH}" == "" ] ; then
	git clone git://git.linphone.org/linphone-android.git --recursive
else
	git clone -b "${BRANCH}" git://git.linphone.org/linphone-android.git --recursive
fi

cd linphone-android
declare -r GIT_HASH=$(git log -n1 --format="%H")

if [ -d "${LINPHONE_ANDROID_PATCH_DIR}" ] ; then
	find "${LINPHONE_ANDROID_PATCH_DIR}" -maxdepth 1 -name \*.patch -exec git am {} \;
fi

if [ -d "${LINPHONE_PATCH_DIR}" ] ; then
	cd submodules/linphone/
	find "${LINPHONE_PATCH_DIR}" -maxdepth 1 -name \*.patch -exec git am {} \;
	cd ../..
fi

if [ -d "${MEDIASTREAMER2_PATCH_DIR}" ] ; then
	cd submodules/linphone/mediastreamer2
	find "${MEDIASTREAMER2_PATCH_DIR}" -maxdepth 1 -name \*.patch -exec git am {} \;
	cd ../../..
fi

if [ -d "${BELLESIP_PATCH_DIR}" ] ; then
	cd submodules/belle-sip
	find "${BELLESIP_PATCH_DIR}" -maxdepth 1 -name \*.patch -exec git am {} \;
	cd ../..
fi

cd ../..

"${COMPILE_SCRIPT}" "${BUILD_DIR}" "${GIT_HASH}"
