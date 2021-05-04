# Import all classification package
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import precision_score, recall_score, f1_score, accuracy_score

import pandas as pd
import numpy as np
import random
import os
import sys
import string
import time
import inspect, re

from configs import *
from featureLoader_wdate import *
from common import *
import wget
import shutil


#for reproducible experiments uncomment these lines
#random.seed(480509637)
#np.random.seed(480509637)

g_binary = False # binary or multiple-class classification

def remove_malicious_and_none_fam(features, labels):
    purelabels = list()
    for app in list(features.keys()):
        purelabels.append (labels[app])
    l2c = malwareCatStat(purelabels)
    minorapps = list()
    for app in list(features.keys()):
        if (labels[app] == "MALICIOUS" or labels[app] == "none"):
            minorapps.append( app )
    for app in minorapps:
        del features[app]
        del labels[app]
    print ("%d malicious and none families pruned" % (len(minorapps)))
    return (features,labels)

def get_families(path_md5_families):
    families = {}
    metainfo = open(path_md5_families)
    for line in metainfo.readlines():
        split = line.split()
        if len(split) == 2:
            md5 = str(split[0]).strip()
            label = str(split[1]).strip()
            families[md5.lower()] = label.lower()
    return families

def holdout_bydate(model, trainfeatures, trainlabels, testfeatures, testlabels):
    predicted_labels=list()
    model.fit ( trainfeatures, trainlabels )
    print ("training: %d samples each with %d features" % (len(trainfeatures), len(trainfeatures[0])))

    for j in range(0, len(testlabels)):
        y_pred = model.predict( [testfeatures[j]] )

        predicted_labels.append ( y_pred )
    y_pred = predicted_labels
    print("Number of features used "+str(len(trainfeatures[0])))
    prec = precision_score(testlabels, y_pred, average='weighted')
    rec = recall_score(testlabels, y_pred, average='weighted')
    f1 = f1_score(testlabels, y_pred, average='weighted')
    acc = accuracy_score( testlabels, y_pred )

    return (prec, rec, f1, acc)


def split(features, labels):
    lab2dates = {}
    lab2features = {}
    for (app,date) in list(features.keys()):
        lab = labels[(app,date)]
        if lab not in list(lab2dates.keys()):
            lab2dates[lab] = []
        lab2dates[lab].append( date )
        if lab not in list(lab2features.keys()):
            lab2features[lab] = {}
        lab2features[lab][(app,date)] = features[(app,date)]

    testfeatures = {}
    testlabels = {}
    trainfeatures = {}
    trainlabels = {}

    for lab in list(lab2dates.keys()):
        alldates = lab2dates[lab]
        alldates.sort()
        pivot = alldates [ int(len(alldates)*7/10) ]

        itest = 0
        itrain = 0
        for (app,date) in list(lab2features[lab].keys()):
            if date > pivot:
                itest += 1
            else:
                itrain += 1

        # if all samples' dates are the same, then use ordinary random split
        if itest<1 or itrain<1:
            print("applying random split for "+lab+" ...", file=sys.stdout)
            idxrm=[]
            for j in range(0, int(len(alldates)*7/10)):
                t = random.randint(0,len(list(lab2features[lab].keys()))-1)
                idxrm.append(t)
                key = list(lab2features[lab].keys())[t]

                trainfeatures[ key ] = features [ key ]
                trainlabels [key] = labels [key]

            for i in range(0, len(list(lab2features[lab].keys()))):
                if i not in idxrm:
                    key = list(lab2features[lab].keys())[i]
                    testfeatures[key] = features [key]
                    testlabels [key] = labels [key]
        else:
            print("applying split by date for "+lab+" ...", file=sys.stdout)
            for (app,date) in list(lab2features[lab].keys()):
                key = (app,date)
                if date > pivot:
                    testfeatures[key] = features [key]
                    testlabels [key] = labels [key]
                else:
                    trainfeatures[ key ] = features [ key ]
                    trainlabels [key] = labels [key]

    print ("%d samples for training, %d samples held out will be used for testing" % (len (trainfeatures), 
                                                                                      len(testfeatures)), 
           file=sys.stdout)

    return trainfeatures, trainlabels, testfeatures, testlabels

