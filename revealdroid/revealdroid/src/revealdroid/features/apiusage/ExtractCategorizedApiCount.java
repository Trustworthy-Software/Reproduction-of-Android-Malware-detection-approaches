package revealdroid.features.apiusage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import revealdroid.StopWatch;
import revealdroid.Util;
import revealdroid.enduser.cli.AnalyzeSingleAppForReputationUsingCatApicount;

public class ExtractCategorizedApiCount {

	public static Map<String,Integer> catApiCounts = null;
	public static boolean skipIfFeatureFileExists = false;

	public static void main(String[] args) {
		boolean DEBUG=false;
		
		Logger logger = Util.setupSiftingLogger(ExtractCategorizedApiCount.class);
		
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		
		String apkFilePath = args[0];
		File apkFile = new File(apkFilePath);
		MDC.put("apkName", apkFile.getName());
		String apkFileName = apkFile.getName();
		String apkFileBase = apkFileName.substring(0,apkFileName.lastIndexOf("."));
		String outFileName = "data" + File.separator + "apiusage" + File.separator + apkFile.getParentFile().getName() + "_" + apkFileBase + "_categorized_apicount.txt";
		File outFile = new File(outFileName);
		
		if (outFile.exists() && skipIfFeatureFileExists) {
			logger.debug(outFile + " already exists. Skipping...");
			System.exit(0);
		}
		
		CategorizedApiCountTransformer transformer = new CategorizedApiCountTransformer(apkFilePath);
		transformer.run();
		
		if (DEBUG) {
			logger.debug("apkFile base name: " + apkFileBase);
		}
		
		try {
			if (DEBUG) {
			logger.debug("Access counts for each category:");
			}
			FileWriter writer = new FileWriter(AnalyzeSingleAppForReputationUsingCatApicount.revealdroidDir+ outFile);
			for (String cat : transformer.catAccessCountMap.keySet()) {
				String outLine = cat + "," + transformer.catAccessCountMap.get(cat);
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
		
		catApiCounts = new LinkedHashMap<String,Integer>(transformer.catAccessCountMap);
		
		stopWatch.stop();
		logger.debug("Android API Usage Extraction has run for " + stopWatch.getElapsedTime() + " ms");
		logger.debug("Feature Extraction Time: " + stopWatch.getElapsedTime() + " ms");
		
		System.out.println("Android API Usage Extraction has run for " + stopWatch.getElapsedTime() + " ms");
		System.out.println("Feature Extraction Time: " + stopWatch.getElapsedTime() + " ms");
	}

}
