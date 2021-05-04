# Load features from DroidFax feature statistics files
import numpy as np
import random
import os
import sys
import string
import subprocess
import random
from configs import *
from os import listdir
from os.path import isfile, join

#for reproducible experiments uncomment these lines
#random.seed(480509637)
#np.random.seed(480509637)

'''load general features'''
def load_generalFeatures(gfn):
    fh = open(gfn, 'r')
    if fh==None:
        raise IOError("error occurred when opening file " + gfn)
    contents = fh.readlines()
    fh.close()
    gfeatures=dict()
    n=0
    for line in contents:
        line=line.lstrip().rstrip()
        items = line.split()
        n=n+1
        assert len(items)==31
        appname = items[0]
        date = items[1]
        if (appname,date) not in list(gfeatures.keys()):
            gfeatures[ (appname,date) ] = list()
        fvs = [float(x) for x in items[2:]]
        gfeatures[ (appname,date) ].append( fvs )
    # for multiple sets of feature values per app, compute and keep the averages only
    for (app,date) in list(gfeatures.keys()):
        allsets = gfeatures[(app,date)]
        for j in range(0, len(allsets[0])):
            for k in range(1,len(allsets)):
                #print("k j", k, j)
                allsets[0][j] += allsets[k][j]
            allsets[0][j] /= (len(allsets)*1.0)
        del gfeatures[(app,date)]
        gfeatures[(app,date)] = allsets[0] #change to mapping: appname -> vector of average (element-wise) feature values
    return gfeatures

'''load ICC features'''
def load_ICCFeatures(iccfn):
    fh = open(iccfn, 'r')
    if fh==None:
        raise IOError("error occurred when opening file " + iccfn)
    contents = fh.readlines()
    fh.close()
    iccfeatures = dict()
    for line in contents:
        line = line.lstrip().rstrip()
        items = line.split()
        assert len(items)==9
        appname = items[0]
        date = items[1]
        if (appname,date) not in list(iccfeatures.keys()):
            iccfeatures[ (appname,date) ] = list()
        fvs = [float(x) for x in items[2:]]
        iccfeatures[ (appname,date) ].append( fvs )
    # for multiple sets of feature values per app, compute and keep the averages only
    for (app,date) in list(iccfeatures.keys()):
        allsets = iccfeatures[(app,date)]
        for j in range(0, len(allsets[0])):
            for k in range(1,len(allsets)):
                allsets[0][j] += allsets[k][j]
            allsets[0][j] /= (len(allsets)*1.0)
        del iccfeatures[(app,date)]
        iccfeatures[(app,date)] = allsets[0] # change to mapping: appname -> vector of average (element-wise) feature values
    return iccfeatures

'''load security features'''
def load_securityFeatures(secfn):
    fh = open(secfn, 'r')
    if fh==None:
        raise IOError("error occurred when opening file " + secfn)
    contents = fh.readlines()
    fh.close()
    secfeatures=dict()
    n=0
    for line in contents:
        line=line.lstrip().rstrip()
        items = line.split()
        assert len(items)==88
        appname = items[0]
        date = items[1]
        if (appname,date) not in list(secfeatures.keys()):
            secfeatures[ (appname,date) ] = list()
        fvs = [float(x) for x in items[2:]]
        secfeatures[ (appname,date) ].append( fvs )
    # for multiple sets of feature values per app, compute and keep the averages only
    for (app,date) in list(secfeatures.keys()):
        allsets = secfeatures[(app,date)]
        for j in range(0, len(allsets[0])):
            for k in range(1,len(allsets)):
                allsets[0][j] += allsets[k][j]
            allsets[0][j] /= (len(allsets)*1.0)
        del secfeatures[(app,date)]
        secfeatures[(app,date)] = allsets[0] # change to mapping: appname -> vector of average (element-wise) feature values
    return secfeatures

def loadBenignData(rootdir):
    return getBenignTrainingData ( os.path.join(rootdir, FTXT_G), os.path.join(rootdir, FTXT_ICC), os.path.join(rootdir, FTXT_SEC) )

