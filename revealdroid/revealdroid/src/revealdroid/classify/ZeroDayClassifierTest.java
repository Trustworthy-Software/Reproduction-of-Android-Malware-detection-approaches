package revealdroid.classify;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.core.WekaPackageManager;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToNominal;

public class ZeroDayClassifierTest {

	public static void main(String[] args) {
		JCommander cmd = null;
		ClassifierTestFeaturesOptions opts = new ClassifierTestFeaturesOptions();
		
		try {
			cmd = new JCommander(opts);
			cmd.parse(args);
	 	} catch (ParameterException ex) {
	        System.out.println(ex.getMessage());
	        cmd.usage();
	        System.exit(1);
	    }
		
		BufferedReader trainReader;
		BufferedReader testReader;
		try {
			trainReader = new BufferedReader(new FileReader(opts.trainArff));
			testReader = new BufferedReader(new FileReader(opts.testArff));

			Instances trainData = new Instances(trainReader);
			Instances testData = new Instances(testReader);

			List<String> instanceNames = new ArrayList<String>();
			for (int i = 0; i < testData.numInstances(); i++) {
				instanceNames.add(testData.instance(i).stringValue(0));
			}

			// filter
			MultiFilter mFilter = Util.buildMultiFilter(trainData);

			Instances filteredTrainData = Filter.useFilter(trainData, mFilter);
			Instances filteredTestData = Filter.useFilter(testData, mFilter);

			Util.setClassIndex(filteredTrainData);
			Util.setClassIndex(filteredTestData);

			Classifier classifier = useClassifierFromFile("/home/joshua/Dropbox/ser/projects/android/security/malware/weka/8853_apks_libsvm_linear.model");
			/*classifier.buildClassifier(filteredTrainData);

			Evaluation eval = new Evaluation(filteredTrainData);
			eval.evaluateModel(classifier, filteredTestData);
			System.out.println(eval.toSummaryString("\nResults\n======\n", false));*/

			Set<String> misclassifiedApks = new LinkedHashSet<String>();
			Set<String> correctlyClassifiedApks = new LinkedHashSet<String>();
			for (int instIndex = 0; instIndex < filteredTestData.numInstances(); instIndex++) {
				double pred = classifier.classifyInstance(filteredTestData.instance(instIndex));
				String actual = filteredTestData.classAttribute().value((int) filteredTestData.instance(instIndex).classValue());
				String predicted = filteredTestData.classAttribute().value((int) pred);
				String apkFileName = instanceNames.get(instIndex);
				System.out.print("ID: " + apkFileName);
				System.out.print(", actual: " + actual);
				System.out.print(", predicted: " + predicted);
				System.out.print(", features: "); // +
				// filteredTestData.instance(instIndex)
				for (int attrIndex = 0; attrIndex < filteredTestData.instance(instIndex).numAttributes(); attrIndex++) {
					double attrValue = filteredTestData.instance(instIndex).value(attrIndex);
					String attrName = filteredTestData.instance(instIndex).attribute(attrIndex).name();
					if (attrValue != 0) {
						System.out.print("," + attrName + ":" + attrValue);
					}
				}
				if (!actual.equals(predicted)) {
					misclassifiedApks.add(apkFileName);
				}
				else {
					correctlyClassifiedApks.add(apkFileName);
				}
				System.out.println();
			}
			System.out.println();

			System.out.println("Misclassified apks: ");
			Files.walk(Paths.get(opts.testDir)).filter(Files::isRegularFile).peek(f -> misclassifiedApks.contains(f.getFileName().toString())).filter(f -> misclassifiedApks.contains(f.getFileName().toString())).forEach(ZeroDayClassifierTest::printFileNameAndParent);
			System.out.println();
			
			System.out.println("Correctly Classified apks: ");
			Files.walk(Paths.get(opts.testDir)).filter(Files::isRegularFile).peek(f -> correctlyClassifiedApks.contains(f.getFileName().toString())).filter(f -> correctlyClassifiedApks.contains(f.getFileName().toString())).forEach(ZeroDayClassifierTest::printFileNameAndParent);
			
			Instances unlabeled = new Instances(testData);
			for (int i=unlabeled.numInstances()-1;i>=0;i--) {
				Instance currInstance = unlabeled.instance(i);
				String apkName = currInstance.stringValue(0);
				if (!correctlyClassifiedApks.contains(apkName)) {
					unlabeled.delete(i);
				}
			}
			
			classifyCorrectlyIdentifiedMalware(unlabeled, "data_intent_actions_apiusage_actions_nobenign.arff", opts.testArff);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public static Classifier useClassifierFromFile(String modelFileName) {
		ObjectInputStream ois;
		Classifier svm = null;
		try {
			ois = new ObjectInputStream(
			        new FileInputStream(modelFileName));
			svm = (Classifier) ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return svm;
	}
	
	private static void classifyCorrectlyIdentifiedMalware(Instances unlabeled, String familyTrainArff, String unlabeledTrainArff) {
		// load unlabeled data
		Instances trainData = null;
		
		try {
			// create copy
			Instances labeled = new Instances(unlabeled);

			BufferedReader trainReader;
			trainReader = new BufferedReader(new FileReader(familyTrainArff));
			trainData = new Instances(trainReader);

			MultiFilter mFilter = Util.buildMultiFilter(trainData);

			Instances filteredTrainData = Filter.useFilter(trainData, mFilter);
			Instances filteredLabeledData = Filter.useFilter(labeled, mFilter);

			Util.setClassIndex(filteredTrainData);
			Util.setClassIndex(filteredLabeledData);
			
			
			labeled.deleteAttributeAt(labeled.numAttributes()-1);
			labeled.insertAttributeAt(filteredTrainData.classAttribute(), labeled.numAttributes());
			Util.setClassIndex(labeled);
			
			WekaPackageManager.loadPackages(false, true, false); // Load all code in packages
			Classifier classifier = (Classifier)Class.forName("weka.classifiers.lazy.IB1").newInstance();
			classifier.buildClassifier(filteredTrainData);

			// label instances
			for (int instIndex = 0; instIndex < filteredLabeledData.numInstances(); instIndex++) {
				Instance currInstance = filteredLabeledData.instance(instIndex);
				double clsLabel = classifier.classifyInstance(currInstance);
				labeled.instance(instIndex).setClassValue(clsLabel);
			}
			// save labeled data

			String baseName = unlabeledTrainArff.substring(0, unlabeledTrainArff.lastIndexOf("."));
			BufferedWriter writer = new BufferedWriter(new FileWriter(baseName + "_labeled.arff"));
			writer.write(labeled.toString());
			writer.newLine();
			writer.flush();
			writer.close();

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

	public static void printFileNameAndParent(Path path) {
		System.out.println(path.getFileName() + ":" + path.getParent().getFileName());
	}

}
