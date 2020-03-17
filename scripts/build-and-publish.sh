#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r  SKIP_PUBLISH_TO_PLAYSTORE=${SKIP_PUBLISH_TO_PLAYSTORE:-""}
declare -rx SIMLAR_ANDROID_PUBLISHER_CREDENTIALS=${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS:-""}
declare -rx SIMLAR_ANDROID_KEYSTORE_FILE=${SIMLAR_ANDROID_KEYSTORE_FILE:-""}

declare -r  PROJECT_DIR="$(dirname $(readlink -f $0))/.."
declare -r  GRADLEW="${PROJECT_DIR}/gradlew"

if [ -z "${SKIP_PUBLISH_TO_PLAYSTORE}" ] && [ -z "${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS}" ] ; then
	echo "Please set the environment variable SIMLAR_ANDROID_PUBLISHER_CREDENTIALS, e.g.:"
	echo "  export SIMLAR_ANDROID_PUBLISHER_CREDENTIALS=~/dev/simlar/simlar-play-publisher-credentials.json"
	echo "or skip publishing with: "
	echo "  SKIP_PUBLISH_TO_PLAYSTORE=yes $0"
	exit 1
fi

if [ -z "${SIMLAR_ANDROID_KEYSTORE_FILE}" ] ; then
	echo "Please set the environment variable SIMLAR_ANDROID_KEYSTORE_FILE, e.g.:"
	echo "  export SIMLAR_ANDROID_KEYSTORE_FILE=~/dev/simlar/simlar-release-key.keystore"
	exit 1
fi

echo "using publisher credentials ${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS}"
echo "using keystore ${SIMLAR_ANDROID_KEYSTORE_FILE}"
echo "enter its password:"
declare -rx SIMLAR_ANDROID_KEYSTORE_PASSWORD=${SIMLAR_ANDROID_KEYSTORE_PASSWORD:-"$( stty -echo; head -n 1; stty echo )"}


cd "${PROJECT_DIR}"
rm -f Simlar.apk
rm -f Simlar-alwaysOnline.apk


"${GRADLEW}" clean assembleAlwaysOnlineRelease -Pno-google-services
mv ./app/build/outputs/apk/alwaysOnline/release/app-alwaysOnline-release.apk Simlar-alwaysOnline.apk

if [ -n "${SKIP_PUBLISH_TO_PLAYSTORE}" ] ; then
	"${GRADLEW}" clean assemblePushRelease
else
	"${GRADLEW}" clean publishPushReleaseApk
fi
mv ./app/build/outputs/apk/push/release/app-push-release.apk Simlar.apk

"${GRADLEW}" clean

echo
echo
echo "successfully created: Simlar-alwaysOnline.apk and Simlar.apk"
if [ -z "${SKIP_PUBLISH_TO_PLAYSTORE}" ] ; then
	echo "successfully published to playstore: Simlar.apk"
fi
echo
