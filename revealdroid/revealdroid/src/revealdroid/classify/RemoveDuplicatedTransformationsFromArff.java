package revealdroid.classify;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import weka.core.Instance;
import weka.core.Instances;

public class RemoveDuplicatedTransformationsFromArff {

	public static void main(String[] args) {
		String arffFileName = args[0];
		
		if (!arffFileName.endsWith(".arff")) {
			throw new RuntimeException("Input file " + arffFileName + " does not end with .arff");
		}
		
		Instances instances = Util.buildInstancesFromFile(arffFileName);
		
		Pattern transPatt = Pattern.compile("t([0-9]+)_([0-9a-zA-Z]+)\\.apk");
		
		// key: hash, value: set of transformation numbers
		Map<String,Set<Integer>> hashToTrans = new LinkedHashMap<String,Set<Integer>>();
		
		for (int i=0;i<instances.numInstances();i++) {
			Instance instance = instances.instance(i);
			String apkName = instance.stringValue(0);
			
			Matcher m = transPatt.matcher(apkName);
			if (m.find()) {
				Integer transNum = Integer.parseInt(m.group(1));
				String hash = m.group(2);
				
				System.out.println("t" + transNum + " on " + hash);
				
				Set<Integer> transformations = null;
				if (hashToTrans.containsKey(hash)) {
					transformations = hashToTrans.get(hash);
				}
				else {
					transformations = new TreeSet<Integer>();
				}
				transformations.add(transNum);
				hashToTrans.put(hash,transformations);
			}
			else {
				throw new RuntimeException("Invalid apk file name " + apkName);
			}
		}
		
		Set<String> duplicateApkNames = new LinkedHashSet<String>();
		for (Entry<String,Set<Integer>> entry : hashToTrans.entrySet()) {
			String hash = entry.getKey();
			Set<Integer> transformations = entry.getValue();
			List<Integer> transList = new ArrayList<Integer>();
			transList.addAll(transformations);
			if (transformations.size() > 1) {
				System.out.println("Duplicate -> " + hash + " : " + transformations);
				System.out.println("Will remove duplicate:");
			}
			for (int i=1;i<transList.size();i++) {
				int trans = transList.get(i);
				String duplicateApkName = "t" + trans + "_" + hash + ".apk";
				System.out.println("\t" + duplicateApkName);
				duplicateApkNames.add(duplicateApkName);
			}
		}
		
		for (int i=instances.numInstances()-1;i>=0;i--) {
			Instance instance = instances.instance(i);
			String apkName = instance.stringValue(0);
			if (duplicateApkNames.contains(apkName)) {
				System.out.println("Removing " + apkName);
				instances.remove(i);
			}
		}
		
		String[] tokens = arffFileName.split("\\.");
		String fileBaseName = tokens[0];
		String newFileName = fileBaseName + "_nodupetrans.arff";

		try {
			FileWriter writer = new FileWriter(newFileName);
			writer.write(instances.toString());
			writer.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

	}

}
