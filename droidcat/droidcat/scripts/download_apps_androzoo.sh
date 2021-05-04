#!/bin/bash

#file containing sha256 of apps
input=$1
#namedir is the name of the directory in ~/droidcat/droidcat/testbed/input/ where the apps will be stored
namedir=$2
#your AndroZoo APIKEY
apikey=$3

mkdir -p ~/droidcat/droidcat/testbed/input/${namedir}
while IFS= read -r line
do
  sha256="${line%.*}"
  wget "https://androzoo.uni.lu/api/download?apikey=${apikey}&sha256=${sha256}" \
  -O ~/droidcat/droidcat/testbed/input/${namedir}/${sha256}.apk
done < "$input"

exit 0
