#!/usr/bin/env python

#!/usr/bin/env python

import time
import argparse
import numpy as np
import utils
import sys
import pandas as pd
import os
from sklearn.model_selection import KFold
from sklearn import metrics
from sklearn.feature_extraction import DictVectorizer

from sklearn import tree
from sklearn.svm import LinearSVC


def main():
    start_time = time.time()
    parser = argparse.ArgumentParser(description='run cross-validation for family classification')
    parser.add_argument('--filelabels',
                        help='path to the file that contains hashes and the corresponding families separated by space " "')
    parser.add_argument('--malware', 
                        nargs='+', 
                        help="""name of malware dataset (or datasets) to use. \ 
                        The extracted features should be located in data/apiusage data/native_external_calls and \ 
                        ../android-reflection-analysis/data""" , 
                        required=True)
    parser.add_argument('-f',
                        help='number of folds for cross-validation',
                        type=int,
                        default=10)
    parser.add_argument('-c',
                        metavar='C',
                        default='d',
                        help='select a classifier: d for decision tree (the default), l for linear SVC')
    parser.add_argument("-sd",
                        "--seed",
                        help="The seed to fix for the experiments",
                        type=int,
                        required=False,
                        default=np.random.randint(0, 2**32 - 1))
    args = parser.parse_args()

    malware_data = args.malware
    all_data = dict()

    for name_data in malware_data:
        print("Process "+ name_data)
        #merged_dataset is a list that has for each app a dict of all the features of that app
        merged_dataset, _, apk_names = utils.create_dataset(name_data, "")
        all_data[name_data] = [merged_dataset, apk_names]
               
    #merge the feat of all the apps
    list_of_all_dict, name_of_all_apps = [], []
    for key in list(all_data.keys()):
        list_of_all_dict+=all_data[key][0]
        name_of_all_apps+=all_data[key][1]
        
    malware_to_process = dict()
    #read the files of family lables
    read_file = open(args.filelabels, "r")
    for line in read_file:
        malware_to_process[line.split(" ")[0]] = line.strip("\n").split(" ")[1]
    read_file.close()
    
    #keep only the apps that have their families in the file
    dict_to_use, y_to_use, name_of_apps_to_use = [], [], []
    for app in list(malware_to_process.keys()):
        if not malware_to_process[app].startswith("SINGLETON"):
            try: 
                my_index = name_of_all_apps.index(app)
                dict_to_use.append(list_of_all_dict[my_index])
                y_to_use.append(malware_to_process[app])
                name_of_apps_to_use.append(app)
            except:
                pass
           
    print("converting now to matrix")
    vec = DictVectorizer()
    X = vec.fit_transform(dict_to_use)
    number_labels = len(list(set(y_to_use)))

    print("number of apps", len(name_of_apps_to_use))
    print("number of labels", number_labels)
    print ("running {}-fold cv".format(args.f))
    kf = KFold(n_splits=10, shuffle=True)
    accuracies = []
    y = np.array(y_to_use)
    for i, (train,test) in enumerate(kf.split(X)):
        clf = utils.select_classifier(args.c)
        model = clf.fit(X[train],y[train])        
        y_predict = model.predict(X[test])
        accuracies.append(metrics.accuracy_score(y[test], y_predict))  
        print("accuracy is: "+str(metrics.accuracy_score(y[test], y_predict)))
    
    print("-"*40)
    print("Average accuracies is: "+str(np.mean(accuracies)))
    print ('total execution time: {}'.format(time.time()-start_time))

if __name__ == '__main__':
    main()


