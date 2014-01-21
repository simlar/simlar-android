#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail


declare -r BUILD_DIR="$(pwd)/linphone_$(date '+%Y%m%d_%H%M%S')"
mkdir "${BUILD_DIR}"

cd "${BUILD_DIR}"

mkdir -p prefixdir/usr/local/

git clone git://git.linphone.org/osip.git -b linphone linphone-osip
cd linphone-osip
./autogen.sh
./configure --prefix="${BUILD_DIR}/prefixdir/usr/local/"
make
make install
cd ..
echo "linphone-osip success"


git clone git://git.linphone.org/polarssl.git linphone-polarssl
cd linphone-polarssl
make SHARED=1
make install DESTDIR="${BUILD_DIR}/prefixdir/usr/local/"
cd ..
echo "linphone-polarssl success"


git clone git://git.linphone.org/belle-sip.git linphone-belle-sip
cd linphone-belle-sip
./autogen.sh
./configure --enable-tls --prefix="${BUILD_DIR}/prefixdir/usr/local/"
make
make install
cd ..
echo "belle-sip success"


git clone git://git.linphone.org/srtp.git linphone-srtp
cd linphone-srtp
autoconf
./configure --prefix="${BUILD_DIR}/prefixdir/usr/local/"
make
make install
cd ..
echo "linphone-srtp success"


git clone git://git.linphone.org/zrtpcpp.git linphone-libzrtpcpp
cd linphone-libzrtpcpp/
mkdir build
cd build
cmake -Denable-ccrtp=false ..
make
make install DESTDIR="${BUILD_DIR}/prefixdir/"
cd ..
cd ..
echo "linphone-libzrtpcpp success"


git clone git://git.linphone.org/linphone.git --recursive linphone
cd linphone/
cd oRTP/
./autogen.sh
PKG_CONFIG_PATH="${BUILD_DIR}/prefixdir/usr/local/lib/pkgconfig/" ./configure --prefix="${BUILD_DIR}/prefixdir/usr/local/" --with-srtp="${BUILD_DIR}/prefixdir/usr/local/" --enable-zrtp
make 
make install
cd ..
echo "oRTP success"

cd mediastreamer2
./autogen.sh
./configure --prefix="${BUILD_DIR}/prefixdir/usr/local/"
make
make install
cd ..
echo "mediastreamer2 success"

./autogen.sh
PKG_CONFIG_PATH="${BUILD_DIR}/prefixdir/usr/local/lib/pkgconfig/" ./configure --prefix="${BUILD_DIR}/prefixdir/usr/local/" --with-osip="${BUILD_DIR}/prefixdir/usr/local/" --enable-external-ortp --enable-zrtp --enable-ssl
make
make install
echo "linphone success"