def predict(f, l, fh, i):
    _trainfeatures, _trainlabels, _testfeatures, _testlabels = split(f, l)

    #ensure features vectors have same length
    (trainfeatures, trainlabels) = adapt (_trainfeatures, _trainlabels)
    (testfeatures, testlabels) = adapt (_testfeatures, _testlabels)
    
    labels = list()
    for item in trainlabels:
        labels.append(item)
    for item in testlabels:
        labels.append(item)

    l2c = malwareCatStat(labels)
    for lab in list(l2c.keys()):
        print ("%s\t%s" % (lab, l2c[lab]))
    print ("%d classes in total" % len(list(l2c.keys())))

    uniqLabels = set()
    for item in labels:
        uniqLabels.add (item)

    models = (RandomForestClassifier(n_estimators = 128, random_state=0),)
    datatag = 'fam_det1' if i==0 else ('fam_det2' if i==1 else ('fam_det3' if i==3 else 'fam_det4'))
    #use 70 selected features
    fset = FSET_YYY
    
    model2ret={}
    for model in models:
        print( 'model ' + str(type(model).__name__), file=fh)
        roc_bydate(g_binary, 
                   models[0], 
                   selectFeatures(trainfeatures, fset), 
                   trainlabels, 
                   selectFeatures(testfeatures, fset), 
                   testlabels, 
                   datatag)
        ret = holdout_bydate(model, 
                             selectFeatures(trainfeatures, fset), 
                             trainlabels, 
                             selectFeatures(testfeatures, fset), 
                             testlabels)
        model2ret[str(model)] = ret

    tlabs=('precision', 'recall', 'F1', 'accuracy')
    for i in (0,1,2,3):
        print (tlabs[i], file=fh)
        cols=list()
        for model in models:
            col=list()
            ret = model2ret[str(model)]
            col.append(ret[i])
            cols.append(col)
        for r in range(0,len(cols[0])):
            for c in range(0,len(cols)):
                print( "%s\t" % cols[c][r], file=fh)
    print("*"*80)

