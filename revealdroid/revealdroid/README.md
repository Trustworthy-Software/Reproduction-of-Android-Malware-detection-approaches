# README #


## Dependencies and Building ##

Please see the instructions in https://bitbucket.org/joshuaga/revealdroid/src/master/


## Download the apps and extract the features

Please use load_apk_and_extract_features.py script.

It needs:

* list of hashes

* name of your dataset = namedt

* AndroZoo apikey

The script downloads the apps from AndroZoo and extract the features that stores in:

* data/apiusage/namedt 

* data/native_external_calls/namedt

* android-reflection-analysis/data/namedt
