import os
import shutil
import subprocess as sub
import shlex
import csv
from shutil import copyfile
import requests
from time import sleep
import pandas as pd
import numpy as np
import time
import sys
import signal
from concurrent.futures import ProcessPoolExecutor as PoolExecutor
from functools import partial
from subprocess import Popen
import shlex
import argparse

def parseargs():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-fh",
        "--filehashes",
        help="The path to the txt file containing the hashes",
        type=str,
        required=True
    )
    parser.add_argument(
        "-namedt",
        "--namedataset",
        help="The name of the dataset",
        type=str,
        required=True
    )
    parser.add_argument(
        "-apikey",
        "--apikey",
        help="Your AndroZoo apikey",
        type=str,
        required=True
    )
    parser.add_argument(
        "-sd",
        "--seed",
        help="The seed to fix for the experiments",
        type=int,
        required=False,
        default=np.random.randint(0, 2**32 - 1)
    )
    args = parser.parse_args()
    return args



revealdroid="."
soot="../soot"
jasmin="../jasmin"
heros="../heros"
soot_infoflow="../soot-infoflow"
soot_infoflow_android="../soot-infoflow-android"
handleFlowDroid="../handleflowdroid"
android_reflection_analysis="../android-reflection-analysis"
seal_utils="../seal-utils"

def handler(signum, frame):
    raise Exception("Time passed 5 min")
    
def list_apks(my_path_to_file_hashes):
    csv = pd.read_csv(my_path_to_file_hashes, names=["hash"]) 
    my_list = csv.values
    my_list = my_list.ravel()
    return my_list

def get_apk_from_androzoo(path_apk, my_hash):
    try:
        url= "https://androzoo.uni.lu/api/download?apikey="+str(apikey)+"&sha256="+str(my_hash)
        r = requests.get(str(url))
        if r.status_code == 200:
            apk = os.path.join(path_apk, str(my_hash)+'.apk')
            open(str(apk), 'wb').write(r.content)
        else:
            print("APK file is not found!")
    except:
        print("Retrieve APK from AndroZoo failed")
    
    
def extract_apiusage(path_apks, name_dataset, apiusage_dir, path_file_ok_api, path_file_failed_api, my_hash):
    path_apk = os.path.join(path_apks, my_hash+".apk")
    try:
        cmd = "java -Dfile.encoding=UTF-8 -classpath "+revealdroid+"/lib/axml-2.0.jar:"+revealdroid+\
        "/lib/slf4j-api-1.7.6.jar:"+revealdroid+"/lib/weka.jar:"+revealdroid+\
        "/lib/jcommander-1.36-SNAPSHOT.jar:"+soot+"/classes:"+jasmin+"/classes:"+jasmin+"/libs/java_cup.jar:"+heros+\
        "/guava-18.0.jar:"+heros+"/junit.jar:"+heros+"/org.hamcrest.core_1.3.0.jar:"+soot+"/libs/polyglot.jar:"+soot+\
        "/libs/AXMLPrinter2.jar:"+soot+"/libs/hamcrest-all-1.3.jar:"+soot+"/libs/junit-4.11.jar:"+soot+\
        "/libs/dexlib2-2.1.2-87d10dac.jar:"+soot+"/libs/util-2.1.2-87d10dac.jar:"+soot+\
        "/libs/asm-debug-all-5.0.3.jar:"+soot+":"+heros+"/target/classes:"+\
        "/plugins/org.junit_4.13.0.v20200204-1500.jar:"+soot_infoflow+"/bin:"+soot_infoflow+"/lib/cos.jar:"+soot_infoflow+\
        "/lib/j2ee.jar:"+soot_infoflow_android+"/bin:"+soot_infoflow_android+"/lib/AXMLPrinter2.jar:"+\
        soot_infoflow_android+"/lib/axml-2.0.jar:"+handleFlowDroid+"/bin:"+handleFlowDroid+":"+revealdroid+\
        "/lib/commons-io-2.4.jar:"+revealdroid+"/lib/apk-parser-all.jar:"+revealdroid+\
        "/lib/logback-core-1.1.2.jar:"+revealdroid+"/lib/logback-classic-1.1.2.jar:"+revealdroid+\
        "/src revealdroid/features/apiusage/ExtractApiUsageFeatures "+path_apk
        
        ouput = Popen(shlex.split(cmd))
        while 1:
            check = Popen.poll(ouput)
            if check is not None:
                break
        copyfile(
            os.path.join("data", "apiusage", name_dataset+"_"+str(my_hash)+"_apiusage.txt"), 
            os.path.join(apiusage_dir, str(my_hash)+"_apiusage.txt"))
        os.remove(os.path.join("data", "apiusage", name_dataset+"_"+str(my_hash)+"_apiusage.txt"))
        write_hash(my_hash, path_file_ok_api)
    except:
        print(my_hash+" apiusage failed")
        write_hash(my_hash, path_file_failed_api)

