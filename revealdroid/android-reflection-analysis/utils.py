#!/usr/bin/env python

import numpy as np
import os
import sys
import time
import chardet
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from sklearn.model_selection import KFold
from sklearn import metrics 
from sklearn.feature_extraction import DictVectorizer
from sklearn import tree
from sklearn.svm import LinearSVC
from sklearn.metrics import precision_recall_curve, auc


def run_cv_with_prc_curve(clf, y, X, numfolds, prc_filename):
    kf = KFold(n_splits=numfolds, shuffle=True)
    lw = 2
    y_real = []
    y_proba = []
    
    precision_sc_mal,precision_sc_good = [], []
    recall_sc_mal, recall_sc_good = [], []
    f1_sc_mal, f1_sc_good = [], []

    labels = {"goodware": 0, "malware": 1}
    for i, (train,test) in enumerate(kf.split(X)):
        print("k= "+str(i))
        y_train_encoded = np.array(list(map(labels.get, np.array(y)[train])))
        y_test_encoded = np.array(list(map(labels.get, np.array(y)[test])))
        model = clf.fit(X[train], y_train_encoded)
        
        y_predict = model.predict(X[test])
        
        precision_sc_mal.append(metrics.precision_score(y_test_encoded, y_predict, pos_label=1))
        precision_sc_good.append(metrics.precision_score(y_test_encoded, y_predict, pos_label=0))

        recall_sc_mal.append(metrics.recall_score(y_test_encoded, y_predict, pos_label=1))
        recall_sc_good.append(metrics.recall_score(y_test_encoded, y_predict, pos_label=0))
        
        f1_sc_mal.append(metrics.f1_score(y_test_encoded, y_predict, pos_label=1))
        f1_sc_good.append(metrics.f1_score(y_test_encoded, y_predict, pos_label=0))
        
        print("precision mal is: "+str(metrics.precision_score(y_test_encoded, y_predict, pos_label=1)))
        print("recall mal is: "+str(metrics.recall_score(y_test_encoded, y_predict, pos_label=1)))
        print("f1 mal is: "+str(metrics.f1_score(y_test_encoded, y_predict, pos_label=1)))

        print("precision good is: "+str(metrics.precision_score(y_test_encoded, y_predict, pos_label=0)))
        print("recall good is: "+str(metrics.recall_score(y_test_encoded, y_predict, pos_label=0)))
        print("f1 good is: "+str(metrics.f1_score(y_test_encoded, y_predict, pos_label=0)))
        
        y_real.append(y_test_encoded)
        y_proba.append(model.decision_function(X[test]))
    print("-"*40)
    print("Average precision mal is: "+str(np.mean(precision_sc_mal)))
    print("Average recall mal is: "+str(np.mean(recall_sc_mal)))
    print("Average f1 mal is: "+str(np.mean(f1_sc_mal)))
    
    print("Average precision good is: "+str(np.mean(precision_sc_good)))
    print("Average recall good is: "+str(np.mean(recall_sc_good)))
    print("Average f1 good is: "+str(np.mean(f1_sc_good)))
          
    fig = plt.figure(1)
    y_real = np.concatenate(y_real)
    y_proba = np.concatenate(y_proba)
    precision, recall, thresholds = precision_recall_curve(y_real, y_proba, pos_label=1)
    auc_precision_recall = auc(recall, precision)
    label = 'Overall AUC={:.02f}'.format(auc_precision_recall) 
    plt.plot(recall, precision, color="b", lw=2, label=label)
    plt.xlabel('Recall')
    plt.ylabel('Precision')
    plt.ylim([0.0,1.0])
    plt.xlim([0.0,1.0])
    plt.legend(loc="lower left")
    plt.savefig(prc_filename+".pdf")
    plt.clf() 
    fig.clf()

    
def select_classifier(c):
    if c == 'd':
        print ('selecting decision tree')
        sys.stdout.flush()
        clf = tree.DecisionTreeClassifier()
    elif c == 'l':
        print ('selecting linear SVC')
        sys.stdout.flush()
        clf = LinearSVC(C=0.01, penalty="l1", dual=False)
    return clf


