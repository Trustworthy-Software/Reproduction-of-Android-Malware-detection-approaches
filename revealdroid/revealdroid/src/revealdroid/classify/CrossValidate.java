package revealdroid.classify;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.output.prediction.PlainText;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Range;
import weka.core.SelectedTag;
import weka.core.WekaPackageManager;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToNominal;
import weka.core.SerializationHelper;

public class CrossValidate {

	public static void main(String[] args) {
		JCommander cmd = null;
		CrossValidateOptions opts = new CrossValidateOptions();
		
		try {
			cmd = new JCommander(opts);
			cmd.parse(args);
	 	} catch (ParameterException ex) {
	        System.out.println(ex.getMessage());
	        cmd.usage();
	        System.exit(1);
	    }
		
		String arffFileName = opts.arff;
		
		try {
			Instances trainData = Util.buildInstancesFromFile(arffFileName);
			Util.setClassIndex(trainData);
			
			// filter
			MultiFilter mFilter = Util.buildMultiFilter(trainData);

			Instances filteredTrainData = Filter.useFilter(trainData, mFilter);

			Util.setClassIndex(filteredTrainData);
			
			String baseName = revealdroid.Util.extractBaseNameAndExtension(arffFileName)[0];
			String modelFileName = "res/" + baseName + "_" + opts.classifier + ".model";
			File modelFile = new File(modelFileName);
			Classifier classifier = null;
			WekaPackageManager.loadPackages(false, true, false); // Load all code in packages
			if (modelFile.exists()) {
				System.out.println("Using existing classifier from file: " + modelFileName);
				classifier = (Classifier) SerializationHelper.read(modelFileName);
			}
			else {
				System.out.println("Building new classifier");
				classifier = Util.selectClassifier(opts.classifier);//(Classifier)Class.forName("weka.classifiers.lazy.IB1").newInstance();
				classifier.buildClassifier(filteredTrainData);
				System.out.println("Storing new classifier to file: " + modelFileName);
				SerializationHelper.write(modelFileName, classifier);
			}
			PlainText output = new PlainText();
			StringBuffer forPredictionsPrinting = new StringBuffer();
			output.setBuffer(forPredictionsPrinting);
			Range attsToOutput = null; 
			Boolean outputDistribution = new Boolean(true);
			Evaluation eval = new Evaluation(filteredTrainData);
			eval.crossValidateModel(classifier, filteredTrainData, opts.folds, new Random(1), output, attsToOutput, outputDistribution);
			
			System.out.println(eval.toSummaryString());
			
			
			// key: nominal value and family name, value: number of errors for the family
			Map<String,Integer> familyErrorMap = new LinkedHashMap<String,Integer>();
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
					}
					else {
						currFamilyErrorCount = 1;
					}
					familyErrorMap.put(actual,currFamilyErrorCount);
					
					errorCount++;
				}
			}
			
			double percentErrorDKFCount = ((double)errorDroidKungFuCount/(double)errorCount)*100;
			
			System.out.println();
			System.out.println("number of DroidKungFu misclassified as variant: " + errorDroidKungFuCount);
			System.out.println("% of DroidKungFu misclassified as variant: " + percentErrorDKFCount);
			System.out.println("Errors per family:");
			for (Entry<String,Integer> entry : familyErrorMap.entrySet()) {
				String family = entry.getKey();
				Integer count = entry.getValue();
				System.out.println(family + " : " + count);
				double percentage = ((double)count/(double)errorCount)*100;
				System.out.println(family + " : " + percentage);
			}
			
			System.out.println(eval.toClassDetailsString());
			
			//System.out.println(eval.toMatrixString());

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static Map<Double, Integer> printNumInstancesPerLabel(Instances trainData) {
		Map<Double,Integer> classLabelCounts = new LinkedHashMap<Double,Integer>();
		for (int i=0;i<trainData.numInstances();i++) {
			Instance currInstance = trainData.instance(i);
			double classLabel = currInstance.classValue();
			Integer count = null;
			if (classLabelCounts.containsKey(classLabel)) {
				count = classLabelCounts.get(classLabel);
			}
			else {
				count = new Integer(0);
			}
			count++;
			classLabelCounts.put(classLabel,count);
		}
		
		System.out.println("label counts: ");
		for (Entry<Double,Integer> entry : classLabelCounts.entrySet()) {
			Double classLabel = entry.getKey();
			Integer count = entry.getValue();
			String label = trainData.classAttribute().value((int)classLabel.doubleValue());
			System.out.println(label + " : " + count);
		}
		return classLabelCounts;
	}

}
