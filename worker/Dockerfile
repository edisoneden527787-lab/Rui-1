FROM eclipse-temurin:17-jdk-jammy
ENV ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip wget python3 python3-pip && rm -rf /var/lib/apt/lists/*
RUN mkdir -p $ANDROID_HOME/cmdline-tools && wget -q -O /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools && mkdir -p $ANDROID_HOME/cmdline-tools/latest && mv /tmp/cmdtools/cmdline-tools/* $ANDROID_HOME/cmdline-tools/latest/ && rm -rf /tmp/cmdtools /tmp/cmdtools.zip
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true
RUN sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
RUN wget -q -O /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.11.1-bin.zip && unzip -q /tmp/gradle.zip -d /opt && rm /tmp/gradle.zip
ENV PATH=$PATH:/opt/gradle-8.11.1/bin
WORKDIR /srv
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt
COPY main.py .
EXPOSE 8010
CMD ["uvicorn","main:app","--host","0.0.0.0","--port","8010"]
