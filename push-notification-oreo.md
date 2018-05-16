Not allowed to start service Intent { act=com.google.android.c2dm.intent.RECEIVE flg=0x30 pkg=org.simlar cmp=org.simlar/.service.SimlarService (has extras) }: app is in background uid UidRecord{a405921 u0a96 RCVR bg:+2m4s78ms idle change:uncached procs:1 seq(0,0,0)}

SIP_USER="*4915207995688*" php send-push-notification-cmd.php

Reproduce on Oreo
 * start app
 * receive Push => works
 * wait 2 Min / or force quit app
 * receive Push => crash

https://developers.google.com/cloud-messaging/android/android-migrate-fcm

MicroG does not support FCM, yet.
So we workaround this issue by lowering the targetSdk to 25 temporarily.
