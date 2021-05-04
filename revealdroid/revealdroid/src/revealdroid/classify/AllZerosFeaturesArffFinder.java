package revealdroid.classify;

import weka.core.Instance;
import weka.core.Instances;

public class AllZerosFeaturesArffFinder {

	public static void main(String[] args) {
		String arffFileName = args[0];
		
		Instances instances = Util.buildInstancesFromFile(arffFileName);
		
		for (int i=0;i<instances.numInstances();i++) {
			Instance instance = instances.instance(i);
			boolean foundNonZeroAttrVal = false;
			for (int attrIdx=1; attrIdx<instances.numAttributes()-1;attrIdx++) {
				double attrVal = instance.value(attrIdx);
				if (attrVal != 0) {
					foundNonZeroAttrVal = true;
					break;
				}
			}
			if (!foundNonZeroAttrVal) { // if all the attribute value's for all attributes except the first and last are zero
				String apkName = instance.stringValue(0);
				String family = instance.stringValue(instances.numAttributes()-1);
				//System.out.println(apkName + " has all zero values and belongs to family " + family);
				System.out.println(apkName);
			}
		}

	}

}
