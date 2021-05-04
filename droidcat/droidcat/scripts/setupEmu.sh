#!/bin/bash

port=${2:-"5554"}
did="emulator-$port"

pid=`ps axf | grep -v grep | grep "$1 -noaudio -no-window -port $port" | awk '{print $1}'`
kill -9 "${pid}"

timeout 30s emulator -avd $1 -wipe-data -no-window

emulator -avd $1 -noaudio -no-window -port $port -gpu off &

date1=$(date +"%s")

OUT=`adb -s $did shell getprop init.svc.bootanim` 
timeout=0
while [[ ${OUT:0:7}  != 'stopped' ]]; do
  OUT=`adb -s $did shell getprop init.svc.bootanim`
  sleep 5
  ((timeout=timeout+5))
  if [ $timeout -ge 180 ];then
      echo "booting emulator time out; bailing out"
      exit 1
  fi
done

echo "Emulator booted!"
exit 0
