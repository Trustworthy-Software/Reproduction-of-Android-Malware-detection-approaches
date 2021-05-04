#!/bin/bash

apkfile=$1
ROOT=~/droidcat

OUTDIR=$2
LOGDIR=$3
mkdir -p $OUTDIR $LOGDIR

MAINCP="$ROOT/jdk1.8.0_261/jre/lib/rt.jar:$ROOT/libs/droidfax.jar"
SOOTCP="~/.android/platforms/android-21/android.jar:$ROOT/libs/droidfax.jar"

for i in $ROOT/libs/*.jar;
do
    MAINCP=$MAINCP:$i
done

# get the apk file name without prefixing path and suffixing extension
suffix=${apkfile##*/}
suffix=${suffix%.*}

logout=$LOGDIR/instr-$suffix.out
logerr=$LOGDIR/instr-$suffix.err

starttime=`date +%s%N | cut -b1-13`

cmd="java -Xmx200g -Xss1g -ea -cp $MAINCP dynCG.sceneInstr \
	-w -cp $SOOTCP -p cg verbose:false,implicit-entry:true \
	-p cg.spark verbose:false,on-fly-cg:true,rta:false \
	-d $OUTDIR \
        -instr3rdparty \
        -process-dir $apkfile"

($cmd | tee $logout) 3>&1 1>&2 2>&3 | tee $logerr

stoptime=`date +%s%N | cut -b1-13`
echo "StaticAnalysisTime for $suffix elapsed: " `expr $stoptime - $starttime` milliseconds
echo "static analysis finished."

echo "chapple" | ./signandalign.sh $OUTDIR/${suffix}.apk
exit 0

