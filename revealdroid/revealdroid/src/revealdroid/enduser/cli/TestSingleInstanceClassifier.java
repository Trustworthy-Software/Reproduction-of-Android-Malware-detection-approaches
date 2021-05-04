package revealdroid.enduser.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import revealdroid.classify.Util;
import revealdroid.features.apiusage.CatApiCountUtil;
import revealdroid.features.apiusage.ExtractCategorizedApiCount;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.filters.Filter;
import weka.filters.MultiFilter;

public class TestSingleInstanceClassifier {

	public static void main(String[] args) {	
		String apkFileName = args[0];
		ExtractCategorizedApiCount.skipIfFeatureFileExists=false;
		ExtractCategorizedApiCount.main(new String[]{apkFileName});
		File apkFile = new File(apkFileName);
		apkFile.getName();
		Instance instance = CatApiCountUtil.buildSingleCatApiCountInstance("?", apkFile.getName(), ExtractCategorizedApiCount.catApiCounts, "res/detection_labels.txt");
		
		
		Instances data = revealdroid.classify.Util.buildInstancesFromFile("benign_malgenome_virusshare_catapicount.arff");
		Util.setClassIndex(data);
		
		instance.setDataset(data);
		
		
		try {
			// filter
			MultiFilter mFilter = Util.buildMultiFilter(data);

			Instances filteredData = Filter.useFilter(data, mFilter);

			Util.setClassIndex(filteredData);
			
			List<String> instanceNames = new ArrayList<String>();
			for (int i = 0; i < data.numInstances(); i++) {
				instanceNames.add(data.instance(i).stringValue(0));
			}
			
			Classifier classifier = (Classifier) SerializationHelper.read("res/benign_malgenome_virusshare_catapicount_j48.model");
			
			int misclassifiedCount = 0;
			for (int instIndex = 0; instIndex < filteredData.numInstances(); instIndex++) {
				double pred = classifier.classifyInstance(filteredData.instance(instIndex));
				String actual = filteredData.classAttribute().value((int) filteredData.instance(instIndex).classValue());
				String predicted = filteredData.classAttribute().value((int) pred);
				String instApkFileName = instanceNames.get(instIndex);
				if (instApkFileName.equals(apkFile.getName())) {
					System.out.println("ID: " + instApkFileName);
					System.out.print("features: "); // +
					// filteredTestData.instance(instIndex)
					for (int attrIndex = 0; attrIndex < filteredData.instance(instIndex).numAttributes(); attrIndex++) {
						double attrValue = filteredData.instance(instIndex).value(attrIndex);
						String attrName = filteredData.instance(instIndex).attribute(attrIndex).name();
						if (attrValue != 0) {
							System.out.print("," + attrName + ":" + attrValue);
						}
					}
					System.out.println();
					System.out.print("features: "); // +
					// filteredTestData.instance(instIndex)
					for (int attrIndex = 0; attrIndex < instance.numAttributes(); attrIndex++) {
						double attrValue = instance.value(attrIndex);
						String attrName = instance.attribute(attrIndex).name();
						if (attrValue != 0) {
							System.out.print("," + attrName + ":" + attrValue);
						}
					}
					System.out.println();
					System.out.println("predicted: " + predicted);
					
					
					
					if (!mFilter.input(instance)) {
						throw new RuntimeException("Could not filter instance " + instance);
					}
					Instance filteredInstance = mFilter.output();
					
					pred = classifier.classifyInstance(filteredInstance);
					predicted = filteredData.classAttribute().value((int) pred);
					System.out.println("predicted: " + predicted);
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
