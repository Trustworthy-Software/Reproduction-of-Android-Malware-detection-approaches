package revealdroid.classify;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.evaluation.output.prediction.PlainText;
import weka.core.Instances;
import weka.core.Range;
import weka.core.WekaPackageManager;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToNominal;

public class TrainTestDifference {

	public static void main(String[] args) {
		JCommander cmd = null;
		TrainTestOptions opts = new TrainTestOptions();
		
		try {
			cmd = new JCommander(opts);
			cmd.parse(args);
	 	} catch (ParameterException ex) {
	        System.out.println(ex.getMessage());
	        cmd.usage();
	        System.exit(1);
	    }
		
		String originalArffFileName = opts.trainArff;
		String testArffFileName = opts.testArff;
		
		Instances trainInstances = Util.buildInstancesFromFile(originalArffFileName);
		Instances testInstances = Util.buildInstancesFromFile(testArffFileName);
				
		Instances trainMinusTestInstances = Util.removeInstancesFromInstances(trainInstances, testInstances);
		
		System.out.println("Number of original instances in training: " + trainInstances.numInstances());
		System.out.println("Number of remaining instances in training: " + trainMinusTestInstances.numInstances());
		
		WekaPackageManager.loadPackages(false, true, false); // Load all code in packages
		Classifier classifier = Util.selectClassifier(opts.classifier);
		
		PlainText output = new PlainText();
		StringBuffer forPredictionsPrinting = new StringBuffer();
		output.setBuffer(forPredictionsPrinting);
		Range attsToOutput = new Range("all"); 
		Boolean outputDistribution = new Boolean(true);
		
		try {			
			MultiFilter mFilter = Util.buildMultiFilter(trainMinusTestInstances);

			Instances filteredTrainData = Filter.useFilter(trainMinusTestInstances, mFilter);
			Instances filteredTestData = Filter.useFilter(testInstances, mFilter);

			Util.setClassIndex(filteredTrainData);
			Util.setClassIndex(filteredTestData);

			output.setHeader(filteredTestData);

			classifier.buildClassifier(filteredTrainData);
			Evaluation eval = new Evaluation(filteredTrainData);
			// eval.evaluateModel(classifier, filteredTestData, output,
			// attsToOutput, outputDistribution);
			eval.evaluateModel(classifier, filteredTestData, output, attsToOutput, outputDistribution);
			System.out.println(eval.toSummaryString());

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

			System.out.println(eval.toClassDetailsString());

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
