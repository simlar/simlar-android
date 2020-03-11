#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r  SKIP_PUBLISH_TO_PLAYSTORE=${SKIP_PUBLISH_TO_PLAYSTORE:-""}
declare -r  SIMLAR_KEYSTORE=${SIMLAR_KEYSTORE:-""}
declare -rx KEYSTORE_FILE=${1:-"${SIMLAR_KEYSTORE}"}

declare -r  PROJECT_DIR="$(dirname $(readlink -f $0))/.."
declare -r  GRADLEW="${PROJECT_DIR}/gradlew"

if [ -z "${KEYSTORE_FILE}" ] ; then
	echo "Please add parameter keystore, e.g.:"
	echo "  $0 ~/dev/android/simlar-release-key.keystore"
	echo "or set the environment variable SIMLAR_KEYSTORE, e.g.:"
	echo "  export SIMLAR_KEYSTORE=~/dev/simlar/simlar-release-key.keystore ; $0"
	exit
fi

echo "using keystore ${KEYSTORE_FILE}"
echo "enter its password:"
declare -rx KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD:-"$( stty -echo; head -n 1; stty echo )"}


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
