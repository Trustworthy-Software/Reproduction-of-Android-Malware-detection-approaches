package revealdroid;

import handleFlowDroid.DataFeatureSpace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.comparator.SizeFileComparator;

import revealdroid.features.util.FeatureExtractionOptions;
import soot.jimple.infoflow.android.TestApps.Test;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
 
public class Test2 implements Callable<String> {
 
	File apkFileToProcess;
	static String apkClass;
	Integer timeout=10;
	static String arffFileName = "data.arff";
	
    public Test2(File apkFile, String apkClass, Integer timeout) {
		apkFileToProcess = apkFile;
		this.apkClass = apkClass;
		this.timeout = timeout;
	}

	@Override
    public String call() throws Exception {
		System.out.println("Processing apk file " + apkFileToProcess);
		
		DataFeatureSpace data = new DataFeatureSpace();
		data.addEmptyRecord(apkFileToProcess.getName(), this.apkClass);
		String[] argsFlowDroid = { apkFileToProcess.getAbsolutePath(), "lib" + File.separator + "android.jar", "--nostatic", "--aplength", "1", "--aliasflowins", "--nocallbacks ", "--layoutmode ", "none", "--nopaths", "--timeout", timeout.toString() };// ,"--systimeout","1","--pathalgo","CONTEXTINSENSITIVE"};//"--cgalgo",
		
		//data = Test.run(data,argsFlowDroid);
		
		ProcessBuilder pb = new ProcessBuilder("java","-cp",System.getProperty("java.class.path"),"soot.jimple.infoflow.android.TestApps.Test",apkFileToProcess.getAbsolutePath(), "lib" + File.separator + "android.jar", "--nostatic", "--aplength", "1", "--aliasflowins", "--nocallbacks ", "--layoutmode ", "none", "--nopaths");
		pb.inheritIO();
		Process p = pb.start();
		p.waitFor();
        
        //return the thread name executing this callable task
        return Thread.currentThread().getName() + " has processed " + apkFileToProcess;
    }
     
