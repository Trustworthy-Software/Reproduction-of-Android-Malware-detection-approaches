package revealdroid;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckForDuplicatesInDataArff {

	public static void main(String[] args) {
		String inputArffFilename = args[0];
		
		Pattern p = Pattern.compile("[^']+\\.apk");
		
		List<String> linesReadSoFar = new ArrayList<String>();
		Set<String> duplicateApks = new TreeSet<String>();
		
		List<String> nonDupeLines = new ArrayList<String>();

		Set<String> analyzedApks = new TreeSet<String>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputArffFilename), Charset.defaultCharset())) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				linesReadSoFar.add(line);
				
				Matcher m = p.matcher(line);

				if (m.find()) {
					String apkName = m.group(0);
					//System.out.println(apkName);
					boolean apkNameAdded = analyzedApks.add(apkName);
					if (!apkNameAdded) {
						System.out.println("\t" + apkName + " is a duplicate");
						duplicateApks.add(apkName);
						for (String lineReadSoFar : linesReadSoFar) {
							if (lineReadSoFar.contains(apkName)) {
								System.out.println("\t\t" + lineReadSoFar);
							}
						}
					}
					else {
						nonDupeLines.add(line);
					}
				}
				else {
					nonDupeLines.add(line);
				}
			}
		} catch (IOException x) {
			System.err.format("IOException: %s%n", x);
		}
		
		System.out.println("Analyzed apks:");
		for (String apkName : analyzedApks) {
			System.out.println("\t" + apkName);
		}
		
		String inputArffBaseName = inputArffFilename.substring(0,inputArffFilename.lastIndexOf("."));
		try {
			FileWriter writer = new FileWriter(inputArffBaseName + "_nondupe.arff");
			for (String line : nonDupeLines) {
				writer.write(line + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		

	}

}
