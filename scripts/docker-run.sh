#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r USAGE="Usage example: $0 ./gradlew bootstrap"
declare -r COMMAND=${*?${USAGE}}

declare DOCKER_OPTIONS=""

if [ -n ${SIMLAR_ANDROID_DOCKER_GRADLE_CACHE:-""} ] ; then
	DOCKER_OPTIONS="${DOCKER_OPTIONS} -v ${SIMLAR_ANDROID_DOCKER_GRADLE_CACHE}:/home/builder/.gradle"
fi

if [ -n ${SIMLAR_ANDROID_KEYSTORE_FILE:-""} ] ; then
	DOCKER_OPTIONS="${DOCKER_OPTIONS} -v ${SIMLAR_ANDROID_KEYSTORE_FILE}:/home/builder/android-release-key.keystore -e SIMLAR_ANDROID_KEYSTORE_FILE=/home/builder/android-release-key.keystore"
fi

if [ -n ${SIMLAR_ANDROID_KEYSTORE_PASSWORD:-""} ] ; then
	DOCKER_OPTIONS="${DOCKER_OPTIONS} -e SIMLAR_ANDROID_KEYSTORE_PASSWORD=${SIMLAR_ANDROID_KEYSTORE_PASSWORD}"
fi

if [ -n ${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS:-""} ] ; then
	DOCKER_OPTIONS="${DOCKER_OPTIONS} -v ${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS}:/home/builder/android-publisher-credentials.json -e SIMLAR_ANDROID_PUBLISHER_CREDENTIALS=/home/builder/android-publisher-credentials.json"
fi

docker run ${DOCKER_OPTIONS} ${COMMAND}
