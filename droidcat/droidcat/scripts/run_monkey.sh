#!/bin/bash
#namedir is the name of your directory in ~/droidcat/droidcat/testbed/cg.instrumented/ where the instrumented apks are stored
namedir=$1
tmv="300"
INPDIR=~/droidcat/droidcat/testbed/cg.instrumented/${namedir}
OUTDIR=~/droidcat/droidcat/testbed/monkey_results/${namedir}
mkdir -p $OUTDIR

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

run_monkey () {
    fnapk=$2
    echo "================ RUN INDIVIDUAL APP: ${fnapk##*/} ==========================="
    if [ -s $OUTDIR/${fnapk##*/}.logcat ];
    then
             echo "$fnapk has been processed already, skipped."
             return
    else
    emu=$1
    thisdid=$3
    echo device $emu $thisdid
    ./setupEmu.sh $emu "${thisdid: -4}"
    if [ $? = 1 ] ; then
        echo "Problem with emulator $emu!"
        exit 1
    fi

    adb -s $thisdid install $fnapk
    adb -s $thisdid logcat -v raw -s "hcai-intent-monitor" "hcai-cg-monitor" &>$OUTDIR/${fnapk##*/}.logcat &
    tgtp=`./getpackage.sh $fnapk | awk '{print $2}'`
    timeout $tmv "adb -s $thisdid shell monkey -p $tgtp --ignore-crashes \
    --ignore-timeouts --ignore-security-exceptions --throttle 200 10000000 \
    >$OUTDIR/${fnapk##*/}.monkey"
    
    fi
}
#prepare the ports
dids=()
for ((j=54; j<60 ; j+=2));
do
	dids=(${dids[@]} "emulator-55$j")
done

fnarray=()
for fnapk in $INPDIR/*.apk;
do
   if [ ! -s $OUTDIR/${fnapk##*/}.logcat ];
   then
       fnarray=(${fnarray[@]} $fnapk)
   fi
done
echo ${#fnarray[@]} apps to process!

for ((i=0; i<${#fnarray[@]} ;i+=3));
do  
         #lsof -ti tcp:5037 | xargs kill
         echo New-run $i 
         if ((($((i + 1)) != ${#fnarray[@]})) && (($((i + 2)) != ${#fnarray[@]})));
         then
            (trap 'kill 0' SIGINT; run_monkey Nexus-One-10_1 ${fnarray[i]} ${dids[0]} &\ 
            run_monkey Nexus-One-10_2 ${fnarray[i+1]} ${dids[1]} &\
            run_monkey Nexus-One-10_3 ${fnarray[i+2]} ${dids[2]})
         elif (($((i + 2)) == ${#fnarray[@]}));
         then
             (trap 'kill 0' SIGINT; run_monkey Nexus-One-10_1 ${fnarray[i]} ${dids[0]} &\
             run_monkey Nexus-One-10_2 ${fnarray[i+1]} ${dids[1]})
         else
             (trap 'kill 0' SIGINT; run_monkey Nexus-One-10_1 ${fnarray[i]} ${dids[0]})
         fi
         lsof -ti tcp:5037 | xargs kill
done
exit 0
