FROM debian:buster

ENV ANDROID_SDK_VERSION "29"
ENV ANDROID_SDK_COMMAND_LINE_TOOLS_URL "https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip"
ENV ANDROID_SDK_COMMAND_LINE_TOOLS_CHECKSUM "89f308315e041c93a37a79e0627c47f21d5c5edbe5e80ea8dc0aac8a649e0e92"
ENV DOWNLOAD_TEMP_FILE "/tmp/android-sdk-commandlinetools.zip"
ENV LANG "C"
ENV DEBIAN_FRONTEND "noninteractive"
ENV ANDROID_SDK_ROOT "/opt/android-sdks"

RUN apt-get update
RUN apt-get dist-upgrade --assume-yes
RUN apt-get install --assume-yes --no-install-recommends default-jdk wget unzip

RUN wget -O "${DOWNLOAD_TEMP_FILE}" "${ANDROID_SDK_COMMAND_LINE_TOOLS_URL}"
RUN echo "${ANDROID_SDK_COMMAND_LINE_TOOLS_CHECKSUM} ${DOWNLOAD_TEMP_FILE}" | sha256sum -c

RUN unzip "${DOWNLOAD_TEMP_FILE}" -d "${ANDROID_SDK_ROOT}"
RUN rm -f "${DOWNLOAD_TEMP_FILE}"
RUN mkdir "${ANDROID_SDK_ROOT}/cmdline-tools"
RUN mv "${ANDROID_SDK_ROOT}/tools/" "${ANDROID_SDK_ROOT}/cmdline-tools/"

RUN echo "y" | "${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin/sdkmanager" "platforms;android-${ANDROID_SDK_VERSION}"