if __name__=="__main__":
    apikey = APIKEY
    g_binary = False
    #load AndroZoo CSV file to get the sha256 of apps
    path_csv_andrz = os.path.expanduser("~/latest.csv")
    csv_file_andrz = pd.read_csv(path_csv_andrz)
    keys_csv = csv_file_andrz["sha256"].values
    values_csv = csv_file_andrz["md5"].values
    keys_csv = [str(i).lower() for i in keys_csv]
    values_csv = [str(i).lower() for i in values_csv]
    csv_file_andrz = ""
    zip_iterator = zip(keys_csv, values_csv)
    sha_dictionary = dict(zip_iterator)
    path_features = os.path.expanduser("features_droidcat_byfirstseen")
    datasets = [ \
                {"malware":["vs2010","vs2011", "zoo2010", "zoo2011"]},
                {"malware":["vs2012","vs2013", "zoo2012", "zoo2013"]},
                {"malware":["vs2014","vs2015", "zoo2014", "zoo2015"]},
                {"malware":["vs2016","more2017", "zoo2016", "zoo2017"]}
                ]

    fh = sys.stdout
    #mfam contains all the apps and their families from md5families/
    mfam = {}
    md5_with_two_diff_labels = []
    all_files_fam = os.listdir("md5families/")
    for file_fam in all_files_fam:
        fnFam = "md5families/"+file_fam
        #return dict with apps: labels
        sub_mfam = get_families(fnFam)
        
        #find apps that have two different labels
        inter = [i for i in list(sub_mfam.keys()) if i in list(mfam.keys())]
        if len(inter)!= 0:
            for j in inter:
                if sub_mfam[j] != mfam[j]:
                    md5_with_two_diff_labels.append(j)
        mfam.update(sub_mfam)
            
    mfam_keys = [i for i in list(mfam.keys())]
    
    #malware-2017-more.txt file provides labels with sha256 
    fnFam = "md5families/malware-2017-more.txt"
    mfam_2017 = get_families(fnFam)
    mfam_2017_keys = [i for i in list(mfam_2017.keys())]
    dir_app = "droidcat_temp_fam"
    count_total=0
    for i in range(0, len(datasets)):
        count_err, count_success = 0, 0
        g_fnames=set()
        print ("work on %s ... " % ( datasets[i] ))
        (bft, blt) = ({}, {})
        for k in range(0, len(datasets[i]['malware'])):
            print ("----work on %s ... " % ( datasets[i]['malware'][k] ))
            (mf, ml) = loadMalwareNoFamily(path_features+"/"+datasets[i]['malware'][k])
            bft.update(mf)
            print ("-------------number of samples %s ... " % ( len(list(mf.keys())) ))
            newfam  = {}
            newfam.update(ml)
            for (app,date) in list(ml.keys()):
                #malware2017 labels are provided with sha256
                if datasets[i]['malware'][k] != "more2017":
                    try:
                        #serach for the labels in the file of the same year first
                        fnFam_current = "md5families/"+datasets[i]['malware'][k]+".txt"
                        mfam_current = get_families (fnFam_current)
                        mfam_current_keys = [i for i in list(mfam_current.keys())]
                        try:
                            #get md5 of the app from AndroZoo CSV file if it exists
                            app_md5 = sha_dictionary[app[:-4].lower()]
                        except:
                            #else get md5 of the app from AndroZoo by downloading the app
                            if not os.path.exists(dir_app):
                                os.makedirs(dir_app)
                            else:
                                shutil.rmtree(dir_app, ignore_errors=True)
                                os.makedirs(dir_app)
                            url = "https://androzoo.uni.lu/api/download?apikey="+apikey+"&sha256="+app[:-4]
                            wget.download(url, dir_app)
                            app_md5 = os.popen("md5sum "+os.path.join(dir_app, app[:-4].upper()+".apk")).read().split(" ")[0] 
                            shutil.rmtree(dir_app, ignore_errors=True)
                        #use the md5 to find the label
                        if app_md5.lower() in mfam_current_keys:
                            count_total+=1
                            count_success+=1
                            newfam[(app,date)] = mfam_current[app_md5.lower()]
                            continue
                    except:
                        pass
                #else if dataset is malware-2017, find directly the label with sha256 if it exists
                elif app[:-4].lower() in mfam_2017_keys:
                    count_total+=1
                    count_success+=1
                    newfam[(app,date)] = mfam_2017[app[:-4].lower()]
                    continue
                
                #else search for the labels in the other existing family labels files
                try:
                    app_md5 = sha_dictionary[app[:-4].lower()]
                except:
                    if not os.path.exists(dir_app):
                        os.makedirs(dir_app)
                    else:
                        shutil.rmtree(dir_app, ignore_errors=True)
                        os.makedirs(dir_app)
                    url = "https://androzoo.uni.lu/api/download?apikey="+apikey+"&sha256="+app[:-4]
                    wget.download(url, dir_app)
                    app_md5 = os.popen("md5sum "+os.path.join(dir_app, app[:-4].upper()+".apk")).read().split(" ")[0] 
                    shutil.rmtree(dir_app, ignore_errors=True)

                if (app_md5.lower() in md5_with_two_diff_labels):
                    count_err+=1
                    print ("hash with two families %s %s" % (app, app_md5) )
                    
                elif app_md5.lower() in mfam_keys:
                    count_total+=1
                    count_success+=1
                    newfam[(app,date)] = mfam[app_md5.lower()]
                    
                #last try with mfam_2017 that are provided with sha256
                elif ((datasets[i]['malware'][k] != "more2017") and (app[:-4].lower() in mfam_2017_keys)):
                    count_total+=1
                    count_success+=1
                    newfam[(app,date)] = mfam_2017[app[:-4].lower()]

                else:
                    count_err+=1
                    print ("error finding family for %s %s" % (app, app_md5))
                    
            print("number of classes "+str(len(list(set(newfam.values())))))
            blt.update(newfam)

        print("Total number of samples", count_total)

        remove_malicious_and_none_fam(bft, blt)
        predict(bft, blt, fh, i)

    fh.flush()
    fh.close()

    sys.exit(0)
