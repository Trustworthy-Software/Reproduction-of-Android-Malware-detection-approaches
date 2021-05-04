#!/usr/bin/python

import os

'''
feature text files
'''

FTXT_G = "gfeatures.txt"
FTXT_ICC = "iccfeatures.txt"
FTXT_SEC = "securityfeatures.txt"

'''
AndroZoo APIKEY
'''

APIKEY = "PutYourAPIKEYHere"

#these benign apps are found malicious by VirusTotal, will be excluded from the training data set
malbenignapps=["com.ictap.casm", "com.aob", "com.vaishnavism.vishnusahasranaamam.english", "com.hardcoreapps.loboshaker"]


'''
Feature sets
'''

FSET_YYY = [1,2,3,10,13,16,19,37,39,41,53,55,57,58,59,60,61,63,73,74,75,76,78,80,81,82,83,84,93,94,95,96,105,106,117,118, 11,14,22,24,30,35,38,40,43,44,54,56,62,64,65,67,86,88,99,100,101,102,103,104,107,108, 12,15,23,28,32,34,36,42]

FSET_NAMES={str(FSET_YYY):"FSET_YYY"}