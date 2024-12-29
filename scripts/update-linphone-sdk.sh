#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r USAGE="Usage example: $0 \"4.4.15\" \"4.4.20\""

declare -r OLD_VERSION=${1?${USAGE}}
declare -r NEW_VERSION=${2?${USAGE}}

declare -r LIBS_DIRECTRORY="app/libs/linphone-sdk/${NEW_VERSION}"
if [ -d "${LIBS_DIRECTRORY}" ] ; then
  echo "ERROR: "${LIBS_DIRECTRORY}" exists"
  exit 1
fi


~/cloud/scripts/git-create-space-commits.sh "liblinphone ${NEW_VERSION}"


sed -i "s/liblinphoneVersion = \"${OLD_VERSION}\"/liblinphoneVersion = \"${NEW_VERSION}\"/" app/build.gradle
git commit -am "[gradle] update liblinphone ${OLD_VERSION} -> ${NEW_VERSION}"


find app/src/push/play/release-notes/ -name alpha.txt -exec sed -i "s/${OLD_VERSION}/${NEW_VERSION}/" {} \;
git commit -am "[play] update release notes liblinphone ${NEW_VERSION}"


sed -i "s/${OLD_VERSION}/${NEW_VERSION}/" scripts/bootstrap-liblinphone.sh
git commit -am "[bootstrap-liblinphone.sh] default to version ${NEW_VERSION}"


time docker run --cap-drop all --security-opt=no-new-privileges -it --rm -v $(pwd)-docker-gradle-cache:/home/builder/.gradle -v $(pwd):/pwd -e CMAKE_BUILD_PARALLEL_LEVEL=16 simlar-android-builder:latest bash -c "cd /pwd && ./scripts/bootstrap-liblinphone.sh"

#git add app/libs/
#git commit -m "[liblinphone] rebuild version ${NEW_VERSION}"
#git revert HEAD --no-edit
