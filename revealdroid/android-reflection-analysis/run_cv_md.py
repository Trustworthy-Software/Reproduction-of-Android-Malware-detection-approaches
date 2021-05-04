#!/usr/bin/env python

import time
import argparse
import numpy as np
import utils
import sys
import pandas as pd
import os
from sklearn.feature_extraction import DictVectorizer
import chardet
from sklearn.svm import LinearSVC

def main():
    start_time = time.time()

    parser = argparse.ArgumentParser(description='run cross-validation for malware detection')
    parser.add_argument('--malware', 
                        nargs='+', 
                        help="""name of malware dataset (or datasets) to use. \ 
                        The extracted features should be located in data/apiusage data/native_external_calls and \ 
                        ../android-reflection-analysis/data""" , 
                        required=True)
    parser.add_argument('--goodware', 
                        nargs='+', 
                        help="""name of goodware dataset (or datasets) to use. \ 
                        The extracted features should be located in data/apiusage data/native_external_calls and \ 
                        ../android-reflection-analysis/data""", 
                        required=True)
    parser.add_argument('-f',
                        help='number of folds for cross-validation',
                        type=int,
                        default=10)
    parser.add_argument('--filename',
                        help='name to be used to save the roc curve',
                        type=str)
    parser.add_argument('-c',
                        metavar='C',
                        default='l',
                        help= "select a classifier: d for decision tree, l for linear SVC")
    parser.add_argument("-sd",
                        "--seed",
                        help="The seed to fix for the experiments",
                        type=int,
                        required=False,
                        default=np.random.randint(0, 2**32 - 1))
    
    args = parser.parse_args()

    malware_data = args.malware
    goodware_data = args.goodware
    all_data = dict()

    #load all malware and goodware features
    for name_data in malware_data:
        print("Process "+ name_data)
        #merged_dataset is a list that has for each app a dict of all the features of that app
        merged_dataset, y, apk_names = utils.create_dataset(name_data, "malware")
        all_data[name_data] = [merged_dataset, y, apk_names]
    for name_data in goodware_data:
        print("Process "+ name_data)
        merged_dataset, y, apk_names = utils.create_dataset(name_data, "goodware")
        all_data[name_data] = [merged_dataset, y, apk_names]
    #merge the feat of all the apps
    list_of_all_dict, y_of_all_apps, name_of_all_apps = [], [], []
    for key in list(all_data.keys()):
        list_of_all_dict+=all_data[key][0].copy()
        y_of_all_apps+=all_data[key][1]
        name_of_all_apps+=all_data[key][2]
    
    print("converting now to matrix")
    vec = DictVectorizer()
    X = vec.fit_transform(list_of_all_dict)
    fnames = vec.get_feature_names()
    new_fnames = []
    nonascii_encoding_feature_count=0
    for feat_name in fnames:
        if chardet.detect(feat_name.encode())['encoding'].strip() != 'ascii' :
            orig_feat_name=feat_name
            feat_name='nonascii_encoding_feature{}'.format(nonascii_encoding_feature_count) 
            print ('Detected feature name with nonascii encoding. Found encoding {}. The feature {} has been renamed to {}'.format(chardet.detect(feat_name.encode())['encoding'],orig_feat_name,feat_name))
        new_fnames.append(feat_name)
        nonascii_encoding_feature_count=nonascii_encoding_feature_count+1

    print ('dataset shape: {}'.format(X.shape))     
    print ("running {}-fold cv".format(args.f))
    sys.stdout.flush()
    clf = utils.select_classifier(args.c)
    file_name = args.filename
    
    utils.run_cv_with_prc_curve(clf, y_of_all_apps, X, numfolds = args.f, prc_filename = file_name)

    print ('total execution time: {}'.format(time.time()-start_time))

if __name__ == '__main__':
    main()