def getBenignTrainingData(\
        benign_g,\
        benign_icc,\
        benign_sec):

    gfeatures_benign = load_generalFeatures(benign_g)
    iccfeatures_benign = load_ICCFeatures(benign_icc)
    secfeatures_benign = load_securityFeatures(benign_sec)
    
    for app in set(malbenignapps):
        for (_app,date) in gfeatures_benign.keys():
            if app == _app:
                del gfeatures_benign[(_app,date)]
        for (_app,date) in iccfeatures_benign.keys():
            if app == _app:
                del iccfeatures_benign[(_app,date)]
        if (_app,date) in secfeatures_benign.keys():
            if app == _app:
                del secfeatures_benign[(_app,date)]
                
    gfeatures_apps = list(gfeatures_benign.keys())
    iccfeatures_apps = list(iccfeatures_benign.keys())
    secfeatures_apps = list(secfeatures_benign.keys())
    allapps_benign = set(gfeatures_apps).intersection(iccfeatures_apps).intersection(secfeatures_apps)
    
    for (app,date) in set(list(gfeatures_benign.keys())).difference(allapps_benign):
        del gfeatures_benign[(app,date)]
    for (app,date) in set(list(iccfeatures_benign.keys())).difference(allapps_benign):
        del iccfeatures_benign[(app,date)]
    for (app,date) in set(list(secfeatures_benign.keys())).difference(allapps_benign):
        del secfeatures_benign[(app,date)]

    assert len(gfeatures_benign)==len(iccfeatures_benign) and len(iccfeatures_benign)==len(secfeatures_benign)

    allfeatures_benign = dict()
    for (app,date) in list(gfeatures_benign.keys()):
        allfeatures_benign[(app,date)] = gfeatures_benign[(app,date)] + iccfeatures_benign[(app,date)] + secfeatures_benign[(app,date)]

    benignLabels={}
    for (app,date) in list(allfeatures_benign.keys()):
        benignLabels[(app,date)] = "BENIGN"

    print (str(len(allfeatures_benign)) + " valid benign app samples to be used before removing 0 values features.")
    for (app,date) in list(allfeatures_benign.keys()):
        if sum(allfeatures_benign[(app,date)]) < 0.00005:
            del allfeatures_benign[(app,date)]
            del benignLabels[(app,date)]

    print (str(len(allfeatures_benign)) + " valid benign app samples to be used.")
    return (allfeatures_benign, benignLabels)


'''load malware features without malware family labels'''
def loadMalwareNoFamily(rootdir):
    mal_g = os.path.join(rootdir, FTXT_G)
    mal_icc = os.path.join(rootdir, FTXT_ICC)
    mal_sec = os.path.join(rootdir, FTXT_SEC)
    gfeatures_malware = load_generalFeatures(mal_g)
    iccfeatures_malware = load_ICCFeatures(mal_icc)
    secfeatures_malware = load_securityFeatures(mal_sec)

    gfeatures_apps = list(gfeatures_malware.keys())
    iccfeatures_apps = list(iccfeatures_malware.keys())
    secfeatures_apps = list(secfeatures_malware.keys())
    allapps_malware = set(gfeatures_apps).intersection(iccfeatures_apps).intersection(secfeatures_apps)
    
    for app in set(list(gfeatures_malware.keys())).difference(allapps_malware):
        del gfeatures_malware[app]
    for app in set(list(iccfeatures_malware.keys())).difference(allapps_malware):
        del iccfeatures_malware[app]
    for app in set(list(secfeatures_malware.keys())).difference(allapps_malware):
        del secfeatures_malware[app]

    assert len(gfeatures_malware)==len(iccfeatures_malware) and len(iccfeatures_malware)==len(secfeatures_malware)

    allfeatures_malware = dict()
    for app in list(gfeatures_malware.keys()):
        allfeatures_malware[app] = gfeatures_malware[app] + iccfeatures_malware[app] + secfeatures_malware[app]

    malwareLabels={}
    for app in list(allfeatures_malware.keys()):
        malwareLabels[app] = 'MALICIOUS'

    print (str(len(allfeatures_malware)) + " valid malicious app samples to be used before removing 0 values feature.")
    for app in list(allfeatures_malware.keys()):
        if sum(allfeatures_malware[app]) < 0.00005:
            del allfeatures_malware[app]
            del malwareLabels[app]
            
    print (str(len(allfeatures_malware)) + " valid malicious app samples to be used.")
    return (allfeatures_malware,malwareLabels)

def adapt (featureDict, labelDict):
    r=0
    c=None
    for app in list(featureDict.keys()):
        r+=1
        if c==None:
            c = len (featureDict[app])
            print ("feature vector length=%d" % (c))
            continue
        if c != len (featureDict[app]):
            print ("inconsistent feature vector length for app: %s --- %d" % (app, len(featureDict[app])))
        assert c == len (featureDict[app])

    features = np.zeros( shape=(r,c) )
    labels = list()
    k=0
    for app in list(featureDict.keys()):
        features[k] = featureDict[app]
        labels.append (labelDict[app])
        k+=1
    return (features, labels)

def malwareCatStat(labels):
    l2c={}
    for lab in labels:
        if lab not in list(l2c.keys()):
            l2c[lab]=0
        l2c[lab]=l2c[lab]+1
    return l2c

def selectFeatures(features, selection):
    featureSelect=[idx-1 for idx in selection]
    selectedfeatures=list()
    for featureRow in features:
        selectedfeatures.append ( featureRow[ featureSelect ] )
    return selectedfeatures
