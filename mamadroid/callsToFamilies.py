import csv
import time
import os
import subprocess
from multiprocessing import Pool,Process,Queue
import numpy as np

"""
We have modified how the calls are abstracted in family mode. Authors don't consider the first element before "." in package names listed in the "Families.txt" file. We believe the complete name of the packages need to be considered.

"""


# This script does the abstraction to families of the API calls.


# This function is called when -wf flag is set to Y to operate the abstraction.
def fileextract (fileitem,numApps,WHICHSAMPLES,v,PACKETS):

	Packetsfile=[]

	with open('Calls/'+WHICHSAMPLES[v]+'/'+str(fileitem)) as callseq:
		specificapp=[]
		for line in callseq:
			Packetsline=[]
			
			for j in line.split('\t')[:-1]:
				match = False
				for y in PACKETS:
					#x = y.partition('.')[2] #original code
					x = y  # Our code
					j = j.replace('<','')
					j = j.replace(' ','')
					if j.startswith(x):
						match = x
						break
				if match == False:
					splitted = j.split('.')
					obfcount=0
					for k in range (0,len(splitted)):
						if len(splitted[k])<3:
							obfcount+=1
					if obfcount>=len(splitted)/2:
						match='obfuscated'
					else:
						match='selfdefined'
				Packetsline.append(match)
			Packetsfile.append(Packetsline)
		callseq.close()
	f = open('Families/'+WHICHSAMPLES[v]+'/'+str(fileitem), 'w') 

	for j in range (0, len(Packetsfile)):
		eachline=''
		for k in range (0,len(Packetsfile[j])):
			eachline=eachline+Packetsfile[j][k]+'\t'
		f.write(str(eachline)+'\n')	
	f.close
	
# The main function. Inputs are explained in MaMaStat.py. In case -wf is set to N the abstraction is operated in the else and not multiprocessed.
def main(WHICHSAMPLES,wf,CORES,callsdatabase=None):
	PACKETS=[]
	with open('Families.txt') as packseq:
		for line in packseq:
			PACKETS.append(line.replace('\n',''))
	packseq.close()
	famdb=[]
	if wf=='Y':
		for v in range (0,len(WHICHSAMPLES)):
			numApps=os.listdir('Calls/'+WHICHSAMPLES[v]+'/')

			queue = Queue()
			for i in range (0,len(numApps)):
				queue.put(numApps[i])
				
			appslist=[]
			leng=len(numApps)
			ProcessList=[]
			numfor=np.min([leng,CORES])
			for rr in range (0,numfor):
				fileitem=queue.get()
				ProcessList.append(Process(target=fileextract, args=(fileitem,numApps,WHICHSAMPLES,v,PACKETS)))
				ProcessList[rr].daemon = True
				ProcessList[rr].start() 
				
			while queue.empty()==False:
				
				for rr in range (0,CORES):
					
					if (ProcessList[rr].is_alive()==False):
						ProcessList[rr].terminate()
						if (queue.empty()==False):
							
							fileitem=queue.get()
							ProcessList[rr]=Process(target=fileextract, args=(fileitem,numApps,WHICHSAMPLES,v,PACKETS))
							ProcessList[rr].daemon = True
							ProcessList[rr].start() 
				
			for rr in range (0,len(ProcessList)):
					
				ProcessList[rr].join()
				

	else:
		for db in callsdatabase:
			appdb=[]
			for app in db:
				Packetsfile=[]
				for line in app:
					Packetsline=[]
					for j in line.split('\t')[:-1]:
						match = False
						for y in PACKETS:
							#x = y.partition('.')[2] #original code 
							x = y                    #our code 
							j = j.replace('<','')
							j = j.replace(' ','')
							if j.startswith(x):
								match = x
								break
						if match == False:
							splitted = j.split('.')
							obfcount=0
							for k in range (0,len(splitted)):
								if len(splitted[k])<3:
									obfcount+=1
							if obfcount>=len(splitted)/2:
								match='obfuscated'
							else:
								match='selfdefined'
						Packetsline.append(match)
					Packetsfile.append(Packetsline)
				appdb.append(Packetsfile)	
			famdb.append(appdb)
	return famdb
	
