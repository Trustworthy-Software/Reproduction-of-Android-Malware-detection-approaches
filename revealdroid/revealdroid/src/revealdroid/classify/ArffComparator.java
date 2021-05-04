package revealdroid.classify;

import java.io.BufferedReader;
import java.io.FileReader;

import weka.core.Instance;
import weka.core.Instances;

public class ArffComparator {

	public static void main(String[] args) {
		String arffFileName1 = args[0];
		String arffFileName2 = args[1];
		
		Instances instances1 = null;
		Instances instances2 = null;
		
		try {
			instances1 = new Instances(new BufferedReader(new FileReader(arffFileName1)));
			instances2 = new Instances(new BufferedReader(new FileReader(arffFileName2)));

			int matchCount=0;
			for (int i=0;i<instances1.numInstances();i++) {
				for (int j=0;j<instances2.numInstances();j++) {
					Instance instance1 = instances1.instance(i);
					Instance instance2 = instances2.instance(j);
					String instance1Name = instance1.stringValue(0);
					String instance2Name = instance2.stringValue(0).replaceAll("t[0-9]+_", "");
					if (instance1Name.equals(instance2Name)) {
						String familyName = instance1.stringValue(instances1.numAttributes()-1);
						System.out.println(matchCount + ": " + "Matching instance " + instance1Name + " from family: " + familyName);
						
						for (int attIdx=1;attIdx<instances1.numAttributes();attIdx++) {
							double attr1 = instance1.value(attIdx);
							double attr2 = instance2.value(attIdx);
							if (attr1 != attr2) {
								System.out.println("\tValues differ for " + instances1.attribute(attIdx).name());
								System.out.println("\t\tfirst: " + attr1);
								System.out.println("\t\tsecond: " + attr2);
								
							}
						}
						
						matchCount++;
					}
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		

	}

}
