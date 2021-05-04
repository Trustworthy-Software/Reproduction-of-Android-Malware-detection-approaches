#!/bin/bash


dir_feat=features
rootdir=features_droidcat_byfirstseen
mkdir -p $rootdir

for ((i=0;i<=7;i++))
do
    for fn in gfeatures iccfeatures securityfeatures 
    do
        python3 splitByFirstSeen.py firstseendates/firstseen-zoobenign201$i.txt $dir_feat/zoobenign201$i/$fn.txt new-zoobenign 201$i

        for d in new-zoobenign*
        do
            dstdir=$rootdir/${d##*-}
            mkdir -p $dstdir
            cat $d >> $dstdir/$fn.txt
        done
        rm new-zoobenign*
    done
done


for i in 0 1 2 7
do
    for fn in gfeatures iccfeatures securityfeatures 
    do
        python3 splitByFirstSeen.py firstseendates/firstseen-zoo201$i.txt $dir_feat/zoo201$i/$fn.txt new-zoo 201$i

        for d in new-zoo*
        do
            dstdir=$rootdir/${d##*-}
            mkdir -p $dstdir
            cat $d >> $dstdir/$fn.txt
        done
        rm new-zoo*
    done
done


for ((i=3;i<=6;i++))
do
    for fn in gfeatures iccfeatures securityfeatures 
    do
        python3 splitByFirstSeen.py firstseendates/firstseen-vs201$i.txt $dir_feat/vs201$i/$fn.txt new-vs 201$i

        for d in new-vs*
        do
            dstdir=$rootdir/${d##*-}
            mkdir -p $dstdir
            cat $d >> $dstdir/$fn.txt
        done
        rm new-vs*
    done
done

for i in 1
do
    for fn in gfeatures iccfeatures securityfeatures 
    do
        python3 splitByFirstSeen.py firstseendates/firstseen-zoo201$i.txt $dir_feat/newzoo201$i/$fn.txt new-zoo 201$i

        for d in new-zoo*
        do
            dstdir=$rootdir/${d##*-}
            mkdir -p $dstdir
            cat $d >> $dstdir/$fn.txt
        done
        rm new-zoo*
    done
done


for i in 7
do
    for fn in gfeatures iccfeatures securityfeatures 
    do
        python3 splitByFirstSeen.py firstseendates/firstseen-malware201$i.txt $dir_feat/malware-201$i-more/$fn.txt new-malware-more 201$i

        for d in new-malware-more*
        do
            dstdir=$rootdir/${d##*-}
            mkdir -p $dstdir
            cat $d >> $dstdir/$fn.txt
        done
        rm new-malware-more*
    done
done

exit 0
