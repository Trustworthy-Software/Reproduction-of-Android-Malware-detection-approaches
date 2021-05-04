#!/bin/bash 

[ $# -lt 0 ] &&  echo "too few arguments." && exit 1

timeout() {

    time=$1

    # start the command in a subshell to avoid problem with pipes
    # (spawn accepts one command)
    command="/bin/sh -c \"$2\""

    expect -c "set echo \"-noecho\"; set timeout $time; spawn -noecho $command; expect timeout { exit 1 } eof { exit 0 }"    

    if [ $? = 1 ] ; then
        echo "Timeout after ${time} seconds"
    fi

}
#name of the directory in ~/droidcat/droidcat/testbed/input/ where your apps are stored
namedir=$1
appsdir=~/droidcat/droidcat/testbed/input/${namedir}
instrdir=~/droidcat/droidcat/testbed/cg.instrumented/${namedir}
logdir=~/droidcat/droidcat/testbed/logs/${namedir}
mkdir -p $instrdir $logdir
logfile=${logdir}/log.instr
c=0
>$logfile
ls $appsdir/*.apk | while read apk;
do
    if [ -s $instrdir/${apk##*/} ];then
        echo "$apk has been instrumented already"
        continue
    fi
    timeout 1800 "./cgInstr.sh $apk $instrdir $logdir >> $logfile"
    echo "$apk instrumented."
done

exit $c
