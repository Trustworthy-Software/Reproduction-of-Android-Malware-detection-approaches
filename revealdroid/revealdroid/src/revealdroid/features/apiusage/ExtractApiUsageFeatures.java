package revealdroid.features.apiusage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.MDC;

import revealdroid.StopWatch;
import revealdroid.Util;

public class ExtractApiUsageFeatures {
	
	public static boolean skipIfFeatureFileExists = false;

	public static void main(String[] args) {
		boolean DEBUG=false;
		
		Logger logger = Util.setupSiftingLogger(ExtractApiUsageFeatures.class);
		
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		
		String apkFilePath = args[0];
		File apkFile = new File(apkFilePath);
		MDC.put("apkName", apkFile.getName());
		String apkFileName = apkFile.getName();
		String apkFileBase = apkFileName.substring(0,apkFileName.lastIndexOf("."));
		String outFileName = "data" + File.separator + "apiusage" + File.separator + apkFile.getParentFile().getName() + "_" + apkFileBase + "_apiusage.txt";
		File outFile = new File(outFileName);

		String fapiFileName = "data" + File.separator + "fapi" + File.separator + apkFile.getParentFile().getName() + "_" + apkFileBase + "_fapi.txt";
		File fapiFile = new File(fapiFileName);
		
		if (outFile.exists() && skipIfFeatureFileExists) {
			logger.debug(outFile + " already exists. Skipping...");
			System.exit(0);
		}
		
		ApiUsageTransformer transformer = new ApiUsageTransformer(apkFilePath);
		transformer.run();
		
		if (DEBUG) {
			logger.debug("apkFile base name: " + apkFileBase);
		}
		
		try {
			if (DEBUG) {
			logger.debug("Access counts to android packages:");
			}
			FileWriter writer = new FileWriter(outFile);
			for (String pkg : transformer.pkgAccessCountMap.keySet()) {
				String outLine = pkg + "," + transformer.pkgAccessCountMap.get(pkg);
				if (DEBUG) {
					logger.debug(outLine);
				}
				writer.write(outLine + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			if (DEBUG) {
				logger.debug("Access counts to full android method names:");
			}
			FileWriter writer = new FileWriter(fapiFile);
			for (String fullMethodName : transformer.methodInvokeCountMap.keySet()) {
				String outLine = fullMethodName + "," + transformer.methodInvokeCountMap.get(fullMethodName);
				if (DEBUG) {
					logger.debug(outLine);
				}
				writer.write(outLine + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		stopWatch.stop();
		logger.debug("Android API Usage Extraction has run for " + stopWatch.getElapsedTime() + " ms");
		
		System.out.println("Android API Usage Extraction has run for " + stopWatch.getElapsedTime() + " ms");
	}

}
