#!/bin/bash

(test $# -lt 1) && (echo "too few arguments") && exit 0

APKDIR=~/droidcat/droidcat/testbed/cg.instrumented/$1
TRACEDIR=~/droidcat/droidcat/testbed/monkey_results/$1
RESULTDIR=~/droidcat/droidcat/testbed/allICCReports/$1

mkdir -p $RESULTDIR
resultlog=$RESULTDIR/log.ICCReport.all
> $resultlog
for orgapk in $APKDIR/*.apk
do
        apkname=${orgapk##*/}
	if [ ! -s $TRACEDIR/$apkname.logcat ];
	then
        echo $orgapk did not have trace.
		continue
	fi
	echo "result for $orgapk" >> $resultlog 2>&1
	./getpackage.sh $orgapk >> $resultlog 2>&1
	sh ICCReport.sh \
		$orgapk \
		$TRACEDIR/$apkname.logcat $RESULTDIR >> $resultlog 2>&1
done
exit 0

