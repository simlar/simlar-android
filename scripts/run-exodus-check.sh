#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

docker run -v $(pwd)/app/build/outputs/apk/push/release/app-push-release-unsigned.apk:/app.apk --rm -i exodusprivacy/exodus-standalone
