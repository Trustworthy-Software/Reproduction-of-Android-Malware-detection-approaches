package revealdroid.classify;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import weka.core.Instance;
import weka.core.Instances;

public class RemoveObfuscatedSamplesFromArff {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String originalArffFileName = args[0];
		String apksToRemoveArffFileName = args[1];
		String newArffFileName = args[2];
		
		Instances origInstances = Util.buildInstancesFromFile(originalArffFileName);
		Instances toRemoveInstances = Util.buildInstancesFromFile(apksToRemoveArffFileName);
		
		Instances modInstances = Util.removeInstancesFromInstances(origInstances, toRemoveInstances);
		
		System.out.println("No. of remaining instances after removal: " + origInstances.numInstances());
		
		try {
			FileWriter writer = new FileWriter(newArffFileName);
			writer.write(origInstances.toString());
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
