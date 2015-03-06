#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r SIMLAR_KEYSTORE=${SIMLAR_KEYSTORE:-""}
declare -r KEYSTORE=${1:-"${SIMLAR_KEYSTORE}"}

declare -r GRADLEW="$(dirname $(readlink -f $0))/../gradlew"

if [ -z "${KEYSTORE}" ] ; then
	echo "Please set give parameter keystore, e.g.:"
	echo "  $0 ~/dev/android/simlar-release-key.keystore"
	echo "or set the environment variable KEYSTORE, e.g.:"
	echo "  export SIMLAR_KEYSTORE=~/dev/android/simlar-release-key.keystore ; $0"
	exit
fi

echo "using keystore ${KEYSTORE}"

cd "$(dirname $(readlink -f $0))/../"

rm -f Simlar.apk
rm -f Simlar-alwaysOnline.apk

"${GRADLEW}" clean
"${GRADLEW}" assembleRelease

jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore "${KEYSTORE}" app/build/outputs/apk/app-push-release-unsigned.apk simlar
zipalign -v 4 app/build/outputs/apk/app-push-release-unsigned.apk Simlar.apk

jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore "${KEYSTORE}" app/build/outputs/apk/app-alwaysOnline-release-unsigned.apk simlar
zipalign -v 4 app/build/outputs/apk/app-alwaysOnline-release-unsigned.apk Simlar-alwaysOnline.apk

"${GRADLEW}" clean

echo "successfully created: Simlar-alwaysOnline.apk and Simlar.apk"
