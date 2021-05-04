package revealdroid.classify;

import java.io.FileWriter;
import java.io.IOException;

import weka.core.Instance;
import weka.core.Instances;

public class DroidKungFuSingleFamilyArffModifier {

	public static void main(String[] args) {
		String arffFileName = args[0];
		if (!arffFileName.endsWith(".arff")) {
			throw new RuntimeException("Invalid extension for input file: " + arffFileName);
		}
		
		Instances instances = Util.buildInstancesFromFile(arffFileName);
		
		
		
		int classIndex = instances.numAttributes()-1;
		for (int i=0;i<instances.numInstances();i++) {
			Instance instance = instances.instance(i);
			if (instance.stringValue(classIndex).contains("DroidKungFu")) {
				instance.setValue(classIndex, "DroidKungFu1");
			}
		}
		
		
		String[] tokens = arffFileName.split("\\.");
		String baseName = tokens[0];
		try {
			FileWriter writer = new FileWriter(baseName + "_droidkungfu_combined.arff");
			writer.write(instances.toString());
			writer.write("\n");
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}

}
