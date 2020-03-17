#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r USAGE="Usage example: $0 ./gradlew bootstrap"
declare -r COMMAND=${*?${USAGE}}

declare -r SIMLAR_ANDROID_PUBLISHER_CREDENTIALS_GPG=${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS_GPG:-""}
if [ -z "${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS_GPG}" ] ; then
	echo "Please set the environment variable SIMLAR_ANDROID_PUBLISHER_CREDENTIALS_GPG, e.g.:"
	echo "  export SIMLAR_ANDROID_PUBLISHER_CREDENTIALS_GPG=~/dev/simlar/simlar-play-publisher-credentials.json.gpg"
	exit 1
fi


declare -r TEMP_DIR=$(mktemp --directory --tmpdir simlar-android-release-XXXXXXXXXX)
echo "created temporary directory: ${TEMP_DIR}"

function cleanup {
	echo "removing temporary directory: ${TEMP_DIR}"
	rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT


echo "decrypting play publisher credentials"
declare -rx SIMLAR_ANDROID_PUBLISHER_CREDENTIALS="${TEMP_DIR}/simlar-play-publisher-credentials.json"
gpg --output "${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS}" --decrypt "${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS_GPG}"


echo "running: ${COMMAND}"
${COMMAND}
