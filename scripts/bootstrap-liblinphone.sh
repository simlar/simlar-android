#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r BRANCH=${1:-"2.5.1"} ## use master to build current git revision

declare -r COMPILE_SCRIPT="$(dirname $(readlink -f $0))/compile-liblinphone.sh"

declare -r PATCH_DIR="$(dirname $(readlink -f $0))/../liblinphone/patches"

declare -r LINPHONE_ANDROID_PATCH_DIR="${PATCH_DIR}/linphone-android"
declare -r LINPHONE_PATCH_DIR="${PATCH_DIR}/linphone"
declare -r MEDIASTREAMER2_PATCH_DIR="${PATCH_DIR}/mediastreamer2"
declare -r BELLESIP_PATCH_DIR="${PATCH_DIR}/belle-sip"
declare -r ORTP_PATCH_DIR="${PATCH_DIR}/ortp"
declare -r BZRTP_PATCH_DIR="${PATCH_DIR}/bzrtp"

declare -r BUILD_DIR="liblinphone/builds/$(date '+%Y%m%d_%H%M%S')"
mkdir -p "${BUILD_DIR}"
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

if [ -d "${BZRTP_PATCH_DIR}" ] ; then
	cd submodules/bzrtp/
	git am "${BZRTP_PATCH_DIR}"/*.patch
	cd ../..
fi

cd ../../../..

"${COMPILE_SCRIPT}" "${BUILD_DIR}" "${GIT_HASH}"
