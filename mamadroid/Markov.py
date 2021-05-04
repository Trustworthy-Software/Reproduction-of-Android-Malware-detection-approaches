import numpy




#Dummy Coding for Markov Transition matrix
def dummycoding (imported,allnodes,wf):
    
	
	DCVector=[]
	DNSCounter=0
	for i in range (0,len(imported)):
		DCVector.append([])
		if wf=='Y':
			callsline=imported[i].split('\t')
		else:
			callsline=imported[i]
		for v in range (0,len(callsline)):	
			for s in range (0,len(allnodes)):

			        if (callsline[v]==allnodes[s]):
        			        DCVector[i].append(s)
	return DCVector

# This function creates the output matrix that is showing all the transitions probabilities from one state to the other.
def matrixcreation (DCVector,allnodes):
	s=(len(allnodes),len(allnodes))
	MarkovTransition= numpy.zeros(s)
	MarkovFeats= numpy.zeros(s)

	for s in range (0,len(DCVector)):
		for i in range (1,len(DCVector[s])):
		    MarkovTransition [DCVector[s][0],DCVector[s][i]]=MarkovTransition [DCVector[s][0],DCVector[s][i]]+1
	for i in range (0, len(MarkovTransition)):
	        Norma= numpy.sum (MarkovTransition[i])
	        if (Norma==0):
		        MarkovFeats[i]=MarkovTransition[i]
	        
	       	else:
	       		MarkovFeats[i]= MarkovTransition[i]/Norma
        
    
	return MarkovFeats  

       
def main (imported,alln,wf):
    
	(DCV)= dummycoding (imported,alln,wf)
	MarkovFeatures= matrixcreation(DCV,alln)
	return MarkovFeatures
