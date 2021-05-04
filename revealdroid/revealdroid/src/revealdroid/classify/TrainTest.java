package revealdroid.classify;

import java.util.LinkedHashMap;
import java.util.Map;
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

public class TrainTest {

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
		
		Instances trainData = Util.buildInstancesFromFile(opts.trainArff);
		Instances testData = Util.buildInstancesFromFile(opts.testArff);
		
		PlainText output = new PlainText();
		StringBuffer forPredictionsPrinting = new StringBuffer();
		output.setBuffer(forPredictionsPrinting);
		Range attsToOutput = new Range("all"); 
		Boolean outputDistribution = new Boolean(true);
		
		try {
			MultiFilter mFilter = Util.buildMultiFilter(trainData);

			Instances filteredTrainData = Filter.useFilter(trainData, mFilter);
			Instances filteredTestData = Filter.useFilter(testData, mFilter);

			Util.setClassIndex(filteredTrainData);
			Util.setClassIndex(filteredTestData);

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
			//System.out.println("number of DroidKungFu misclassified as variant: " + errorDroidKungFuCount);
			//System.out.println("% of DroidKungFu misclassified as variant: " + percentErrorDKFCount);
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
