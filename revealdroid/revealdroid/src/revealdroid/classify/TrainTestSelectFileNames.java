package revealdroid.classify;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.toolkits.scalar.Pair;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.evaluation.output.prediction.PlainText;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Range;
import weka.core.WekaPackageManager;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToNominal;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;

public class TrainTestSelectFileNames {

	private static Pattern hashOnlyPatt = Pattern.compile("([0-9a-zA-Z]+)\\.apk");

	public static void main(String[] args) {
		JCommander cmd = null;
		TrainTestOnSelectionOptions opts = new TrainTestOnSelectionOptions();
		try {
			cmd = new JCommander(opts);
			cmd.parse(args);
	 	} catch (ParameterException ex) {
	        System.out.println(ex.getMessage());
	        cmd.usage();
	        System.exit(1);
	    }
		
		Set<String> expectedTrainSelectedApks = readApkFileNamesFromFile(opts.trainSelectFile);
		Set<String> expectedTestSelectedApks = readApkFileNamesFromFile(opts.testSelectFile);

		System.out.println("Selected apks for training:");
		System.out.println(Joiner.on("\n").join(expectedTrainSelectedApks));
		
		System.out.println("\nSelected apks for testing:");
		System.out.println(Joiner.on("\n").join(expectedTestSelectedApks));
		
		Instances trainInstances = Util.buildInstancesFromFile(opts.trainArff);
		Instances testInstances = Util.buildInstancesFromFile(opts.testArff);
			
		Instances actualSelectedTrainInstances = selectInstances(expectedTrainSelectedApks,trainInstances);
		System.out.println("Number of selected training instances: " + actualSelectedTrainInstances.numInstances());
		
		Instances actualSelectedTestInstances = selectInstances(expectedTestSelectedApks,testInstances);
		System.out.println("Number of selected testing instances: " + actualSelectedTestInstances.numInstances());
		
		Set<String> actualTrainSelectApks = extractApkNames(actualSelectedTrainInstances);
		Set<String> actualTestSelectApks = extractApkNames(actualSelectedTestInstances);
		
		Set<String> missingTrainApks = difference(expectedTrainSelectedApks,actualTrainSelectApks);
		Set<String> missingTestApk = difference(expectedTestSelectedApks,actualTestSelectApks);
		
		System.out.println("Missing training apks:");
		System.out.println(Joiner.on("\n").join(missingTrainApks));
		
		System.out.println("Missing test apks:");
		System.out.println(Joiner.on("\n").join(missingTestApk));
		
		PlainText output = new PlainText();
		StringBuffer forPredictionsPrinting = new StringBuffer();
		output.setBuffer(forPredictionsPrinting);
		Range attsToOutput = new Range("all"); 
		Boolean outputDistribution = new Boolean(true);
		
		try {
			MultiFilter mFilter = Util.buildMultiFilter(actualSelectedTrainInstances);

			Instances filteredTrainData = Filter.useFilter(actualSelectedTrainInstances, mFilter);
			Instances filteredTestData = Filter.useFilter(actualSelectedTestInstances, mFilter);

			Util.setClassIndex(filteredTrainData);
			Util.setClassIndex(filteredTestData);
			
			System.out.println("filtered training dataset size: " + filteredTrainData.numInstances());
			System.out.println("filtered testing dataset size: " + filteredTestData.numInstances());

			WekaPackageManager.loadPackages(false, true, false); // Load all
																	// code in
																	// packages
			Classifier classifier = Util.selectClassifier(opts.classifier);
			classifier.buildClassifier(filteredTrainData);
			Evaluation eval = new Evaluation(filteredTrainData);
			// eval.evaluateModel(classifier, filteredTestData, output,
			// attsToOutput, outputDistribution);
			output.setHeader(filteredTestData);
			eval.evaluateModel(classifier, filteredTestData, output, attsToOutput, outputDistribution);
			System.out.println(eval.toSummaryString());
			
			//printDetailedPredictions(forPredictionsPrinting);

			System.out.println(eval.toClassDetailsString());
			
			Pair<String,String> baseExtTrain = splitBaseAndExt(opts.trainArff);
			Pair<String,String> baseExtTest = splitBaseAndExt(opts.testArff);
			String newTrainArffPath = baseExtTrain.getO1() + "_train_selected." + baseExtTrain.getO2();
			String newTestArffPath = baseExtTest.getO1() + "_test_selected." + baseExtTest.getO2();
			PrintWriter trainWriter = new PrintWriter(newTrainArffPath,"UTF-8");
			PrintWriter testWriter = new PrintWriter(newTestArffPath,"UTF-8");
			
			trainWriter.write(filteredTrainData.toString());
			testWriter.write(filteredTestData.toString());
			trainWriter.close();
			testWriter.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static Set<String> difference(Set<String> a, Set<String> b) {
		Set<String> diff = new LinkedHashSet<String>(a);
		diff.removeAll(b);
		return diff;		
	}
	
	public static Set<String> extractApkNames(Instances instances) {
		Set<String> selectedApks = new LinkedHashSet<String>();
		for (int i=0; i<instances.numInstances();i++) {
			Instance instance = instances.instance(i);
			String apkName = instance.stringValue(0);
			selectedApks.add(apkName);
		}
		return selectedApks;
	}

	public static void printDetailedPredictions(StringBuffer forPredictionsPrinting) {
		// key: nominal value and family name, value: number of errors for
		// the family
		Map<String, Integer> familyErrorMap = new LinkedHashMap<String, Integer>();
		String predictions = forPredictionsPrinting.toString();
		String[] lines = predictions.split("\\n");
		int errorDroidKungFuCount = 0;
		int errorCount = 0;
		for (String line : lines) {
			if (line.contains("+")) {
				String[] tokens = line.split("\\s+");
				String actual = tokens[2];
				String predicted = tokens[3];
				System.out.println(actual + " " + predicted);

				if (actual.contains("DroidKu") && predicted.contains("DroidKu")) {
					errorDroidKungFuCount++;
				}

				Integer currFamilyErrorCount = null;
				if (familyErrorMap.containsKey(actual)) {
					currFamilyErrorCount = familyErrorMap.get(actual);
					currFamilyErrorCount++;
				} else {
					currFamilyErrorCount = 1;
				}
				familyErrorMap.put(actual, currFamilyErrorCount);

				errorCount++;
			}
		}

		double percentErrorDKFCount = ((double) errorDroidKungFuCount / (double) errorCount) * 100;

		System.out.println();
		System.out.println("number of DroidKungFu misclassified as variant: " + errorDroidKungFuCount);
		System.out.println("% of DroidKungFu misclassified as variant: " + percentErrorDKFCount);
		System.out.println("Errors per family:");
		for (Entry<String, Integer> entry : familyErrorMap.entrySet()) {
			String family = entry.getKey();
			Integer count = entry.getValue();
			System.out.println(family + " : " + count);
			double percentage = ((double) count / (double) errorCount) * 100;
			System.out.println(family + " : " + percentage);
		}
	}

	private static Instances selectInstances(Set<String> selectedApks, Instances instances) {
		Set<String> apksSeen = new LinkedHashSet<String>();
		Set<String> foundApks = new LinkedHashSet<String>();
		for (int i = instances.numInstances() - 1; i >= 0; i--) {
			Instance instance = instances.instance(i);
			String apkName = instance.stringValue(0).replaceAll("'", "");
			
			boolean apkAdded = apksSeen.add(apkName);
			if (!apkAdded) {
				System.out.println("Removing duplicate apk " + apkName);
				instances.remove(i);
				continue;
			}
			if (!selectedApks.contains(apkName)) {
				instances.remove(i);
			}
			else {
				foundApks.add(apkName);
			}
			
		}
		return instances;
	}

	public static Set<String> readApkFileNamesFromFile(String selectionFileName) {
		Set<String> apkNames = new LinkedHashSet<String>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(selectionFileName), Charset.defaultCharset())) {
		    String line = null;
		    while ((line = reader.readLine()) != null) {
				apkNames.add(line.trim());
		    }
		} catch (IOException e) {
		    e.printStackTrace();
		}
		return apkNames;
	}
	
	public static String extractHashFromName(String name) {
		Matcher m = hashOnlyPatt.matcher(name);
		
		if (m.find()) {
			String hash = m.group(1);
			return hash;
		}
		
		return null;
	}
	
	private static Pair<String, String> splitBaseAndExt(String path) {
		String[] tokens = path.split("\\.(?=[^\\.]+$)");
		String base = tokens[0];
		String ext = tokens[1];
		
		Pair<String,String> pair = new Pair<String,String>();
		pair.setPair(base, ext);
		return pair;
	}

}