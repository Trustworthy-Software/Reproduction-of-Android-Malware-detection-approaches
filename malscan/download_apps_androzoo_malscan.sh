#!/bin/bash

#file containing sha256 of apps
input=$1
#year
year=$2
#type is either malware or goodware
type=$3
#your AndroZoo APIKEY
apikey=$4

mkdir -p apps/${year}/${type}
while IFS= read -r line
do
  sha256="${line%.*}"
  wget "https://androzoo.uni.lu/api/download?apikey=${apikey}&sha256=${sha256}" \
  -O apps/${year}/${type}/${sha256}.apk
done < "$input"

exit 0