def extract_native(path_apks, name_dataset, native_dir, path_file_ok_nt, path_file_failed_nt, my_hash):
    path_apk = os.path.join(path_apks, my_hash+".apk")
    try:
        cmd = "python3 extract_native_external_calls.py "+path_apk
        ouput = Popen(shlex.split(cmd))
        while 1:
            check = Popen.poll(ouput)
            if check is not None:
                break
        copyfile(
            os.path.join("data", "native_external_calls", str(my_hash)+"_nec.txt"), 
            os.path.join(native_dir, str(my_hash)+"_nec.txt"))
        os.remove(os.path.join("data", "native_external_calls", str(my_hash)+"_nec.txt"))
        write_hash(my_hash, path_file_ok_nt)
    except:
        print(my_hash+" native failed")
        write_hash(my_hash, path_file_failed_nt)  
        


def write_hash(my_hash, path_txt_file):
    outp = open(path_txt_file, "a")
    outp.write(str(my_hash) + "\n" )
    outp.close()


    
def extract_reflect(path_apks, name_dataset, ref_dir, path_file_ok_ref, path_file_failed_ref, revealdroid_dir, reflection_dir, my_hash):
    path_apk = os.path.join(path_apks, my_hash+".apk")
    os.chdir(reflection_dir)
    try:
        cmd = "java -classpath "+android_reflection_analysis+"/bin:"+seal_utils+"/bin:"+seal_utils+\
        "/lib/logback-core-1.1.2.jar:"+seal_utils+"/lib/AXMLPrinter2.jar:"+seal_utils+"/lib/guava-18.0.jar:"+\
        seal_utils+"/lib/logback-classic-1.1.2.jar:"+soot+"/testclasses:"+soot+"/classes:"+jasmin+"/classes:"+jasmin+\
        "/libs/java_cup.jar:"+heros+"/target/classes:"+heros+"/slf4j-api-1.7.5.jar:"+heros+\
        "/slf4j-simple-1.7.5.jar:"+heros+"/junit.jar:"+heros+"/org.hamcrest.core_1.3.0.jar:"+heros+\
        "/mockito-all-1.9.5.jar:"+heros+"/guava-18.0.jar:"+soot+"/libs/polyglot.jar:"+soot+"/libs/AXMLPrinter2.jar:"+soot+\
        "/libs/hamcrest-all-1.3.jar:"+soot+"/libs/junit-4.11.jar:"+soot+"/libs/asm-debug-all-5.1.jar:"+soot+\
        "/libs/cglib-nodep-2.2.2.jar:"+soot+"/libs/java_cup.jar:"+soot+"/libs/javassist-3.18.2-GA.jar:"+soot+\
        "/libs/mockito-all-1.10.8.jar:"+soot+"/libs/powermock-mockito-1.6.1-full.jar:"+soot+\
        "/libs/jboss-common-core-2.5.0.Final.jar:"+soot+"/libs/dexlib2-2.1.2-87d10dac.jar:"+soot+\
        "/libs/util-2.1.2-87d10dac.jar:"+soot_infoflow_android+"/bin:"+soot_infoflow_android+"/lib/AXMLPrinter2.jar:"+\
        soot_infoflow_android+"/lib/commons-io-2.4.jar:"+soot_infoflow+"/bin:"+soot_infoflow+"/lib/cos.jar:"+soot_infoflow+\
        "/lib/j2ee.jar:"+soot_infoflow+"/lib/slf4j-api-1.7.5.jar:"+soot_infoflow_android+\
        "/lib/axml-2.0.jar:"+handleFlowDroid+"/bin:"+android_reflection_analysis+\
        "/lib/javatuples-1.2.jar:"+android_reflection_analysis+\
        "/lib/jgrapht-jdk1.6.jar edu.uci.seal.cases.analyses.ReflectUsageTransformer "+path_apk
        
        
        ouput = Popen(shlex.split(cmd))
        while 1:
            check = Popen.poll(ouput)
            if check is not None:
                break
        copyfile(
            os.path.join("data", str(my_hash)+"_reflect.txt"), 
            os.path.join(ref_dir, str(my_hash)+"_reflect.txt"))
        os.remove(os.path.join("data", str(my_hash)+"_reflect.txt"))
        write_hash(my_hash, path_file_ok_ref)
        os.chdir(revealdroid_dir)
    except:
        os.chdir(revealdroid_dir)
        print(my_hash+" reflection failed")
        write_hash(my_hash, path_file_failed_ref)
        
    
 
