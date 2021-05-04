package revealdroid.enduser.cli;

import java.io.File;

import revealdroid.classify.Util;
import revealdroid.features.apiusage.CatApiCountUtil;
import revealdroid.features.apiusage.ExtractCategorizedApiCount;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.WekaPackageManager;
import weka.filters.Filter;
import weka.filters.MultiFilter;

public class AnalyzeSingleAppForReputationUsingCatApicount {
	public static final String revealdroidDir=System.getenv("RD_HOME")+ File.separator;
	
	public static void main(String[] args) {
		String apkFileName = args[0];
		ExtractCategorizedApiCount.skipIfFeatureFileExists=false;
		ExtractCategorizedApiCount.main(new String[]{apkFileName});
		File apkFile = new File(apkFileName);
		apkFile.getName();
		Instance reputationInstance = CatApiCountUtil.buildSingleCatApiCountInstance("?", apkFile.getName(), ExtractCategorizedApiCount.catApiCounts, revealdroidDir+"res/detection_labels.txt");
		Instance familyInstance = CatApiCountUtil.buildSingleCatApiCountInstance("?", apkFile.getName(), ExtractCategorizedApiCount.catApiCounts, revealdroidDir+"res/family_labels.txt");
		
		Instances reputationData = revealdroid.classify.Util.buildInstancesFromFile(revealdroidDir + File.separator + "arff" + File.separator + "virusshare_over22000_playdrone_over29000_drebin_sapi.arff");
		Util.setClassIndex(reputationData);
		
		Instances familyData = revealdroid.classify.Util.buildInstancesFromFile(revealdroidDir + File.separator +  "arff" + File.separator + "combined_malgenome_sapi.arff");
		Util.setClassIndex(familyData);
		
		try {
			// filter
			MultiFilter repFilter = Util.buildMultiFilter(reputationData);
			Instances filteredReputationData = Filter.useFilter(reputationData, repFilter);
			Util.setClassIndex(filteredReputationData);
			reputationInstance.setDataset(reputationData);
			
			MultiFilter famFilter = Util.buildMultiFilter(familyData);
			Instances filteredFamData = Filter.useFilter(familyData, famFilter);
			Util.setClassIndex(filteredFamData);
			familyInstance.setDataset(familyData);
			
			System.out.print("features: "); // +
			// filteredTestData.instance(instIndex)
			for (int attrIndex = 1; attrIndex < reputationInstance.numAttributes()-1; attrIndex++) {
				double attrValue = reputationInstance.value(attrIndex);
				String attrName = reputationInstance.attribute(attrIndex).name();
				if (attrValue != 0) {
					System.out.print(attrName + "," + attrValue + ";");
				}
			}
			System.out.println();
			
			WekaPackageManager.loadPackages(false, true, false); // Load all code in packages
			Classifier reputationClassifier = (Classifier) SerializationHelper.read(revealdroidDir+"res/benign_malgenome_virusshare_catapicount_j48.model");
			Classifier familyClassifier = (Classifier) SerializationHelper.read(revealdroidDir+"res/combined_malgenome_catapicount_ib1.model");

			if (!repFilter.input(reputationInstance)) {
				throw new RuntimeException("Could not filter instance " + reputationInstance);
			}
			Instance filteredRepInstance = repFilter.output();

			
			double repPred = reputationClassifier.classifyInstance(filteredRepInstance);
			double repConfidence = reputationClassifier.distributionForInstance(filteredRepInstance)[(int)repPred];
			//System.out.println("pred: " + repPred);
			System.out.println("Reputation Confidence: " + repConfidence);
			String repLabel = reputationInstance.classAttribute().value((int) repPred);
			System.out.println("Reputation: " + repLabel);
			
			if (repLabel.equals("Malware")) {
				if (!famFilter.input(familyInstance)) {
					throw new RuntimeException("Could not filter instance " + familyInstance);
				}
				Instance filteredFamInstance = famFilter.output();
				
				double famPred = familyClassifier.classifyInstance(filteredFamInstance);
				double famConfidence = familyClassifier.distributionForInstance(filteredFamInstance)[(int)famPred];
				//System.out.println("pred: " + famPred);
				System.out.println("Family Confidence: " + famConfidence);
				String famLabel = familyInstance.classAttribute().value((int) famPred);
				System.out.println("Family: " + famLabel);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
