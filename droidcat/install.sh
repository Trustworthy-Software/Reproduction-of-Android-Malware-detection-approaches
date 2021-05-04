#!/bin/bash

#put your link to commandlinetools
android_sdk=https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip

#install android_sdk
wget -O "commandline.zip" $android_sdk
unzip commandline.zip
mkdir -p cmdline-tools
mv tools cmdline-tools
mkdir -p ~/.android
mv cmdline-tools ~/.android

PATH=~/droidcat/jdk1.8.0_261/bin/:~/.android/cmdline-tools/tools/bin:~/.android/emulator:~/.android/platform-tools/:~/.android/platforms/android-19:~/.android/tools:$PATH
JAVA_HOME=~/droidcat/jdk1.8.0_261

sdkmanager "system-images;android-19;google_apis;x86" "platforms;android-19" "build-tools;19.0.0" "platform-tools" "emulator" 
sdkmanager "platforms;android-21" 
#create 3 avds. Note that based on DroidFax dependencies installation, the avd used is Nexus-One (its id is 16), with 1G sdcard 
avdmanager create avd -n Nexus-One-10_1 -k "system-images;android-19;google_apis;x86" -d "16" -c 1G
avdmanager create avd -n Nexus-One-10_2 -k "system-images;android-19;google_apis;x86" -d "16" -c 1G
avdmanager create avd -n Nexus-One-10_3 -k "system-images;android-19;google_apis;x86" -d "16" -c 1G

ANDROID_HOME=~/.android/tools
ANDROID_SDK_ROOT=~/.android/tools

cd ~/droidcat/droidcat/scripts/
chmod u+x cgInstr.sh signandalign.sh getpackage.sh setupEmu.sh
cd ~/droidcat