def retreiveApkFilesFromAndrozoo(path_apks, name_dataset, my_hash):
    
    if len(my_hash) == 32:
        md5 = my_hash
        try:
            my_hash = md5_sha256[my_hash]
            write_hash(md5+","+my_hash, path_file_sha256_of_md5)
            write_hash(md5, path_file_ok_md5)
        except:
            #apk is not in AndroZoo
            my_hash = None
            write_hash(md5, path_file_failed_md5)
            
    if my_hash != None:
        for attempt in range(2):
            try:
                get_apk_from_androzoo(path_apks, my_hash)
            except:
                sleep(10)
            else: #if successful, then break
                break
        if os.path.exists(os.path.join(path_apks, str(my_hash)+'.apk')):
            write_hash(my_hash, path_file_ok_apk)
            extract_apiusage(path_apks, name_dataset, apiusage_dir, path_file_ok_api, path_file_failed_api, my_hash)
            extract_native(path_apks, name_dataset, native_dir, path_file_ok_nt, path_file_failed_nt, my_hash)
            extract_reflect(
                path_apks, name_dataset, ref_dir, 
                path_file_ok_ref, path_file_failed_ref, 
                revealdroid_dir, reflection_dir, my_hash)
            os.remove(os.path.join(path_apks, my_hash+".apk"))
        else:
            write_hash(my_hash, path_file_failed_apk)

            


if __name__ == '__main__':
    args = parseargs()  #retrieve the parameters
    apk_hashes = args.filehashes
    name_dataset = args.namedataset
    apikey = args.apikey
    SEED = args.seed
    np.random.seed(SEED)
    revealdroid_dir = os.getcwd()
    os.chdir("..")
    os.chdir("android-reflection-analysis")
    reflection_dir = os.getcwd()
    os.chdir(revealdroid_dir)
    
    #dir where to download the apks
    path_apks = os.path.join(revealdroid_dir, "dataset", name_dataset)
    if not os.path.exists(path_apks):
        os.makedirs(path_apks)
    #dir of features    
    apiusage_dir = os.path.join("data", "apiusage", name_dataset)
    native_dir = os.path.join("data", "native_external_calls", name_dataset)
    ref_dir = os.path.join(reflection_dir, "data", name_dataset)
    if not os.path.exists(apiusage_dir):
        os.makedirs(apiusage_dir)
    if not os.path.exists(native_dir):
        os.makedirs(native_dir)
    if not os.path.exists(ref_dir):
        os.makedirs(ref_dir)
        
    #track the success and failure of features extraction
    path_file_ok_apk = os.path.join(path_apks, "hashes_OK_apk.txt")
    path_file_failed_apk = os.path.join(path_apks, "hashes_failed_apk.txt")
    
    path_file_ok_api = os.path.join(path_apks, "hashes_OK_api.txt")
    path_file_failed_api = os.path.join(path_apks, "hashes_failed_api.txt")
    path_file_ok_nt = os.path.join(path_apks, "hashes_OK_native.txt")
    path_file_failed_nt = os.path.join(path_apks, "hashes_failed_native.txt")
    path_file_ok_ref = os.path.join(path_apks, "hashes_OK_reflection.txt")
    path_file_failed_ref = os.path.join(path_apks, "hashes_failed_reflection.txt")
    apks = list_apks(apk_hashes)
    
    #download latest csv file from AndroZoo if you have a list of md5 
    md5_sha256 = dict()
    if len(apks[0]) == 32:
        csv_file_androzoo = pd.read_csv(os.path.expanduser('~/latest.csv')
        csv_file_androzoo = csv_file_androzoo[["sha256", "md5"]]
        keys = csv_file_androzoo["md5"].str.lower().values
        values = csv_file_androzoo["sha256"].str.lower().values
        print("keys", keys[0])
        print("values", values[0])
        zip_iterator = zip(keys, values)
        md5_sha256 = dict(zip_iterator)
        path_file_sha256_of_md5 = os.path.join(path_apks, "sha256_of_md5.txt")
        path_file_ok_md5 = os.path.join(path_apks, "hashes_OK_md5.txt")
        path_file_failed_md5 = os.path.join(path_apks, "hashes_failed_md5.txt")
    
    func = partial(retreiveApkFilesFromAndrozoo, path_apks, name_dataset)
    
    with PoolExecutor(max_workers=3) as executor:   
        for _ in executor.map(func, apks):
            pass

