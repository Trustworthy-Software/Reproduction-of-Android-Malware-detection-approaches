package revealdroid.classify;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.WekaPackageManager;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToNominal;

public class Util {

	public static Instances buildInstancesFromFile(String fileName) {
		Instances instances = null;
		try {
			instances = new Instances(new BufferedReader(new FileReader(fileName)));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return instances;
	}
	
	public static Classifier useLibSVM() {
		WekaPackageManager.loadPackages(false, true, false); // Load all code in packages 
	    AbstractClassifier classifier = null;
		try {
			classifier = (AbstractClassifier)Class.forName("weka.classifiers.functions.LibSVM").newInstance();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	    String options = ("-K 0 -H off");
	    String[] optionsArr = options.split("\\s");
	    try {
			classifier.setOptions(optionsArr);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return classifier;
	}
	
	public static J48 useJ48() {
		J48 classifier = new J48();
		return classifier;
	}
	
	public static Classifier useIB1() {
		WekaPackageManager.loadPackages(false, true, false); // Load all code in packages
		Classifier classifier = null;
		try {
			classifier = (Classifier)Class.forName("weka.classifiers.lazy.IB1").newInstance();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return classifier;
	}
	
	/**
	 * 
	 * Creates a new set of Instances containing instances of {@code origInstances} with the instances of {@code toRemoveInstances} removed
	 * 
	 * @param origInstances instances from which the modified instances is initialized
	 * @param toRemoveInstances instances to remove from {@code origInstances}
	 * @return resulting modified Instances
	 */
	public static Instances removeInstancesFromInstances(Instances origInstances, Instances toRemoveInstances) {
		boolean DEBUG=false;
		
		Instances modInstances = new Instances(origInstances);
		Pattern transPatt = Pattern.compile("t[0-9]+_([0-9a-zA-Z]+)\\.apk");
		Pattern hashOnlyPatt = Pattern.compile("([0-9a-zA-Z]+)\\.apk");
			
		Set<String> hashesToRemove = new LinkedHashSet<String>();
		for (int i=0;i<toRemoveInstances.numInstances();i++) {
			Instance instance = toRemoveInstances.instance(i);
			String apkName = instance.stringValue(0);
			Matcher m = transPatt.matcher(apkName);
			
			if (m.find()) {
				String hash = m.group(1);
				if (!hashesToRemove.add(hash)) {
					System.out.println(hash + " is duplicate in " + toRemoveInstances.relationName());
				}
			}
			else {
				throw new RuntimeException("Cannot identify hash for apk: " + apkName);
			}
		}
		
		for (int i=modInstances.numInstances()-1;i>=0;i--) {
			Instance instance = modInstances.instance(i);
			String apkName = instance.stringValue(0);
			Matcher m = hashOnlyPatt.matcher(apkName);
			
			if (m.find()) {
				String hash = m.group(1);
				if (DEBUG) {
					System.out.println(hash);
				}
				if (hashesToRemove.contains(hash)) {
					modInstances.remove(i);
				}
			}
			else {
				throw new RuntimeException("No hash found for " + apkName);
			}
		}
		
		return modInstances;
	}
	
	public static MultiFilter buildMultiFilter(Instances trainData) throws Exception {
		Remove rm = new Remove();
		rm.setInputFormat(trainData);
		rm.setAttributeIndices("1"); // remove 1st attribute

		StringToNominal stn = new StringToNominal();
		stn.setAttributeRange("last");
		stn.setInputFormat(trainData);

		MultiFilter mFilter = new MultiFilter();
		mFilter.setInputFormat(trainData);
		mFilter.setFilters(new Filter[] { rm, stn });
		return mFilter;
	}
	
	public static void setClassIndex(Instances data) {
		for (int i = 0; i < data.numAttributes(); i++) {
			Attribute attribute = data.attribute(i);
			if (attribute.name().equals("class")) {
				data.setClassIndex(i);
				// updatedInstances.setClass(attribute);
				break;
			}
		}
	}
	
	public static Classifier selectClassifier(String classifier) {
		if (classifier.equals("ib1")) {
			return Util.useIB1();
		}
		else if (classifier.equals("j48")) {
			return Util.useJ48();
		}
		else if (classifier.equals("svm")) {
			return Util.useLibSVM();
		}
		else {
			throw new RuntimeException("Invalid classifier specified: " + classifier);
		}
	}

}