    public static void main(String args[]){
    	
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
		
		arffFileName = opts.arffFileName;
		
		FileFilter directoryFilter = new FileFilter() {
			public boolean accept(File file) {
				return file.isDirectory();
			}
		};
		
		FileFilter apkFilter = new FileFilter() {
			public boolean accept(File file) {
				return file.getName().endsWith(".apk");
			}
		};
		Map<File,String> apkFileClassMap = new TreeMap<File,String>();
		File malDirFile = new File(opts.appsDir);
		for (File dirFile : malDirFile.listFiles(directoryFilter) ) {
			System.out.println(dirFile.getName() + ":");
			for (File apkFile : dirFile.listFiles(apkFilter)) {
				System.out.println("\t" + apkFile.getName());
				apkFileClassMap.put(apkFile, dirFile.getName());
			}
		}
		
		Pattern p = Pattern.compile("[^']+\\.apk");
		
		Set<String> analyzedApks = new TreeSet<String>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(opts.apksProcessedFile),Charset.defaultCharset())) {
		    String line = null;
		    while ((line = reader.readLine()) != null) {
		        Matcher m = p.matcher(line);

				if (m.find()) {
					String apkName = m.group(0);
				    System.out.println(apkName);
				    analyzedApks.add(apkName);
				}
		    }
		} catch (IOException x) {
		    System.err.format("IOException: %s%n", x);
		}
		
		Set<File> allApkFiles = apkFileClassMap.keySet();
		Set<File> apkFilesToProcess = new HashSet<File>();
		Set<File> apkFilesProcessed = new HashSet<File>();
		for (File apkFile : allApkFiles) {
			if (!analyzedApks.contains(apkFile.getName())) {
				apkFilesToProcess.add(apkFile);
			}
			else {
				apkFilesProcessed.add(apkFile);
			}
		}
		File[] apkFilesToProcessArr = apkFilesToProcess.toArray(new File[apkFilesToProcess.size()]);
		Arrays.sort(apkFilesToProcessArr,SizeFileComparator.SIZE_COMPARATOR);
		
		File[] apkFilesProcessedArr = apkFilesProcessed.toArray(new File[apkFilesProcessed.size()]);
		Arrays.sort(apkFilesProcessedArr,SizeFileComparator.SIZE_COMPARATOR);
		
		System.out.println("apk files already processed:");
		for (File apkFile : apkFilesProcessedArr) {
			//System.out.println("\t" + apkFile.getName() + " " + apkFile.length());
			System.out.printf("\t%-70s Size:" + apkFile.length() + "\n", apkFile.getName());
		}
		
		System.out.println("apk files to process:");
		for (File apkFile : apkFilesToProcessArr) {
			//System.out.println("\t" + apkFile.getName() + " " + apkFile.length());
			System.out.printf("\t%-70s Size:" + apkFile.length() + "\n", apkFile.getName());
		}
		
		
        //submitAllThenUseFutures(opts, apkFileClassMap, apkFilesToProcessArr);
        //submitAndGetFutureEachIteration(opts, apkFileClassMap, apkFilesToProcessArr);
        runEachProcessAndWaitFor(opts, apkFileClassMap, apkFilesToProcessArr);
    }

	public static void submitAllThenUseFutures(FeatureExtractionOptions opts, Map<File, String> apkFileClassMap, File[] apkFilesToProcessArr) {
		//Get ExecutorService from Executors utility class, thread pool size is 10
        ExecutorService executor = Executors.newSingleThreadExecutor();//Executors.newFixedThreadPool(4);
        //create a list to hold the Future object associated with Callable
        List<Future<String>> list = new ArrayList<Future<String>>();
        for(File apkFile : apkFilesToProcessArr){
        	//Create MyCallable instance
            Callable<String> callable = new Test2(apkFile,apkFileClassMap.get(apkFile),opts.timeout);
            //submit Callable tasks to be executed by thread pool
            Future<String> future = executor.submit(callable);
            //add Future to the list, we can get return value using Future
            list.add(future);
        }
        for(Future<String> fut : list){
            try {
                //print the return value of Future, notice the output delay in console
                // because Future.get() waits for task to get completed
                System.out.println(new Date()+ "::"+fut.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        //shut down the executor service now
        executor.shutdown();
	}
	
	public static void submitAndGetFutureEachIteration(FeatureExtractionOptions opts, Map<File, String> apkFileClassMap, File[] apkFilesToProcessArr) {
		//Get ExecutorService from Executors utility class, thread pool size is 10
        
        //create a list to hold the Future object associated with Callable
        List<Future<String>> list = new ArrayList<Future<String>>();
        for(File apkFile : apkFilesToProcessArr){
        	ExecutorService executor = Executors.newSingleThreadExecutor();//Executors.newFixedThreadPool(4);
        	//Create MyCallable instance
            Callable<String> callable = new Test2(apkFile,apkFileClassMap.get(apkFile),opts.timeout);
            //submit Callable tasks to be executed by thread pool
            Future<String> future = executor.submit(callable);
            try {
				System.out.println(new Date()+ "::"+future.get());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
            //shut down the executor service now
            executor.shutdownNow();
        }
        
	}
	
	public static void runEachProcessAndWaitFor(FeatureExtractionOptions opts, Map<File, String> apkFileClassMap, File[] apkFilesToProcessArr) {
		//Get ExecutorService from Executors utility class, thread pool size is 10
        
        //create a list to hold the Future object associated with Callable
        List<Future<String>> list = new ArrayList<Future<String>>();
        for(File apkFile : apkFilesToProcessArr){            
            System.out.println("Processing apk file " + apkFile);
    		
    		ProcessBuilder pb = new ProcessBuilder("java","-cp",System.getProperty("java.class.path"),"soot.jimple.infoflow.android.TestApps.Test",apkFile.getAbsolutePath(), "lib" + File.separator + "android.jar", "--nostatic", "--aplength", "1", "--aliasflowins", "--nocallbacks ", "--layoutmode ", "none", "--nopaths", "--classlabel", apkFileClassMap.get(apkFile), "--arff", arffFileName);
    		pb.inheritIO();
    		Process p;
			try {
				p = pb.start();
				boolean exitedNormally = p.waitFor(opts.timeout,TimeUnit.MINUTES);
				if (!exitedNormally) {
					String timeoutMsg="Analysis of " + apkFile + " has timed out";
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


	