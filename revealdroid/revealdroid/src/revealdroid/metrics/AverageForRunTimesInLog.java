package revealdroid.metrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AverageForRunTimesInLog {

	public static void main(String[] args) {
		boolean DEBUG = false;
		String logFileName = args[0];
		
		Pattern decimalValPatt = Pattern.compile("[0-9]+\\.[0-9]+");
		
		String PROCESSING="Processing apk file";
		
		List<Double> runTimes = new ArrayList<Double>();
		Set<String> apkNames = new LinkedHashSet<String>();
		Map<String,Double> apkRunTimes = new LinkedHashMap<String,Double>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(logFileName), Charset.defaultCharset())) {
		    String line = null;
		    String lastApkName = null;
		    while ((line = reader.readLine()) != null) {
		    	if (line.contains(PROCESSING)) {
		    		String apkNameWithSlashes = line.substring(line.lastIndexOf(PROCESSING) + PROCESSING.length(), line.length()).trim();
		    		lastApkName = apkNameWithSlashes.substring(apkNameWithSlashes.lastIndexOf(File.separator)+1,apkNameWithSlashes.length());
		    		if (DEBUG)
		    			System.out.println(lastApkName);
		    		apkNames.add(lastApkName);
		    	}
		    	if (line.contains("has run")) {
		    		Matcher m = decimalValPatt.matcher(line);
		    		if (m.find()) {
		    			if (DEBUG)
		    				System.out.println(m.group(0));
		    			Double runTime = Double.parseDouble(m.group(0));
		    			runTimes.add(runTime);
		    			apkRunTimes.put(lastApkName,runTime);
		    		}
		    		else {
		    			throw new RuntimeException("Could not find decimal value runtime for line:\n" + line);
		    		}
		    		
		    	}
		    }
		} catch (IOException e) {
		    e.printStackTrace();
		}
		
		Set<String> failedApks = new LinkedHashSet<String>(apkNames);
		failedApks.removeAll(apkRunTimes.keySet());
		
		double sum=0;
		for (Double runTime : apkRunTimes.values()) {
			sum += runTime;
		}
		double avgRunTime = sum/apkRunTimes.size();
		
		System.out.println("Failed apks:");
		for (String apkName : failedApks) {
			System.out.println(apkName);
		}
		
		System.out.println("No. of failed apks: " + failedApks.size());
		System.out.println("Average run time: " + avgRunTime);
	}

}
