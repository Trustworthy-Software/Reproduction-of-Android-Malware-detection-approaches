#!/bin/bash

for i in $@
do
	res=`aapt list -a $i | grep -E "(^Package Group)*(packageCount=1 name=)"`
    if [ ${#ret}  -gt 1 ];
    then
        echo -e $i"\t"${res##*=}
        exit 0
    fi
	res=`aapt list -a $i | grep -E "package=" | awk -F[\"] '{print $2}'`
    echo -e $i"\t"$res
	#echo ${res##*=}
done
