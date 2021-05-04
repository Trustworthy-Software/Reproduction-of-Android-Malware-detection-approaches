package revealdroid.features.intent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import revealdroid.features.util.FeatureExtractionOptions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class BatchExtractSystemIntentActions {

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
		
		String startPath = opts.appsDir;
		try {
			Iterator<Path> pathIter = Files.walk(Paths.get(startPath),2).iterator();
			while (pathIter.hasNext()) {
				Path path = pathIter.next();
				if (path.toString().endsWith(".apk")) {
					Path apkPath = path;
					String apkActionsFileName = ExtractSystemIntentActions.determineOutputFileName(path.toString());
					Path apkActionsPath = Paths.get(apkActionsFileName);
					if (Files.exists(apkActionsPath)) {
						System.out.println("Skipping computation for " + path + ",  " + apkActionsFileName + " already exists");
					}
					else {
						System.out.println("Processing apk file " + apkPath);
			    		
			    		ProcessBuilder pb = new ProcessBuilder("java","-cp",System.getProperty("java.class.path"),"revealdroid.features.intent.ExtractSystemIntentActions",apkPath.toString());
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

}
