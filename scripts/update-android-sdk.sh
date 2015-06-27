#!/bin/bash

## see: https://gist.github.com/yuvipanda/10408391

expect -c "
set timeout -1;
spawn "${ANDROID_HOME}/tools/android" update sdk --no-ui
expect {
    \"Do you accept the license\" { exp_send \"y\r\"; exp_continue }
    eof
}
"
