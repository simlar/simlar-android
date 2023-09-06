#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r BRANCH=${1:-"5.2.99"} ## use master to build current git revision

declare -r PROJECT_DIR="$(dirname $(readlink -f $0))/.."
declare -r COMPILE_SCRIPT="${PROJECT_DIR}/scripts/compile-liblinphone.sh"

declare -r PATCH_DIR="${PROJECT_DIR}/liblinphone/patches"

declare -r LINPHONE_SDK_PATCH_DIR="${PATCH_DIR}/linphone-sdk"
declare -r LINPHONE_PATCH_DIR="${PATCH_DIR}/liblinphone"
declare -r MEDIASTREAMER2_PATCH_DIR="${PATCH_DIR}/mediastreamer2"
declare -r BELLESIP_PATCH_DIR="${PATCH_DIR}/belle-sip"
declare -r ORTP_PATCH_DIR="${PATCH_DIR}/ortp"
declare -r BZRTP_PATCH_DIR="${PATCH_DIR}/bzrtp"

declare -r BUILD_DIR="${PROJECT_DIR}/liblinphone/builds/$(basename "${BRANCH}")_$(date '+%Y%m%d_%H%M%S')"
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"


git clone https://gitlab.linphone.org/BC/public/linphone-sdk.git
cd linphone-sdk
git checkout "${BRANCH}"
git submodule sync --recursive


if [ -d "${LINPHONE_SDK_PATCH_DIR}" ] ; then
	git am "${LINPHONE_SDK_PATCH_DIR}"/*.patch
fi

## patches to linphone-android may change submodules, so be sure to update them here
git submodule update --recursive --init

if [ -d "${LINPHONE_PATCH_DIR}" ] ; then
	cd liblinphone
	git am "${LINPHONE_PATCH_DIR}"/*.patch
	cd ..
fi

if [ -d "${MEDIASTREAMER2_PATCH_DIR}" ] ; then
	cd mediastreamer2
	git am "${MEDIASTREAMER2_PATCH_DIR}"/*.patch
	cd ..
fi

if [ -d "${BELLESIP_PATCH_DIR}" ] ; then
	cd belle-sip
	git am "${BELLESIP_PATCH_DIR}"/*.patch
	cd ..
fi

if [ -d "${ORTP_PATCH_DIR}" ] ; then
	cd linphone/oRTP
	git am "${ORTP_PATCH_DIR}"/*.patch
	cd ../..
fi

if [ -d "${BZRTP_PATCH_DIR}" ] ; then
	cd bzrtp
	git am "${BZRTP_PATCH_DIR}"/*.patch
	cd ..
fi

cd "${PROJECT_DIR}"

"${COMPILE_SCRIPT}" "${BUILD_DIR}" "${BRANCH}"
