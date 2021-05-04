package revealdroid;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class FixClassAttribute {

	public static void main(String[] args) {
		boolean DEBUG=false;
		String arffFileName = args[0];
		
		Pattern p = Pattern.compile(",\\s*([^,}]+)$");
		List<String> lines = new ArrayList<String>();
		Set<String> classLabels = new TreeSet<String>();
		
		System.out.println("Fixing " + arffFileName);
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(arffFileName), Charset.defaultCharset())) {
		    String line = null;
		    while ((line = reader.readLine()) != null) {
		    	Matcher m = p.matcher(line);
		    	if (DEBUG)
		    		System.out.println(line);
		    	if (m.find()) {
		    		String classLabel = m.group(1);
		    		if (DEBUG)
		    			System.out.println("\t" + classLabel);
		    		classLabels.add(classLabel);
		    	}
		    	lines.add(line);
		        
		    }
		} catch (IOException x) {
		    System.err.format("IOException: %s%n", x);
		}
		
		String newClassLine = "@attribute class {";
		for (int i=0; i<lines.size();i++) {
			String line = lines.get(i);
			if (line.contains("@attribute class")) {
				String delim = "";
				for (String classLabel : classLabels) {
					newClassLine += delim + classLabel;
					delim = ",";
				}
				newClassLine += "}";
				lines.set(i, newClassLine);
			}
		}
		
		if (DEBUG) {
			for (String line : lines) {
				System.out.println(line);
			}
		}
		
		String arffFileBase = arffFileName.substring(0,arffFileName.lastIndexOf("."));
		
		String arffOutputFileName = arffFileBase + "_classfixed.arff";
		System.out.println("Writing new file " + arffOutputFileName);
		try {
			FileWriter writer = new FileWriter(arffOutputFileName);
			for (String line : lines) {
				writer.write(line + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
