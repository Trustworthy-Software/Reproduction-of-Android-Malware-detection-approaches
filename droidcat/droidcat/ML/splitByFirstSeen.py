import random
import os
import sys
import string

import inspect, re

def split(fnFSmapping, fnFeatures, fnDstprefix, defaultYear):
    fhmapping = open(fnFSmapping, 'r')
    fhfeatures = open(fnFeatures, 'r')
    if not (fhmapping and fhfeatures):
        raise Exception ("unable to open specified files...")

    lines_key2dates = fhmapping.readlines()
    lines_fvs = fhfeatures.readlines()

    fhmapping.close()
    fhfeatures.close()

    key2dates=dict()
    for line in lines_key2dates:
        line = line.lstrip().rstrip()
        items = line.split()
        if len(items)==3:
            date = items[1]
            year = date.split('-')[0]
            key2dates[items[0]] = [year, date]

    defaultDate = "%s-01-01" % (defaultYear)
    for line in lines_fvs:
        line = line.lstrip().rstrip()
        items = line.split()
        year = defaultYear
        date = defaultDate
        if items[0] in key2dates:
            year, date = key2dates[ items[0] ]

        print ("redistributing a feature vector from year %s to year %s ..." % (defaultYear, year))
        fnDst = fnDstprefix+str(year)
        fhDst = None
        if os.path.isfile(fnDst):
            fhDst = open(fnDst, 'a')
        else:
            fhDst = open(fnDst, 'w+')

        if not (fhmapping and fhfeatures and fhDst):
            raise Exception ("unable to create result file: %s" % (fnDstprefix+str(year)))

        print("%s\t%s\t%s" % (items[0], date, '\t'.join(items[1:])), file=fhDst)
        fhDst.flush()
        fhDst.close()


if __name__=="__main__":
    if len(sys.argv)<=3:
         print("too few arguments.", file=sys.stderr)
         print("%s [file of feature-vector-key-to-first-seen-date-mapping] \
               [file of feature vectors] [file of resulting feature vectors] \
               [default year]" % (sys.argv[0]), file=sys.stdout)
         sys.exit (-1)

    fnFSmapping = sys.argv[1]
    fnFeatures = sys.argv[2]
    fnDstprefix = sys.argv[3]
    defaultYear = int(sys.argv[4])

    print ("now processing %s and %s" % (fnFSmapping, fnFeatures))
    split(fnFSmapping, fnFeatures, fnDstprefix, defaultYear)
    sys.exit(0)

