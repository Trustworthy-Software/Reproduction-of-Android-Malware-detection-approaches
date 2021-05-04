package revealdroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Date;
import java.util.concurrent.Callable;

import soot.jimple.infoflow.android.TestApps.Test;
import handleFlowDroid.DataFeatureSpace;

public class ThreadRunFlowDroid implements Callable{
	private Thread t;
	private String threadName;
	public String sdkAndroid="lib/android.jar";
	public String strPath;
	public Integer timeout=30;
	
	public ThreadRunFlowDroid(String name){	
		threadName = name;
	}
	public ThreadRunFlowDroid(){
	}
	
	public DataFeatureSpace call(){
		System.out.println("Call started for " +  threadName );
		
		DataFeatureSpace data = new DataFeatureSpace();
		//sdkAndroid = "D:/Program Files (x86)/Android/android-sdk/platforms/android-20/android.jar";
				
		// Check if the arff file exists so that if each apk is already
		// processed, prevent from getting processed again
		String arffFilePath = "data.arff";
		File arffFile = new File(arffFilePath);
		String arffFileContent = "";
		if(arffFile.exists() && !arffFile.isDirectory()) {
			 try {
				BufferedReader in
				   = new BufferedReader(new FileReader(arffFile));
				String line = null;				 
				  while((line =in.readLine())!=null){				 
				  arffFileContent += line + "\n";
				  }
				  in.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		String strClassName = "";
		File directory = new File(strPath);
		// get all the files from a directory
		File[] folderList = directory.listFiles();
		for (File subfolder : folderList) {
			if (subfolder.isDirectory()) {
				strClassName = subfolder.getName();
				File[] fileList = subfolder.listFiles();
				for (File apkFile : fileList) {
					if (apkFile.getName().endsWith("apk") && !arffFileContent.toString().contains(apkFile.getName()) && permitProcessFile(apkFile.getName())) {
						System.out.println("Extracting flowdroid features from " + apkFile);
						data.addEmptyRecord(apkFile.getName(), strClassName);
						String[] argsFlowDroid = { apkFile.getAbsolutePath(), sdkAndroid, "--nostatic", "--aplength", "1", "--aliasflowins", "--nocallbacks ", "--layoutmode ", "none", "--nopaths", "--timeout", timeout.toString() };// ,"--systimeout","1","--pathalgo","CONTEXTINSENSITIVE"};//"--cgalgo",
																																																							// "CHA",
						
						try {
							System.out.println("Running FlowDroid...");
							data = Test.run(data,argsFlowDroid);
							System.out.println("Finished running FlowDroid.");
							if (!Test.timedOut && !Test.executionExceptionOccurred) {
								data.produceOutputFile(arffFilePath);
							}
						} catch (Exception e) {
							e.printStackTrace();
							continue;
							//return "Aleyk";
						}
						// test1.startingTime = new Date().getTime();
						if (Test.timedOut) {
							System.out.println("Extraction of " + apkFile + " timed out");
						}
						else if (Test.executionExceptionOccurred) {
							System.out.println("Extraction of " + apkFile + " threw an execution exception");
						}
						else {
							System.out.println("Completed extraction from " + apkFile);
						}
						return null;
					} else {
						System.out.println("Skipping flowdroid feature extraction from " + apkFile);
					}
				}
			}
		}
		
		System.out.println("Reading data from " + arffFilePath);
	    data.readFromFile(arffFilePath);
	    File data1arff = new File("data1.arff");
	    System.out.println("Deleting " + data1arff);
	    data1arff.delete();
	    System.out.println("Writing new " + data1arff);
	    data.produceOutputFile("data1.arff");
	    System.out.println("Returning from Thread: " + Thread.currentThread().getName());
	    return null;//Thread.currentThread().getName();
	}
	
	private static boolean permitProcessFile(String apkFilename){
		File listApksInUseFile;
		FileChannel channel = null;
		FileLock lock = null;
		boolean apkProcessPermit = false;
		try {
			listApksInUseFile = new File("listApksInUse.txt");
			channel = new RandomAccessFile(listApksInUseFile, "rw").getChannel();

			// Use the file channel to create a lock on the file.
			// This method blocks until it can retrieve the lock.
			lock = channel.lock();
			
			// ** Your logic here **
			ByteBuffer listFiles = ByteBuffer.allocate(1000000);
			
			channel.read(listFiles);
			String listFileContent = new String(listFiles.array());
			if (!listFileContent.contains(apkFilename)){			
				apkFilename = apkFilename+System.getProperty("line.separator");
				channel.write(ByteBuffer.wrap(apkFilename.getBytes()));
				apkProcessPermit = true;
			}
		}catch(IOException e){
				
		}finally {
			// Release and close 
			try {
				lock.release();
				channel.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		return apkProcessPermit;
	}

	public void kill(){
		t.stop();
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Unable to sleep thread ...");
		}
		if (t.isAlive()){
			System.out.println("Still alive thread ...");
		}
		System.out.println("Finishing thread ...");
	}
	
	public void start ()
	{
		System.out.println("Starting " +  threadName );
		if (t == null)
		{
			t = new Thread (threadName);
			t.start ();
		}
	}

}
