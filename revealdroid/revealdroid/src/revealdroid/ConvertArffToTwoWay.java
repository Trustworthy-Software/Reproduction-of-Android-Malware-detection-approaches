package revealdroid;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import revealdroid.classify.Util;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class ConvertArffToTwoWay {

	public static void main(String[] args) {
		String inArffFilename = args[0];
		//nonSparseFormatConversion(inArffFilename);
		
		Instances instances = Util.buildInstancesFromFile(inArffFilename);
		Instances newInstances = new Instances(instances);
		
		Attribute classAttr = instances.attribute("class");
		
		List labels = new ArrayList(2);
		labels.add("Benign");
		labels.add("Malware");
		
		Attribute newClassAttr = new Attribute("class",labels);
		newInstances.replaceAttributeAt(newClassAttr, newInstances.numAttributes()-1);
		
		System.out.println(newInstances.attribute("class"));
		
		newInstances.setClassIndex(newInstances.numAttributes()-1);
		for (int i=0;i<instances.numInstances();i++) {
			Instance instance = instances.instance(i);
			String label = instance.stringValue(instances.numAttributes()-1);
			if (label.equals("Benign")) {
				newInstances.instance(i).setClassValue("Benign");
			}
			else {
				newInstances.instance(i).setClassValue("Malware");
			}
		}
		
		String[] tokens = inArffFilename.split("\\.(?=[^\\.]+$)");
		String base = tokens[0];
		String outputFileName = base + "_2way.arff";
		
		try {
			System.out.println("Writing new two-way arff to " + outputFileName);
			FileWriter writer = new FileWriter(outputFileName);
			writer.write(newInstances.toString() + "\n");
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void nonSparseFormatConversion(String inArffFilename) {
		List<String> modifiedLines = new ArrayList<String>();
		Pattern p = Pattern.compile(".*,\\s*(.*)");
		
		System.out.println("Reading and converting from " + inArffFilename);
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(inArffFilename), Charset.defaultCharset())) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				
				
				Matcher m = p.matcher(line);

				String newLine = line;
				if (m.find()) {
					String classLabel = m.group(1);
				    if (!classLabel.equals("Benign")) {
				    	newLine = line.replace(classLabel, "Malware");
				    }
				    if (classLabel.equals("benign")) {
				    	newLine = line.replace("benign", "Benign");
				    }
				}
				if (line.contains("@attribute class")) {
					newLine = "@attribute class {Benign,Malware}";
				}
				newLine = newLine.replaceAll("''", "'");
				modifiedLines.add(newLine);
			}
		} catch (IOException x) {
			System.err.format("IOException: %s%n", x);
		}
		
		String[] tokens = inArffFilename.split("\\.(?=[^\\.]+$)");
		String base = tokens[0];
		String outputFileName = base + "_2way.arff";
		
		try {
			System.out.println("Writing new two-way arff to " + outputFileName);
			FileWriter writer = new FileWriter(outputFileName);
			for (String line : modifiedLines) {
				writer.write(line + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
