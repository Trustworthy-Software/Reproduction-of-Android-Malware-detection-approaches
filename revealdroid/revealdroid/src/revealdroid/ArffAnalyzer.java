package revealdroid;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.google.common.base.Joiner;

import weka.core.Attribute;
import weka.core.Instances;

public class ArffAnalyzer {

	public static void main(String[] args) {
		
		String inFileName = args[0];
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(inFileName));

			Instances instances = new Instances(reader);

			int uniqIdSmsFlowIdx = -1;
			for (int i=0;i<instances.numAttributes();i++) {
				Attribute attr = instances.attribute(i);
				if (attr.name().equals("UNIQUE_IDENTIFIER____SMS_MMS") ) {
					uniqIdSmsFlowIdx = i;
				}
			}
			for (int i = 0; i < instances.numInstances(); i++) {
				double uniqIdSmsFlowVal = instances.instance(i).value(uniqIdSmsFlowIdx);
				String name = instances.instance(i).stringValue(0);
				System.out.println(name + ": " + uniqIdSmsFlowVal);
			}
			
			// key: family name, value: number of instances of family
			Map<String,Integer> familyCountMap = new TreeMap<String,Integer>();
			for (int i = 0; i < instances.numInstances(); i++) {
				String familyName = instances.instance(i).stringValue(instances.numAttributes()-1);
				Integer count = null;
				if (familyCountMap.containsKey(familyName)) {
					count = familyCountMap.get(familyName);
					count++;
				}
				else {
					count = 1;
				}
				familyCountMap.put(familyName, count);
			}
			
			System.out.println("Family counts:");
			for (Entry<String,Integer> entry : familyCountMap.entrySet()) {
				System.out.println(entry.getKey() + " & " + entry.getValue() + " \\\\");
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}

}
