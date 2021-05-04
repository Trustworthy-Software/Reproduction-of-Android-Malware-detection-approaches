from sklearn.multiclass import OneVsRestClassifier
from sklearn.preprocessing import label_binarize
import numpy as np
from sklearn.metrics import precision_score, recall_score, f1_score, accuracy_score, auc, roc_curve
import pickle
import os
import random

#for reproducible experiments uncomment these lines
#random.seed(480509637)
#np.random.seed(480509637)

def roc_bydate(g_binary, model, trainfeatures, trainlabels, testfeatures, testlabels, datatag):
    m = model
    if not os.path.exists("roc_curves"):
        os.makedirs("roc_curves")
    fnTarget = 'roc_curves/pickle.'+datatag

    labels=set()
    for l in trainlabels: labels.add(l)
    for l in testlabels: labels.add(l)
    labels = np.array(list(labels))
    trainlabels = label_binarize(trainlabels, classes=labels)
    testlabels = label_binarize(testlabels, classes=labels)
    n_classes = len(labels)
    if not g_binary:
        m = OneVsRestClassifier(model)
        m_fitted = m.fit(trainfeatures, trainlabels)
    else:
        m_fitted = m.fit(trainfeatures, np.ravel(trainlabels))
    test_score = m_fitted.predict_proba(np.array(testfeatures))
    
    if not g_binary:
        fpr, tpr, _ = roc_curve(testlabels.ravel(), test_score.ravel())
        roc_auc = auc(fpr, tpr)
    
    else:
        fpr, tpr, _ = roc_curve(testlabels, test_score[:,1])
        roc_auc = auc(fpr, tpr)

    fhTarget = open(fnTarget, 'wb')
    pickle.dump((fpr, tpr, roc_auc), fhTarget)
    fhTarget.close()

