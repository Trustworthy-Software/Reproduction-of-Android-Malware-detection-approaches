package revealdroid.features.apiusage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import revealdroid.features.intent.ExtractSystemIntentActions;
import revealdroid.features.util.FeatureExtractionOptions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class BatchExtractApiUsageFeatures {
	
	public static void main(String[] args) {
		JCommander cmd = null;
		FeatureExtractionOptions opts = new FeatureExtractionOptions();
		try {
			cmd = new JCommander(opts);
			cmd.parse(args);
	 	} catch (ParameterException ex) {
	        System.out.println(ex.getMessage());
	        cmd.usage();
	        System.exit(1);
	    }
		
		Pattern pattern = Pattern.compile("([^']+\\.apk)");
		
		Set<String> apkFileNamesToProcess = new LinkedHashSet<String>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(opts.inArffFileName), Charset.defaultCharset())) {
		    String line = null;
		    while ((line = reader.readLine()) != null) {
		        Matcher m = pattern.matcher(line);
		        
		        if (m.find()) {
		        	String apkFileName = m.group(1);
		        	apkFileNamesToProcess.add(apkFileName);
		        	System.out.println("Arff file contains apk file " + apkFileName);
		        }
		    }
		} catch (IOException x) {
		    System.err.format("IOException: %s%n", x);
		}
		
		String startPath = opts.appsDir;
		try {
			Iterator<Path> pathIter = Files.walk(Paths.get(startPath),2).iterator();
			while (pathIter.hasNext()) {
				Path path = pathIter.next();
				if (path.toString().endsWith(".apk")) {
					Path apkPath = path;
					if (opts.checkInputFile) {
						if (!apkFileNamesToProcess.contains(apkPath.getFileName().toString())) {
							System.out.println("Arff file does not contain " + apkPath.getFileName() + ". Skipping that file and continuing...");
							continue;
						}
					}
					String apkActionsFileName = determineOutputFileName(path.toString());
					Path apkActionsPath = Paths.get(apkActionsFileName);
					if (Files.exists(apkActionsPath)) {
						System.out.println("Skipping computation for " + path + ",  " + apkActionsFileName + " already exists");
					}
					else {
						System.out.println("Processing apk file " + apkPath);
			    		
			    		ProcessBuilder pb = new ProcessBuilder("java","-cp",System.getProperty("java.class.path"),"revealdroid.features.apiusage.ExtractApiUsageFeatures",apkPath.toString());
			    		Map<String,String> env = pb.environment();
			    		env.put("ANDROID_HOME",opts.androidPlatformsDir);
			    		pb.inheritIO();
			    		Process p;
						try {
							p = pb.start();
							boolean exitedNormally = p.waitFor(opts.timeout,TimeUnit.MINUTES);
							if (!exitedNormally) {
								String timeoutMsg="Analysis of " + apkPath + " has timed out";
								System.out.println(timeoutMsg);
							}
							p.destroy();
						} catch (IOException | InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static String determineOutputFileName(String apkFilePath) {
		String apkFileNameWithoutExt = com.google.common.io.Files.getNameWithoutExtension(apkFilePath);
		String familyName = (new File(apkFilePath)).getParentFile().getName();
		//System.out.println(apkFileNameWithoutExt);
		//System.out.println(familyName);
		
		String outputFileName = familyName + "_" + apkFileNameWithoutExt + "_apiusage.txt";
		return outputFileName;
	}

}
