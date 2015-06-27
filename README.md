simlar-android
==============

[Simlar](https://www.simlar.org) is a cross platform VoIP App aiming to make encrypted calls easy.

### Build dependencies ###
* Java Development Kit
* Android SDK
* Android Studio
 * Android Studio is not really needed but is the recommended way to hack on simlar-android.

### Compile (Console) ###
Linux/MacOS
```
export ANDROID_HOME=<YOUR ANDROID SDK DIRECTORY>
./gradlew assembleDebug
```

### Android Studio ###
Initially importing simlar-android in Android Studio, removes the inspection settings. That's why we recommend to run the following command once after importing:
```
git checkout .idea/
```

### liblinphone ###
Simlar heavily depends on [liblinphone](http://www.linphone.org/). In order to make it easy to start hacking on simlar-android, pre-compiled libs are checked in. However if you would like to compile it yourself, you should start with compiling [linphone-android](https://github.com/BelledonneCommunications/linphone-android). Please follow the build instructions there. Once you have managed to compile linphone-android on your system, here is a script for checking out, compile and integrate liblinphone into simlar-android.
```
./scripts/bootstrap-liblinphone.sh origin/master
```
