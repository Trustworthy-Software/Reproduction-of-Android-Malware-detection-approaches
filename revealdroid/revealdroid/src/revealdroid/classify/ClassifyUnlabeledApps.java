package revealdroid.classify;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.core.WekaPackageManager;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToNominal;

public class ClassifyUnlabeledApps {
	
	public static void main(String[] args) {
		JCommander cmd = null;
		ClassifyUnlabeledOptions opts = new ClassifyUnlabeledOptions();
		
		try {
			cmd = new JCommander(opts);
			cmd.parse(args);
	 	} catch (ParameterException ex) {
	        System.out.println(ex.getMessage());
	        cmd.usage();
	        System.exit(1);
	    }
		
		// load unlabeled data
		Instances unlabeled = null;
		Instances trainData = null;
		try {
			unlabeled = new Instances(new BufferedReader(new FileReader(opts.unlabeledArff)));

			// create copy
			Instances labeled = new Instances(unlabeled);
			
			BufferedReader trainReader;
			trainReader = new BufferedReader(new FileReader(opts.trainArff));
			trainData = new Instances(trainReader);

			// filter
			MultiFilter mFilter = Util.buildMultiFilter(trainData);

			Instances filteredTrainData = Filter.useFilter(trainData, mFilter);
			Instances filteredLabeledData = Filter.useFilter(labeled,mFilter);

			Util.setClassIndex(filteredTrainData);
			Util.setClassIndex(filteredLabeledData);
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
			
			String baseName = opts.unlabeledArff.substring(0,opts.unlabeledArff.lastIndexOf(".")); 
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
;
}
