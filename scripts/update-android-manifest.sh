#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

declare -r VERSION_NAME=${1:?"versionName needed"}
declare -r MANIFEST=${2:?"AndroidManifest.xml needed"}


## increment versionCode
declare -ri VERSION_CODE=$((1+$(sed -n "s/^[[:space:]]*android:versionCode=\"\([[:digit:]]*\)\".*/\1/p" "${MANIFEST}")))
sed -i "s/\(^[[:space:]]*android:versionCode=\"\)[[:digit:]]*\(\".*\)/\1${VERSION_CODE}\2/" "${MANIFEST}"

## update versionName
sed -i "s/\(^[[:space:]]*android:versionName=\"\)[^\"]*\(\".*\)/\1${VERSION_NAME}\2/" "${MANIFEST}"
