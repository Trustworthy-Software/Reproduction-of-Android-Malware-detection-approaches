'''
info: This script parses the call egdes extracted from apps into caller and callee
''' 

from collections import defaultdict


def parse_graph(app, _dir):
	caller = []
	callee = []
	edges = defaultdict(list)
	filename = app.split("/")[-1]
	if '.apk' in filename:
		_newFile = filename.replace(".apk", "")
	else:
		_newFile = filename

	with open(app) as lines:
		for line in lines:
		# ==> this signifies the separator between caller and callee. Take right partition because it maybe two
			line = line.rpartition(" ==> ")  
			callee.append(line[2])
			if line[0].count(" in ") > 1:
				calls = line[0].split(" in ")
				if calls[1].startswith("<"):
					_index = calls[1].index("<")
					if calls[1][int(_index) + 1].isalnum():  # checks whether it's a package name
						caller.append(calls[1])
				else:
					_call = line[0].rpartition(" in ")
					if _call[2].startswith("<"):
						_index = _call[2].index("<")
						if _call[2][int(_index) + 1].isalnum():
							caller.append(_call[2])
			else:
			#get the class of caller
				calls = line[0].split(" in ")
				try:
					caller.append(calls[1])
				except:
					print(line[0] +"%%"+ line[1])
	for i, j in zip(caller, callee):
		edges[i].append(j)

	newfile = _dir + "/graphs/" + _newFile
	with open(newfile, 'w') as out:
		for edge in edges:
			out.write(str(edge) + " ==> " + str(edges[edge])+"\n")

	return newfile

