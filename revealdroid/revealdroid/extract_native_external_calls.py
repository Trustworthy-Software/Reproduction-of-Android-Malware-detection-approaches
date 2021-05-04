#!/usr/bin/env python

import argparse
import zipfile
import os
import magic
import subprocess
import re
import time

parser = argparse.ArgumentParser(description='Extract native-code features from apk')
parser.add_argument('apk',help='apk file location')
args = parser.parse_args()
print (args.apk)

apk_f = zipfile.ZipFile(args.apk,"r")

basename = os.path.basename(args.apk)

DATA_NATIVE_FILES="data/native_files"
apk_native_dir=DATA_NATIVE_FILES + "/" + basename

try:
    objdump_command = subprocess.check_output(["ndk-which","objdump"])
    objdump_command = objdump_command.strip()
except subprocess.CalledProcessError as e:
    print ("command '{}' return with error (code {}): {}".format(e.cmd, e.returncode, e.output))

if not os.path.exists(apk_native_dir):
    os.makedirs(apk_native_dir)

start_time=time.time()
disasm_f = ''
apk_name=os.path.basename(args.apk)
calls = dict()
for name in apk_f.namelist():
    file_info = magic.from_buffer(apk_f.read(name))
    if "ELF" in file_info:
        print ("Extracting {}".format(file_info))
        apk_f.extract(name,apk_native_dir)
        print ("Running {} -d on {}".format(objdump_command,name))
        try:
            disasm_f = subprocess.check_output([objdump_command,"-d","-marm",apk_native_dir+"/"+name]) 
            #print (disasm_f) 

            for line in disasm_f.split(b'\n'):
                if line.strip().endswith(b'.apk'):
                    apk_name=os.path.basename(line.strip())
                    print ("apk: " + apk_name)
                m_ext_call = re.match(b".*\s+(b.*?)\s+.*<(.+)(@|\+)(.*)>.*",line) # if I see a branch instruction referencing the PLT
                #if not m_ext_call:
                    #m_plt_label = re.match(r".*\s+<(.+)@plt.*>",line) # for handling the PLT section directly
                if m_ext_call:
                    #print m_ext_call.group(2)
                    if m_ext_call.group(2) in calls:
                        calls[m_ext_call.group(2)] += 1
                    else:
                        calls[m_ext_call.group(2)] = 1
                # if m_plt_label:
                #     if m_plt_label.group(1) in calls:
                #         calls[m_plt_label.group(1)] += 1
                #     else:
                #         calls[m_plt_label.group(1)] = 1
            
        except subprocess.CalledProcessError as e:
            print ("command '{}' return with error (code {}): {}".format(e.cmd, e.returncode, e.output))
print ('native extraction time: {}'.format(time.time()-start_time))

for k,v in calls.items():
    print ("{}: {}".format(k,v))

apk_filename,apk_ext=os.path.splitext(apk_name)
nec_out_file=apk_filename+"_nec.txt"
nec_dir=os.path.join("data","native_external_calls")
if not os.path.exists(nec_dir):
    os.makedirs(nec_dir)

nec_out_f=open(os.path.join(nec_dir,nec_out_file),'w')
nec_out_f.write("apk_name," + apk_name + "\n")

for k,v in calls.items():
    nec_out_f.write( "{},{}".format(k,v) + "\n")

nec_out_f.close()

print ("Reached end of script...")
