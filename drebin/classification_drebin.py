import pandas as pd
import numpy as np
import time
from sklearn.model_selection import train_test_split
from sklearn.feature_extraction.text import CountVectorizer as CountV
from sklearn.svm import LinearSVC
import os
from matplotlib import pyplot as plt
from sklearn.metrics import roc_curve, auc
from sklearn.metrics import recall_score, accuracy_score
from sklearn.utils import shuffle
import CommonModules as CM
import sys
import argparse
"""
This script performs Drebin's classification using SVM LinearSVC classifier.
Inputs are described in parseargs function.
Recall and Accuracy are calculated 10 times. Each experiment is performed with 66% of training set and 33% of test set, and scores are averaged.
Roc curve is plotted using the last trained classifier from the 10 experiments.
The Outputs are the results text file, and roc curve pdf graph that are located in the drebin directory.
"""


def parseargs():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-md",
        "--maldir",
        help="The path to the .data malware files directory",
        type=str,
        required=True
    )
    parser.add_argument(
        "-gd",
        "--goodir",
        help="The path to the .data goodware files directory",
        type=str,
        required=True
    )
    parser.add_argument(
        "-fs",
        "--filescores",
        help="The name of the file where the results will be written",
        type=str,
        required=True
    )
    parser.add_argument(
        "-roc",
        "--roc",
        help="The name of the file where the roc curve will be saved",
        type=str,
        required=True
    )
    parser.add_argument(
        "-sd",
        "--seed",
        help=
        "(Optional) The random seed used for the experiments (useful for 100% identical results replication)",
        type=int,
        required=False,
        default=np.random.randint(0, 2**32 - 1)
    )
    args = parser.parse_args()
    return args


def Classification(MalwarePath, GoodwarePath, file_scores, ROC_GraphName):
    Malware = MalwarePath
    Goodware = GoodwarePath

    # the list of absolute paths of the ".data" malware files
    AllMalSamples = CM.ListFiles(MalwarePath, Extension)

    # the list of absolute paths of the ".data" goodware files
    AllGoodSamples = CM.ListFiles(GoodwarePath, Extension)
    AllSampleNames = AllMalSamples + AllGoodSamples  #combine the two lists

    FeatureVectorizer = CountV(
        input='filename',
        tokenizer=lambda x: x.split('\n'),
        token_pattern=None,
        binary=True
    )
    Mal_labels = np.ones(len(AllMalSamples))  #label malware as 1
    Good_labels = np.empty(len(AllGoodSamples))
    Good_labels.fill(-1)  # label goodware as -1
    # concatenate the two lists of labels
    y = np.concatenate((Mal_labels, Good_labels), axis=0)
    scoresRec, scoresAcc = [], []  # empty lists to store recall and accuracy
    # The experiment is run 10 times
    for i in range(10):
        # shuffle the list in each experiment
        AllSampleNames, y = shuffle(AllSampleNames, y)
        # split the lists into 66% for training and 33% for test.
        x_train, x_test, y_train, y_test = train_test_split(
            AllSampleNames, y, test_size=0.33
        )
        # learn the vocabulary dictionary from the training set
        x_train = FeatureVectorizer.fit_transform(x_train)
        #transform the test set using vocabulary learned
        x_test = FeatureVectorizer.transform(x_test)
        clf = LinearSVC(max_iter=2000)  #create the classifier
        clf.fit(x_train, y_train)  #fit (=train) the training data
        y_predict = clf.predict(x_test)  #predict the labels of test data
        scoreRec = recall_score(y_test, y_predict)  #calculate the recall score
        #calculate the accuracy score
        scoreAcc = accuracy_score(y_test, y_predict)
        #store the calculated recall in the list of recall scores
        scoresRec.append(scoreRec)
        #store the calculated accuarcy in the list of accuracy scores
        scoresAcc.append(scoreAcc)

    #write the results into the results-run.txt file
    outp = open(file_scores, "w")
    outp.write(
        "Recall scores of classifiaction with 0.66 training and 0.33 test\n"
    )
    outp.write(str((scoresRec)) + "\n")
    outp.write(str((np.mean(scoresRec))) + "\n")  #store the mean of the recall
    outp.write(
        "Accuracy scores of classifiaction with 0.66 training and 0.33 test\n"
    )
    outp.write(str((scoresAcc)) + "\n")  #store the mean of the accuracy
    outp.write(str((np.mean(scoresAcc))) + "\n")
    outp.close()

    #last classifier of the 10 experiments is chosen for the plot of ROC curve, and used to predict confidence scores
    y_score = clf.decision_function(x_test)
    #create empty dictionaries for false positive rate, true positive rate
    fpr, tpr = dict(), dict()
    fpr, tpr, _ = roc_curve(y_test, y_score)  #compute the ROC
    plt.figure(figsize=(5, 4))
    plt.title('Receiver Operating Characteristic')
    plt.grid(color='k', linestyle=':')
    plt.plot(fpr, tpr, '-k')  #plot the roc curve
    plt.xlim([0, 0.1])
    plt.ylim([0, 1])
    plt.ylabel('True Positive Rate')
    plt.xlabel('False Positive Rate')
    #save the roc curve in roc.pdf file
    plt.savefig(ROC_GraphName + ".pdf", bbox_inches='tight')
    plt.show()


Extension = ".data"  #features files that are generated by GetApkData.py script ends with ".data"

if __name__ == '__main__':
    Args = parseargs()  #retrieve the parameters
    MalwarePath = Args.maldir
    GoodwarePath = Args.goodir
    file_scores = Args.filescores
    ROC_GraphName = Args.roc
    SEED = Args.seed
    np.random.seed(SEED)
    Classification(MalwarePath, GoodwarePath, file_scores, ROC_GraphName)
