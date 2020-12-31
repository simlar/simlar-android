#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r SKIP_PUBLISH_TO_PLAYSTORE=${SKIP_PUBLISH_TO_PLAYSTORE:-""}
declare -r SIMLAR_ANDROID_PUBLISHER_CREDENTIALS=${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS:-""}
declare -r SIMLAR_ANDROID_KEYSTORE_FILE=${SIMLAR_ANDROID_KEYSTORE_FILE:-""}
declare -r SIMLAR_ANDROID_KEYSTORE_PASSWORD=${SIMLAR_ANDROID_KEYSTORE_PASSWORD:-""}

declare DOCKER_ARGUMENTS="--rm \
    --cap-drop all --security-opt=no-new-privileges \
    -v $(pwd)-docker-gradle-cache:/home/builder/.gradle \
    -v $(pwd):/pwd
    -e SKIP_PUBLISH_TO_PLAYSTORE"

if [ -e "${SIMLAR_ANDROID_KEYSTORE_FILE}" ] ; then
    DOCKER_ARGUMENTS="${DOCKER_ARGUMENTS} \
        -v ${SIMLAR_ANDROID_KEYSTORE_FILE}:/android-release-key.keystore \
        -e SIMLAR_ANDROID_KEYSTORE_FILE=/android-release-key.keystore \
        -e SIMLAR_ANDROID_KEYSTORE_PASSWORD"
fi

if [ -e "${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS}" ] ; then
    DOCKER_ARGUMENTS="${DOCKER_ARGUMENTS} \
        -v ${SIMLAR_ANDROID_PUBLISHER_CREDENTIALS}:/simlar-play-publisher-credentials.json \
        -e SIMLAR_ANDROID_PUBLISHER_CREDENTIALS=/simlar-play-publisher-credentials.json"
fi

docker run ${DOCKER_ARGUMENTS} simlar-android-builder:latest bash -c "cd /pwd && $*"
