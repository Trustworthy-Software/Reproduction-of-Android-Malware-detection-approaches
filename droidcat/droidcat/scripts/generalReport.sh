#!/bin/bash

apkfile=$1
tracefile=$2
resultdir=$3

ROOT=~/droidcat/
current_dir=`pwd`

MAINCP="$ROOT/jdk1.8.0_261/jre/lib/rt.jar"
SOOTCP="~/.android/platforms/android-21/android.jar:$ROOT/libs/droidfax.jar"

for i in ~/droidcat/libs/*.jar;
do
    MAINCP=$MAINCP:$i
done

starttime=`date +%s%N | cut -b1-13`

#generate the features vectors in resultdir
cd $resultdir
java -Xmx4g -cp ${MAINCP} reporters.generalReport \
	-w -cp $SOOTCP -p cg verbose:false,implicit-entry:true \
	-p cg.spark verbose:false,on-fly-cg:true,rta:false \
	-d $tracefile \
	-process-dir $apkfile \
	-trace $tracefile \

stoptime=`date +%s%N | cut -b1-13`

echo "Time elapsed: " `expr $stoptime - $starttime` milliseconds
exit 0
cd $current_dir

