#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r SIMLAR_KEYSTORE=${SIMLAR_KEYSTORE:-""}
declare -r KEYSTORE=${1:-"${SIMLAR_KEYSTORE}"}


if [ -z "${KEYSTORE}" ] ; then
	echo "Please set give parameter keystore, e.g.:"
	echo "  $0 ~/dev/android/simlar-release-key.keystore"
	echo "or set the environment variable KEYSTORE, e.g.:"
	echo "  export SIMLAR_KEYSTORE=~/dev/android/simlar-release-key.keystore ; $0"
	exit
fi

echo "using keystore ${KEYSTORE}"

function remove_ant_files()
{
	rm -f ant.properties build.xml local.properties
}

remove_ant_files
rm -f Simlar.apk

android update project --path . -n Simlar

ant clean
ant release

jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore "${KEYSTORE}" bin/Simlar-release-unsigned.apk simlar
zipalign -v 4 bin/Simlar-release-unsigned.apk Simlar.apk

ant clean

remove_ant_files

echo "successfully created: Simlar.apk"
