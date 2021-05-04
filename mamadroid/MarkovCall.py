import Markov as mk
import os
from time import time
import numpy as np

#Main script for the Markov modeling part. Inputs are explained in MaMaStat.py. Generates a csv file with the features per each row.
def main(WHICHSAMPLES,wf,WHICHCLASS,dbs=None,appslist=None):
	PACKETS=[]

	with open(WHICHCLASS+'.txt') as packseq:
		for line in packseq:
			PACKETS.append(line.replace('\n',''))
	packseq.close()
	allnodes=PACKETS
	allnodes.append('selfdefined')
	allnodes.append('obfuscated')

	Header=[]
	Header.append('filename')
	for i in range (0,len(allnodes)):
		for j in range (0,len(allnodes)):
			Header.append(allnodes[i]+'To'+allnodes[j])
	print('Header is long ', len(Header))

	Fintime=[]
	dbcounter=0
	for v in range (0,len(WHICHSAMPLES)):
		numApps=os.listdir('graphs/'+WHICHSAMPLES[v]+'/')

		DatabaseRes=[]
		DatabaseRes.append(Header)

		leng=len(numApps)
		checks=[0,999,1999,2999,3999,4999,5999,6999,7999,8999,9999,10999,11999,12999]
		for i in range (0,len(numApps)):
			if i in checks:
				print('starting ', i+1, ' of ', leng)
			if wf=='Y':
				with open(WHICHCLASS+'/'+WHICHSAMPLES[v]+'/'+str(numApps[i])) as callseq:
					specificapp=[]
					for line in callseq:
						specificapp.append(line)
				callseq.close()
			else:
				specificapp=[]
				for line in dbs[dbcounter][i]:
					specificapp.append(line)
					
			Startime=time()
			MarkMat=mk.main(specificapp,allnodes,wf)

			MarkRow=[]
			if wf=='Y':
				MarkRow.append(numApps[i])
			else:
				MarkRow.append(appslist[dbcounter][i])			
			for i in range (0,len(MarkMat)):
				for j in range (0,len(MarkMat)):
					MarkRow.append(MarkMat[i][j])			
			
			DatabaseRes.append(MarkRow)
			Fintime.append(time()-Startime)
		dbcounter+=1
		f = open('Features/'+WHICHCLASS+'/'+WHICHSAMPLES[v]+'.csv', 'w')  
		for line in DatabaseRes:
			f.write(str(line)+'\n')
		f.close

