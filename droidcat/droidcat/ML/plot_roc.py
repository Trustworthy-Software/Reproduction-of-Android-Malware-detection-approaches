# Import all classification package


from sklearn.metrics import precision_score, recall_score, f1_score, roc_auc_score, accuracy_score, auc, roc_curve

import numpy as np
import random
import os
import sys
import string
import argparse

import inspect, re
import pickle

from configs import *
from featureLoader_wdate import *

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

#for reproducible experiments uncomment these lines
#random.seed(480509637)
#np.random.seed(480509637)

def parseargs():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-type",
        "--type",
        help="det if malware detection and fam if family detection.",
        type=str,
        required=True
    )
    parser.add_argument(
        "-file",
        "--file",
        help="The name of the output roc curve file",
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

def plot_one(plt, fncurve, label_txt, **kwargs):
    f = open(fncurve, 'rb')
    try:
        (fpr, tpr, roc_auc) = pickle.load (f)
        f.close()
    except (EOFError, pickle.UnpicklingError):
        print("error loading data from %s " % fncurve, file=sys.stderr)
        return

    plt.plot(fpr, tpr, lw=2, label='%s (%0.4f)' % (label_txt, roc_auc), **kwargs)

if __name__=="__main__":
    
    args = parseargs()  #retrieve the parameters
    _type = args.type
    file_name = args.file
    SEED = args.seed
    np.random.seed(SEED)
    random.seed(SEED)
    

    plt.figure(figsize=(4,5))
    plt.rc('font', size=9)
    if _type == "det":
        plot_one (plt, "roc_curves/pickle.mal_det4", 'D1617', color='black', ls='solid')
        plot_one (plt, "roc_curves/pickle.mal_det3", 'D1415', color='blue', ls='dashed')
        plot_one (plt, "roc_curves/pickle.mal_det2", 'D1213', color='green', ls='dashdot')
        plot_one (plt, "roc_curves/pickle.mal_det1", 'D0911', color='magenta', ls='dotted')  
    elif _type == "fam":
        plot_one (plt, "roc_curves/pickle.fam_det4", 'D1617', color='black', ls='solid')
        plot_one (plt, "roc_curves/pickle.fam_det3", 'D1415', color='blue', ls='dashed')
        plot_one (plt, "roc_curves/pickle.fam_det2", 'D1213', color='green', ls='dashdot')
        plot_one (plt, "roc_curves/pickle.fam_det1", 'D0911', color='magenta', ls='dotted') 
    else:
        sys.exit("type argument is either det or fam") 

    plt.plot([0, 1], [0, 1], color='red', lw=1, linestyle='--')
    plt.xlim([0.0, 1.0])
    plt.ylim([0.0, 1.05])
    plt.xlabel('False Positive Rate')
    plt.ylabel('True Positive Rate')
    plt.legend(loc="lower right")
    plt.savefig(os.path.join("roc_curves", file_name+".plot.pdf"))
