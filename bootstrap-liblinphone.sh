#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r BRANCH=${1:-""}

declare -r COMPILE_SCRIPT="$(dirname $(readlink -f $0))/compile-liblinphone.sh"
declare -r LINPHONE_ANDROID_PATCH_DIR="$(dirname $(readlink -f $0))/patches/linphone-android"
declare -r LINPHONE_PATCH_DIR="$(dirname $(readlink -f $0))/patches/linphone"
declare -r MEDIASTREAMER2_PATCH_DIR="$(dirname $(readlink -f $0))/patches/mediastreamer2"
declare -r BELLESIP_PATCH_DIR="$(dirname $(readlink -f $0))/patches/belle-sip"
declare -r ORTP_PATCH_DIR="$(dirname $(readlink -f $0))/patches/ortp"

declare -r BUILD_DIR="liblinphone_build_$(date '+%Y%m%d_%H%M%S')"
mkdir "${BUILD_DIR}"
cd "${BUILD_DIR}"

if [ "${BRANCH}" == "" ] ; then
	git clone git://git.linphone.org/linphone-android.git --recursive
else
	git clone git://git.linphone.org/linphone-android.git
	cd linphone-android
	git checkout "${BRANCH}"
	git submodule sync
	git submodule update --recursive --init
	cd ..
fi

cd linphone-android
declare -r GIT_HASH=$(git log -n1 --format="%H")

if [ -d "${LINPHONE_ANDROID_PATCH_DIR}" ] ; then
	git am "${LINPHONE_ANDROID_PATCH_DIR}"/*.patch

	## patches to linphone-android may change submodules, so be sure to update them here
	git submodule update --recursive --init
fi

if [ -d "${LINPHONE_PATCH_DIR}" ] ; then
	cd submodules/linphone/
	git am "${LINPHONE_PATCH_DIR}"/*.patch
	cd ../..
fi

if [ -d "${MEDIASTREAMER2_PATCH_DIR}" ] ; then
	cd submodules/linphone/mediastreamer2
	git am "${MEDIASTREAMER2_PATCH_DIR}"/*.patch
	cd ../../..
fi

if [ -d "${BELLESIP_PATCH_DIR}" ] ; then
	cd submodules/belle-sip
	git am "${BELLESIP_PATCH_DIR}"/*.patch
	cd ../..
fi

if [ -d "${ORTP_PATCH_DIR}" ] ; then
	cd submodules/linphone/oRTP/
	git am "${ORTP_PATCH_DIR}"/*.patch
	cd ../../..
fi

cd ../..

"${COMPILE_SCRIPT}" "${BUILD_DIR}" "${GIT_HASH}"
