package revealdroid.features.apiusage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.MDC;

import revealdroid.Util;
import revealdroid.features.intent.ExtractSystemIntentActions;
import revealdroid.features.util.FeatureExtractionOptions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class BatchCategorizedApiCount {
	
	static Logger logger = Util.setupThreadLogger(BatchCategorizedApiCount.class);
	
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
		        	System.out.println("Input file contains apk file " + apkFileName);
		        }
		    }
		} catch (IOException x) {
		    System.err.format("IOException: %s%n", x);
		}
		
		Map<Long,Set<Path>> apkSizes = new TreeMap<Long,Set<Path>>();
		String startPath = opts.appsDir;
		try {
			System.out.println("Processing apps from input file...");
			processApks(opts, apkFileNamesToProcess, startPath);  
			Iterator<Path> pathIter = Files.walk(Paths.get(startPath),2).iterator();
			while (pathIter.hasNext()) {
				Path path = pathIter.next();
				if (path.toString().endsWith(".apk")) {
					Path apkPath = path;
					ZipFile zipFile = new ZipFile(apkPath.toFile());
					List<ZipEntry> entries = (List<ZipEntry>) Collections.list(zipFile.entries());
					for (ZipEntry entry : entries) {
						if (entry.getName().equals("classes.dex")) {
							Long size = entry.getSize();
							//System.out.println("Found classes.dex for " + apkPath + " with size: " + size);
							Set<Path> paths = null;
							if (apkSizes.containsKey(size)) {
								paths = apkSizes.get(size);
							}
							else {
								paths = new LinkedHashSet<Path>();
							}
							paths.add(apkPath);
							apkSizes.put(size,paths);
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Set<String> apkPathsBySize = new LinkedHashSet<String>();
		System.out.println("Sorted paths:");
		for (Entry<Long,Set<Path>> entry : apkSizes.entrySet()) {
			Long size = entry.getKey();
			Set<Path> paths = entry.getValue();
			System.out.println(size + " : " + paths);
			for (Path path : paths) {
				apkPathsBySize.add(path.toString());
			}
		}
		
		try {
			processApksUsingExecutor(opts,apkPathsBySize, startPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}

	public static void processApks(FeatureExtractionOptions opts, Set<String> apkFileNamesToProcess, String startPath) throws IOException {
		Iterator<Path> pathIter = Files.walk(Paths.get(startPath),2).iterator();
		int availableProcessors = Runtime.getRuntime().availableProcessors();
		int maxCoresPerApp = 4;
		int poolSize = availableProcessors/maxCoresPerApp-1;
		System.out.println("Available processors: " + availableProcessors);
		System.out.println("max cores per app:" + maxCoresPerApp);
		System.out.println("pool size: " + poolSize);
		ExecutorService executor = Executors.newFixedThreadPool(poolSize);
		while (pathIter.hasNext()) {
			Path path = pathIter.next();
			if (path.toString().endsWith(".apk")) {
				Path apkPath = path;
				if (opts.checkInputFile) {
					if (!apkFileNamesToProcess.contains(apkPath.getFileName().toString())) {
						System.out.println("Input file does not contain " + apkPath.getFileName() + ". Skipping that file and continuing...");
						continue;
					}
				}
				String apkActionsFileName = determineOutputFileName(path.toString());
				Path apkActionsPath = Paths.get(apkActionsFileName);
				if (Files.exists(apkActionsPath)) {
					System.out.println("Skipping computation for " + path + ",  " + apkActionsFileName + " already exists");
				}
				else {
					
					executor.execute(new Runnable() {
						@Override
						public void run() {
							logger.debug("Processing apk file " + apkPath);
				    		
				    		ProcessBuilder pb = new ProcessBuilder("java","-cp",System.getProperty("java.class.path"),"revealdroid.features.apiusage.ExtractCategorizedApiCount",apkPath.toString());
				    		Map<String,String> env = pb.environment();
				    		env.put("ANDROID_HOME",opts.androidPlatformsDir);
				    		pb.inheritIO();
				    		Process p;
							try {
								p = pb.start();
								boolean exitedNormally = p.waitFor(opts.timeout,TimeUnit.MINUTES);
								if (!exitedNormally) {
									String timeoutMsg="Analysis of " + apkPath + " has timed out";
									logger.debug(timeoutMsg);
								}
								p.destroy();
							} catch (IOException | InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					});
				}
			}
		}
		executor.shutdown();
	}
	
	public static void processApksUsingExecutor(FeatureExtractionOptions opts, Set<String> apkFileNamesToProcess, String startPath) throws IOException {
		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		Iterator<Path> pathIter = Files.walk(Paths.get(startPath),2).iterator();
		while (pathIter.hasNext()) {
			Path path = pathIter.next();
			if (path.toString().endsWith(".apk")) {
				Path apkPath = path;
				if (opts.checkInputFile) {
					if (!apkFileNamesToProcess.contains(apkPath.getFileName().toString())) {
						System.out.println("Input file does not contain " + apkPath.getFileName() + ". Skipping that file and continuing...");
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
					
										
					executor.execute(new Runnable() {
					    @Override
					    public void run() {
					    	ExtractCategorizedApiCount.main(new String[] {apkPath.toString()});
					    }
					});
				}
			}
		}
		executor.shutdownNow();
	}

	public static String determineOutputFileName(String apkFilePath) {
		String apkFileNameWithoutExt = com.google.common.io.Files.getNameWithoutExtension(apkFilePath);
		String familyName = (new File(apkFilePath)).getParentFile().getName();
		//System.out.println(apkFileNameWithoutExt);
		//System.out.println(familyName);
		
		String outputFileName = "data" + File.separator + "catapicount" + File.separator + familyName + "_" + apkFileNameWithoutExt + "_categorized_apicount.txt";
		return outputFileName;
	}

}