def create_dataset_native(apk_names, class_apps, native_dir):
    dataset=[]
    for f in apk_names:
        feat_vectors = dict()
        feat_vectors['apk_name'], feat_vectors['label'] = f, class_apps
        file = open(os.path.join(native_dir, f+"_nec.txt"), "r")
        for line in file:
            tokens = line.rstrip('\n').split(',')
            feat_name = tokens[0]
            feat_val = tokens[1]
            if chardet.detect(feat_name.encode())['encoding'] is None:
                feat_name = 'native_function_name_with_unknown_encoding'
            if feat_name != 'apk_name':
                feat_vectors[feat_name] = int(feat_val)
        file.close()
        #fv['label']='unknown' ????
        dataset.append(feat_vectors)
    return dataset

def create_dataset_apiusage(apk_names, class_apps, apiusage_dir):
    dataset = []
    for f in apk_names:
        feat_vectors = dict()
        feat_vectors['apk_name'], feat_vectors['label'] = f, class_apps
        file = open(os.path.join(apiusage_dir, f+"_apiusage.txt"), "r")
        for line in file:
            tokens = line.rstrip('\n').split(',')
            pkg = tokens[0]
            count = tokens[1]
            feat_vectors[pkg]=int(count)
        file.close()
        dataset.append(feat_vectors)
    return dataset

def create_dataset_reflection(apk_names, class_apps, reflection_feat_dir):
    dataset = []
    for f in apk_names:
        feat_vectors = dict()
        feat_vectors['apk_name'], feat_vectors['label'] = f, class_apps
        file = open(os.path.join(reflection_feat_dir, f+"_reflect.txt"), "r")
        for line in file:
            tokens = line.rstrip('\n').split(',')
            categ = tokens[0]
            count = tokens[1]
            feat_vectors[categ]=int(count)
        file.close()
        dataset.append(feat_vectors)
    return dataset

def create_dataset(name_data, class_data):
    prog_start_time=time.time()
    reflection_dir = os.getcwd()
    os.chdir("..")
    os.chdir("revealdroid")
    revealdroid_dir = os.getcwd()
    os.chdir(reflection_dir)
    
    apiusage_dir = os.path.join(revealdroid_dir, "data/apiusage", name_data)
    reflection_feat_dir = os.path.join(reflection_dir, "data", name_data)
    native_dir = os.path.join(revealdroid_dir, "data/native_external_calls", name_data)
    
    #Handling apiusage_dir
    if (os.path.exists(apiusage_dir) and os.path.isdir(apiusage_dir)):
        if not os.listdir(apiusage_dir):
            print("apiusage directory is empty")
    else:
        print("apiusage directory don't exists")
        return
    #Handling reflection_feat_dir
    if (os.path.exists(reflection_feat_dir) and os.path.isdir(reflection_feat_dir)):
        if not os.listdir(reflection_feat_dir):
            print("reflection directory is empty")
    else:
        print("reflection directory don't exists")
        parser.print_help()
        return
    #Handling native_dir
    if (os.path.exists(native_dir) and os.path.isdir(native_dir)):
        if not os.listdir(native_dir):
            print("native directory is empty")
    else:
        print("native directory don't exists")
        parser.print_help()
        return
    
    
    files_apiusage = os.listdir(apiusage_dir)
    files_reflection = os.listdir(reflection_feat_dir)
    files_native = os.listdir(native_dir)
    
    
    apk_names_apiusage = [f.split("_")[0] for f in files_apiusage]
    apk_names_reflection = [f.split("_")[0] for f in files_reflection]
    apk_names_native = [f.split("_")[0] for f in files_native]
    apk_names = [f for f in apk_names_apiusage if f in apk_names_reflection and f in apk_names_native]
    print("number of apps", len(apk_names))
    
    dataset_apiusage = create_dataset_apiusage(apk_names, class_data, apiusage_dir)
    dataset_reflection = create_dataset_reflection(apk_names, class_data, reflection_feat_dir)
    dataset_native = create_dataset_native(apk_names, class_data, native_dir)
    
    y = [class_data] * len(apk_names)
    
    merged_dataset = []
    for i in range(len(dataset_apiusage)):
        merged_feat_of_app = {}
        dataset_apiusage[i].pop("apk_name")
        dataset_apiusage[i].pop("label")
        dataset_reflection[i].pop("apk_name")
        dataset_reflection[i].pop("label")
        dataset_native[i].pop("apk_name")
        dataset_native[i].pop("label")
        merged_feat_of_app.update(dataset_apiusage[i])
        merged_feat_of_app.update(dataset_reflection[i])
        merged_feat_of_app.update(dataset_native[i])
        merged_dataset.append(merged_feat_of_app)
    return merged_dataset, y, apk_names