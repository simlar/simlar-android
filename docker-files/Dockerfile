FROM debian:bullseye

ENV ANDROID_SDK_VERSION "32"
ENV ANDROID_BUILD_TOOLS_VERSION "32.0.0"
ENV ANDROID_NDK_VERSION "21.0.6113669"

ENV ANDROID_SDK_COMMAND_LINE_TOOLS_URL "https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip"
ENV ANDROID_SDK_COMMAND_LINE_TOOLS_CHECKSUM "89f308315e041c93a37a79e0627c47f21d5c5edbe5e80ea8dc0aac8a649e0e92"
ENV DOWNLOAD_TEMP_FILE "/tmp/android-sdk-commandlinetools.zip"
ENV LANG "C.UTF-8"
ENV DEBIAN_FRONTEND "noninteractive"
ENV ANDROID_SDK_ROOT "/home/builder/Android/Sdk"
ENV ANDROID_HOME "${ANDROID_SDK_ROOT}"
ENV ANDROID_NDK "${ANDROID_SDK_ROOT}/ndk/${ANDROID_NDK_VERSION}"
ENV PATH "${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin:${PATH}"

RUN apt-get update && \
    apt-get dist-upgrade --assume-yes && \
    apt-get install --assume-yes --no-install-recommends default-jdk wget unzip && \
    apt-get install --assume-yes --no-install-recommends git cmake make patch pkg-config doxygen nasm yasm python3-pystache python3-six graphicsmagick && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN useradd --create-home builder
USER builder

RUN mkdir -p "${ANDROID_SDK_ROOT}" && \
    wget -O "${DOWNLOAD_TEMP_FILE}" "${ANDROID_SDK_COMMAND_LINE_TOOLS_URL}" && \
    echo "${ANDROID_SDK_COMMAND_LINE_TOOLS_CHECKSUM} ${DOWNLOAD_TEMP_FILE}" | sha256sum -c && \
    unzip "${DOWNLOAD_TEMP_FILE}" -d "${ANDROID_SDK_ROOT}" && \
    rm -f "${DOWNLOAD_TEMP_FILE}" && \
    mkdir "${ANDROID_SDK_ROOT}/cmdline-tools" && \
    mv "${ANDROID_SDK_ROOT}/tools/" "${ANDROID_SDK_ROOT}/cmdline-tools/"

RUN yes | "sdkmanager" \
    "platform-tools" \
    "emulator" \
    "platforms;android-${ANDROID_SDK_VERSION}" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
    "ndk;${ANDROID_NDK_VERSION}"

RUN git config --global user.email 'builder@simlar-android-builder' && \
    git config --global user.name 'simlar android builder'
