package revealdroid.virustotal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import weka.core.Instance;
import weka.core.Instances;

public class FamilyDirWithArffCreator {
	
	public static void main(String args[]) {
		String inArffFileName = args[0];
		String malwareDir = args[1];
		
		BufferedReader inArffReader;
		try {
			inArffReader = new BufferedReader(new FileReader(inArffFileName));

			Instances inputInstances = new Instances(inArffReader);

			
			Set<String> alreadyExtractedApksFromVirusShare = new LinkedHashSet<String>();
			for (int i = 0; i < inputInstances.numInstances(); i++) {
				String apkName = inputInstances.instance(i).stringValue(0);
				if (apkName.contains("VirusShare")) {
					alreadyExtractedApksFromVirusShare.add(apkName.trim());
				}
			}
			
			Set<String> apkFileNamesFromDir = new LinkedHashSet<String>();
			// key: apk file name, value: family
			Map<String,String> apkNameFamilyMap = new LinkedHashMap<String,String>();
			for (File file : FileUtils.listFiles(new File(malwareDir), new String[] {"apk"}, true) ) {
				String apkName = file.getName().trim();
				apkFileNamesFromDir.add(apkName);
				String familyName = file.getParentFile().getName();
				apkNameFamilyMap.put(apkName,familyName);
			}
			
			Set<String> alreadyExtractedApksFromVirusShareAndInDir = new LinkedHashSet<String>(alreadyExtractedApksFromVirusShare);
			alreadyExtractedApksFromVirusShareAndInDir.retainAll(apkFileNamesFromDir);
			
			int alreadyExtractedApkCount = 1;
			System.out.println("Already extracted apks: ");
			for (String name : alreadyExtractedApksFromVirusShareAndInDir) {
				System.out.println("\t" + alreadyExtractedApkCount + ": " + name);
				alreadyExtractedApkCount++;
			}
			
			Set<String> missingApks = new LinkedHashSet<String>(apkFileNamesFromDir);
			missingApks.removeAll(alreadyExtractedApksFromVirusShareAndInDir);
			System.out.println("Missing apks:");
			int missingApkCount = 0;
			for (String name : missingApks) {
				System.out.println("\t" + missingApkCount + ": " + name);
				missingApkCount++;
			}
			
			
			List<String> familiesToIgnore = new ArrayList<String>();
			familiesToIgnore.add("Jifake");
			for (int i=inputInstances.numInstances()-1; i>=0;i--) {
				Instance currInstance = inputInstances.instance(i);
				String apkName = currInstance.stringValue(0);
				if (apkName.contains("VirusShare") && !alreadyExtractedApksFromVirusShareAndInDir.contains(apkName.trim())) {
					inputInstances.delete(i);
				}
				if (!apkName.contains("VirusShare")) {
					inputInstances.delete(i);
				}
				String familyName = apkNameFamilyMap.get(apkName);
				if ( familiesToIgnore.contains(familyName) ) {
					inputInstances.delete(i);
				}
			}
			
			inputInstances.setClassIndex(inputInstances.numAttributes()-1);
			for (int i=inputInstances.numInstances()-1; i>=0;i--) {
				Instance currInstance = inputInstances.instance(i);
				String apkName = currInstance.stringValue(0);
				String familyName = apkNameFamilyMap.get(apkName);
				currInstance.setClassValue(familyName);
			}
			
			
			int numZeroInstances = 1;
			for (int i=inputInstances.numInstances()-1; i>=0;i--) {
				Instance currInstance = inputInstances.instance(i);
				String apkName = currInstance.stringValue(0);
				
				double attrValAccumulator=0;
				for (int attrIndex=1 /*skip apk name*/;attrIndex < inputInstances.numAttributes()-1 /* skip class attribute */;attrIndex++) {
					attrValAccumulator += currInstance.value(attrIndex);
				}
				if (attrValAccumulator == 0) {
					System.out.println(numZeroInstances++ + ": " + apkName + " has all zero features: " + currInstance);
				}
			}
			
			String baseName = inArffFileName.substring(0, inArffFileName.lastIndexOf("."));
			BufferedWriter writer = new BufferedWriter(new FileWriter(baseName + "_expanded_malgenome_incomplete.arff"));
			writer.write(inputInstances.toString());
			writer.newLine();
			writer.flush();
			writer.close();

			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
